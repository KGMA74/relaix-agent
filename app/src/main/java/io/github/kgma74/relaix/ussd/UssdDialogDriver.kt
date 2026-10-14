package io.github.kgma74.relaix.ussd

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one interactive USSD menu, and decides what happens next in it.
 *
 * `TelephonyManager.sendUssdRequest` answers one request and is done: a code
 * that opens a menu leaves that menu on screen with the network waiting for an
 * answer, and nothing in the platform's API can send one. The only thing
 * Android allows to type into a window belonging to another app — the system's
 * USSD dialog — is an accessibility service, so [UssdAccessibilityService]
 * does the typing and this class holds every decision: which reply is next,
 * what counts as a new screen, and when the menu is over.
 *
 * Nothing here logs. A reply may be the SIM's PIN, which the runner has
 * already taken from the vault, and the one place it exists in this process is
 * between [replyToType] and [replySent] — outside a session, every entry point
 * answers with nothing at all.
 *
 * One session at a time. The radio runs a single USSD dialog, and a reply
 * left over from a session that ended must never be typed into the next one.
 */
@Singleton
class UssdDialogDriver @Inject constructor() {

    /** How long a state of the menu may go without news. Shortened by tests. */
    internal data class Waits(
        /** A reply is with the network: how long for its next screen. */
        val answerMillis: Long = 30_000,
        /** A screen is up: how long for the service to place the next reply. */
        val typeMillis: Long = 15_000,
        /** Every reply typed: how long for the network's last screen. */
        val settleMillis: Long = 4_000,
    )

    private enum class Event { SCREEN, REPLIED, DIAL_FAILED }

    private class Session(replies: List<String>) {
        val replies = replies.toList()
        val events = Channel<Event>(Channel.UNLIMITED)
        val screens = mutableListOf<String>()
        /** Packages whose dialog was seen with an input field — diagnostics. */
        val dialogs = mutableListOf<String>()
        var lastScreen = ""
        var typed = 0
        /** True while a reply — or the code itself — is with the network. */
        var awaitingScreen = true
        var failure: Pair<String, String>? = null
    }

    private data class Snapshot(
        val events: Channel<Event>,
        val screens: List<String>,
        val dialogs: List<String>,
        val typed: Int,
        val replyCount: Int,
        val awaitingScreen: Boolean,
        val failure: Pair<String, String>?,
    )

    private val lock = Any()
    private var session: Session? = null

    private val _enabled = MutableStateFlow(false)

    /** Whether the service that does the typing is bound to this process. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    internal var waits: Waits = Waits()

    // ---- the accessibility service ----

    fun attach() {
        _enabled.value = true
    }

    /** The service went away: what it was about to type belongs to no job. */
    fun detach() {
        _enabled.value = false
        fail(
            "USSD_ACCESSIBILITY_DISABLED" to
                "the accessibility service stopped while the menu was running",
        )
    }

    /** Whether a menu is running: outside one the service reads nothing. */
    fun active(): Boolean = synchronized(lock) { session != null }

    /**
     * Whether a screen with no input field counts: only while something is
     * with the network. That is how a menu ends — the last screen is a notice
     * with an OK button — and how a network refuses one early.
     */
    fun acceptsFinalScreen(): Boolean = synchronized(lock) { session?.awaitingScreen == true }

    /** The service found the dialog's input field, in [pkg]. */
    fun dialogSeen(pkg: String) = synchronized(lock) {
        session?.dialogs?.let { if (pkg !in it) it += pkg }
        Unit
    }

    /**
     * The reply to type into the dialog right now, or null: no session, a
     * reply is already with the network, or every reply has been typed.
     */
    fun replyToType(): String? = synchronized(lock) {
        val s = session ?: return null
        if (s.awaitingScreen || s.typed >= s.replies.size) null else s.replies[s.typed]
    }

    /** The reply the service placed and submitted: wait for what it opens. */
    fun replySent() {
        synchronized(lock) {
            val s = session ?: return
            s.typed++
            s.awaitingScreen = true
            s.events.trySend(Event.REPLIED)
        }
    }

    /**
     * What the network put on screen. False when it was not news — the dialog
     * reports the same screen from every direction (a content change, a
     * window appearing, the field taking focus) and only a screen nobody has answered
     * yet moves the menu on.
     */
    fun screen(text: String): Boolean = synchronized(lock) {
        val s = session ?: return false
        val normalized = WHITESPACE.replace(text.trim(), " ")
        if (normalized.isEmpty() || normalized == s.lastScreen) return false
        s.lastScreen = normalized
        s.screens += normalized
        s.awaitingScreen = false
        s.events.trySend(Event.SCREEN)
        true
    }

    /** The dial itself failed; there is no menu to answer. */
    fun dialFailed(errorCode: String, errorMessage: String) = fail(errorCode to errorMessage)

    // ---- the runner ----

    /** Starts a menu. False when one is already running. */
    fun begin(replies: List<String>): Boolean = synchronized(lock) {
        if (session != null) false else {
            session = Session(replies)
            true
        }
    }

    /** Forgets the session: whatever happens next is nobody's reply. */
    fun end() {
        synchronized(lock) { session = null }
    }

    /**
     * Waits until the menu is over, and answers what the network showed and
     * why it stopped — the whole run's outcome, once every reply is typed.
     */
    suspend fun await(): UssdOutcome {
        while (true) {
            val state = snapshot() ?: return UssdOutcome(
                success = false,
                errorCode = "USSD_MENU_ABORTED",
                errorMessage = "the menu session ended before it was over",
            )
            state.failure?.let { (code, message) ->
                return UssdOutcome(
                    success = false,
                    responses = state.screens,
                    errorCode = code,
                    errorMessage = message,
                )
            }

            val wait = when {
                state.awaitingScreen -> waits.answerMillis
                state.typed >= state.replyCount -> waits.settleMillis
                else -> waits.typeMillis
            }
            if (withTimeoutOrNull(wait) { state.events.receive() } == null) {
                return when {
                    // Still waiting on the first screen: the code never opened
                    // a menu this agent could see, so there is nothing to type.
                    state.awaitingScreen && state.screens.isEmpty() -> UssdOutcome(
                        success = false,
                        errorCode = "USSD_MENU_TIMEOUT",
                        errorMessage = "the code did not open a menu the agent could read",
                    )
                    state.awaitingScreen -> UssdOutcome(
                        success = false,
                        responses = state.screens,
                        errorCode = "USSD_MENU_TIMEOUT",
                        errorMessage = if (state.typed >= state.replyCount) {
                            "the network did not answer the last reply"
                        } else {
                            "the network did not answer reply ${state.typed + 1}"
                        },
                    )
                    // Everything typed, and the last screen has been in for a
                    // while: the menu is over, the dialog simply stays up.
                    state.typed >= state.replyCount -> UssdOutcome(
                        success = true,
                        responses = state.screens,
                    )
                    else -> UssdOutcome(
                        success = false,
                        responses = state.screens,
                        errorCode = "USSD_MENU_UNANSWERED",
                        errorMessage = unanswered(state),
                    )
                }
            }
        }
    }

    private fun fail(error: Pair<String, String>) {
        synchronized(lock) {
            val s = session ?: return
            s.failure = error
            s.events.trySend(Event.DIAL_FAILED)
        }
    }

    private fun snapshot(): Snapshot? = synchronized(lock) {
        session?.let {
            Snapshot(
                events = it.events,
                screens = it.screens.toList(),
                dialogs = it.dialogs.toList(),
                typed = it.typed,
                replyCount = it.replies.size,
                awaitingScreen = it.awaitingScreen,
                failure = it.failure,
            )
        }
    }

    /** Why a screen that is up could not be answered. */
    private fun unanswered(state: Snapshot): String =
        if (state.dialogs.isEmpty()) {
            "no USSD dialog with an input field appeared — this phone's dialog may not " +
                "offer one (logcat tag RelaixUssd)"
        } else {
            "the USSD dialog could not be typed into (dialog seen in " +
                state.dialogs.joinToString(", ") + ")"
        }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}

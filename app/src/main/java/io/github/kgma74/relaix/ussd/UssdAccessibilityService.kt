package io.github.kgma74.relaix.ussd

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Types a menu's replies into the system's USSD dialog.
 *
 * When a `UssdJob` carries replies, the network opens a menu and waits for
 * each answer on a screen of its own, and the dialog the platform puts up is
 * the only place that answer can be given. Android lets one kind of component
 * reach into a window owned by another app — the phone app that owns that
 * dialog — an accessibility service, so this is that component.
 *
 * What it does, and just as importantly what it does not:
 *
 *  - it looks at windows only while [UssdDialogDriver] is running a menu;
 *    outside one, every event returns before a single node is read;
 *  - it types only into a window that is a dialog from a package the platform
 *    knows to host USSD dialogs (`com.android.phone` and friends), or, for an
 *    OEM we have never seen, a system alert window — never into an ordinary
 *    window, the status bar or the shade;
 *  - it records what the network showed minus the buttons and minus whatever
 *    it has just typed itself, so a reply never travels back as a screen;
 *  - it never logs a reply: only the package it was typed into.
 *
 * The dialog differs per OEM — some have no input field at all, and a menu
 * cannot be answered there. That is reported by [UssdDialogDriver] as
 * `USSD_MENU_UNANSWERED` with the package seen, rather than passed over.
 */
@AndroidEntryPoint
class UssdAccessibilityService : AccessibilityService() {

    @Inject lateinit var driver: UssdDialogDriver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Window events arrive in storms; one is enough to look again. */
    private val signal = Channel<Unit>(Channel.CONFLATED)

    private var started = false

    /** Replies attempted since the current one, so a broken dialog stops. */
    private var attempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        driver.attach()
        if (started) return
        started = true
        scope.launch {
            for (request in driver.closeRequests) {
                request.complete(
                    try {
                        close()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "could not close the USSD dialog", e)
                        false
                    },
                )
            }
        }
        scope.launch {
            while (true) {
                signal.receive()
                // The dialog is mid-change until it stops changing: typing into
                // a field that is being rebuilt is how a reply goes nowhere.
                delay(SETTLE_MILLIS)
                try {
                    answer()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A window that vanished mid-read, an action the OEM
                    // refused: neither stops the menu, the driver's timeouts
                    // decide whether it went anywhere.
                    Log.w(TAG, "could not read the USSD dialog", e)
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        driver.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        driver.detach()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Outside a menu nothing is read: what a window holds at any other
        // moment is not this service's business, PIN or no PIN.
        // `enabled` is no gate: it is true whenever this service runs at all.
        if (!driver.active()) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            -> signal.trySend(Unit)
        }
    }

    /** Reads the menu, records its screen and puts the next reply in. */
    private suspend fun answer() {
        if (!driver.active()) return
        val window = menuWindow() ?: run {
            // No field: the network's last word (or an early refusal) is a
            // notice with an OK button, and it is the job's outcome.
            if (driver.acceptsFinalScreen()) {
                noticeWindow()?.root?.let { driver.screen(textOf(it, null)) }
            }
            return
        }
        val root = window.root ?: return
        val pkg = root.packageName?.toString() ?: return
        // The field, not the window: it is what makes this the dialog rather
        // than any other window, and the one thing never to read text into.
        val field = findEditable(root, requireVisible = true)
            ?: findEditable(root, requireVisible = false)
            ?: return

        driver.dialogSeen(pkg)
        driver.screen(textOf(root, field))
        typeInto(pkg, window.id)
    }

    private suspend fun typeInto(pkg: String, windowId: Int) {
        val reply = driver.replyToType()
        if (reply == null) {
            attempts = 0
            return
        }
        if (attempts >= MAX_ATTEMPTS) return
        attempts++

        val placed = try {
            place(reply, windowId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "the dialog of $pkg failed while a reply was being placed", e)
            false
        }
        if (placed) {
            // A reply with the network is not to be typed again; the screen it
            // opens is what starts the next one, from zero attempts.
            attempts = 0
        } else if (attempts < MAX_ATTEMPTS) {
            // The dialog may simply not have settled yet: look again shortly.
            signal.trySend(Unit)
        } else {
            // Nothing more from here: the driver's own timeout is what reports
            // a menu that could not be answered, with the package it saw.
            Log.w(TAG, "giving up on a reply into the USSD dialog of $pkg after $attempts attempts")
        }
    }

    /**
     * Closes the dialog a finished session left up: a menu's own Cancel — the
     * network then ends the session — or a notice's OK. Back only on the
     * dialog itself, focused; nothing is pressed in any other window.
     */
    private suspend fun close(): Boolean {
        delay(SETTLE_MILLIS)
        val window = menuWindow() ?: noticeWindow() ?: return false
        val root = window.root ?: return false
        val button = if (findEditable(root, requireVisible = false) == null) findSendButton(root) else findCancelButton(root)
        if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK, null) == true) return true
        return window.isFocused && performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** The dialog's negative action: `button2` on the platform dialog, else the label. */
    private fun findCancelButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) {
            val id = node.viewIdResourceName.orEmpty()
            val label = node.text?.toString()?.trim()?.lowercase()
            if (id.endsWith(":id/button2") || id.endsWith(":id/negative") || label in CANCEL_LABELS) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findCancelButton(child)?.let { return it }
        }
        return null
    }

    /** Sets the reply in the field, then submits it. */
    private suspend fun place(reply: String, windowId: Int): Boolean {
        val field = editableField(windowId) ?: return false
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            reply,
        )
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        // The dialog re-renders around the new text; the node we hold is stale
        // the moment it does, so everything past this point is re-read.
        delay(FIELD_SETTLE_MILLIS)

        val root = windowRoot(windowId) ?: return false
        val fresh = findEditable(root, requireVisible = true)
            ?: findEditable(root, requireVisible = false)
            ?: return false
        if (!submit(root, fresh)) return false
        driver.replySent()
        return true
    }

    /**
     * Sends what is in the field: the dialog's own button where it can be
     * recognised, otherwise the field's IME action. Neither is a guess — a
     * button whose label is not a send action is never clicked, since clicking
     * Cancel on a transfer menu is exactly the mistake this must not make.
     */
    private fun submit(root: AccessibilityNodeInfo, field: AccessibilityNodeInfo): Boolean {
        findSendButton(root)?.let { if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK, null)) return true }
        // ACTION_IME_ENTER is API 30; below that there is only the button, and
        // a dialog whose button could not be recognised is reported, not faked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id, null)
        }
        return false
    }

    /** The window the menu is on: one we may read, with a field to type into. */
    private fun menuWindow(): AccessibilityWindowInfo? = windows.firstOrNull { window ->
        val root = ussdRoot(window) ?: return@firstOrNull false
        (findEditable(root, requireVisible = true) ?: findEditable(root, requireVisible = false)) != null &&
            // A dialer's own screen has a field too — its keypad, its search —
            // and no Send button: only the phone app's dialog is taken on trust.
            (root.packageName == PHONE || findSendButton(root) != null)
    }

    /**
     * The window of a screen with no field — a menu's last one: the same
     * dialog, but with only a button to close it. The button is required, so
     * that nothing else the phone app draws passes for the network's answer.
     */
    private fun noticeWindow(): AccessibilityWindowInfo? = windows.firstOrNull { window ->
        val root = ussdRoot(window) ?: return@firstOrNull false
        findEditable(root, requireVisible = false) == null && findSendButton(root) != null
    }

    /**
     * The root of [window] when it may be the USSD dialog: a known phone
     * package, or — for an OEM never seen — a system alert window, which is
     * how the platform draws it; never an app's ordinary window, never the
     * status bar or the shade (whose inline replies are fields too), and never
     * the "running" spinner shown before the network has said anything.
     */
    private fun ussdRoot(window: AccessibilityWindowInfo): AccessibilityNodeInfo? {
        val root = window.root ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg == packageName || pkg == SYSTEM_UI) return null
        if (pkg !in KNOWN_DIALOG_PACKAGES && window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return null
        if (hasProgress(root)) return null
        return root
    }

    private fun hasProgress(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == "android.widget.ProgressBar") return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasProgress(child)) return true
        }
        return false
    }

    private fun windowRoot(windowId: Int): AccessibilityNodeInfo? =
        windows.firstOrNull { it.id == windowId }?.root

    private fun editableField(windowId: Int): AccessibilityNodeInfo? {
        val root = windowRoot(windowId) ?: return null
        return findEditable(root, requireVisible = true)
            ?: findEditable(root, requireVisible = false)
    }

    private fun findEditable(node: AccessibilityNodeInfo, requireVisible: Boolean): AccessibilityNodeInfo? {
        if (node.isEditable && (!requireVisible || node.isVisibleToUser)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditable(child, requireVisible)?.let { return it }
        }
        return null
    }

    /**
     * The screen as the network put it: every text but the field's — which
     * holds what this agent just typed — and but the buttons, which repeat on
     * every screen and mean nothing to whoever reads the job afterwards.
     */
    private fun textOf(root: AccessibilityNodeInfo, field: AccessibilityNodeInfo?): String {
        var text = collect(root, field, skipClickable = true)
        if (text.isBlank()) text = collect(root, field, skipClickable = false)
        return text
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        field: AccessibilityNodeInfo?,
        skipClickable: Boolean,
    ): String {
        val out = StringBuilder()
        walk(node) {
            if (it !== field && !it.isEditable && (!skipClickable || !it.isClickable)) {
                val value = it.text ?: return@walk
                if (value.isBlank()) return@walk
                if (out.isNotEmpty()) out.append(' ')
                out.append(value)
            }
        }
        return WHITESPACE.replace(out.toString(), " ").trim()
    }

    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, visit)
        }
    }

    /** The dialog's positive action: `button1` on the platform dialog, else the label. */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && looksLikeSend(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findSendButton(child)?.let { return it }
        }
        return null
    }

    private fun looksLikeSend(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName.orEmpty()
        if (id.endsWith(":id/button1") || id.endsWith(":id/send") || id.endsWith(":id/positive")) {
            return true
        }
        val label = node.text?.toString()?.trim()?.trimEnd(':')?.lowercase() ?: return false
        return label in SEND_LABELS
    }

    private companion object {
        const val TAG = "RelaixUssd"

        /** Long enough for the dialog to stop changing, short enough to keep up. */
        const val SETTLE_MILLIS = 150L

        /** Between the field taking the text and the button being pressed. */
        const val FIELD_SETTLE_MILLIS = 250L

        /** Attempts at one reply before the driver is left to report it. */
        const val MAX_ATTEMPTS = 3

        /** The status bar and the shade: never the dialog, whatever they draw. */
        const val SYSTEM_UI = "com.android.systemui"

        /** Packages whose windows are the platform's phone UI, USSD dialog included. */
        const val PHONE = "com.android.phone"

        val KNOWN_DIALOG_PACKAGES = setOf(
            PHONE,
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.contacts",
            "com.mediatek.incallui",
            "com.qualcomm.qti.incallui",
        )

        /** Send, in the languages an operator's SIM is likely to speak. */
        val SEND_LABELS = setOf(
            "send", "ok", "okay", "submit", "enter", "confirm", "continue", "next",
            "valider", "envoyer", "confirmer", "continuer", "suivant",
            "envia", "invia", "versturen", "verzenden", "enviar", "envío",
        )

        /** Cancel, likewise: what ends a menu without answering it. */
        val CANCEL_LABELS = setOf(
            "cancel", "close", "dismiss", "annuler", "fermer", "quitter", "cancelar", "annulla", "annuleren",
        )

        val WHITESPACE = Regex("""\s+""")
    }
}

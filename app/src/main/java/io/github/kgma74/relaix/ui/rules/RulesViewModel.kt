package io.github.kgma74.relaix.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kgma74.relaix.connect.ConnectionManager
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.health.SimProvider
import io.github.kgma74.relaix.policy.PolicyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import smsgateway.v1.Device
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

/** A daily slot being edited. Minutes since midnight; end exclusive. */
data class WindowDraft(val days: Set<Int>, val start: Int, val end: Int)

/** Send caps; 0 is no cap. */
data class LimitsDraft(val perHour: Int = 0, val perDay: Int = 0) {
    /** A daily cap below the hourly one can never be reached as written. */
    val inverted: Boolean get() = perHour > 0 && perDay > 0 && perDay < perHour
}

data class AcceptDraft(val countries: List<String> = emptyList(), val pattern: String = "")

data class SimDraft(
    val subscriptionId: Int,
    val disabled: Boolean = false,
    val accept: AcceptDraft = AcceptDraft(),
    val windows: List<WindowDraft> = emptyList(),
    val timeZone: String = "",
    val limits: LimitsDraft = LimitsDraft(),
)

data class PolicyDraft(
    val timeZone: String = "",
    val windows: List<WindowDraft> = emptyList(),
    val accept: AcceptDraft = AcceptDraft(),
    val sims: Map<Int, SimDraft> = emptyMap(),
    val limits: LimitsDraft = LimitsDraft(),
    /** Not edited on this screen, but carried through: rules are replaced whole. */
    val inbound: Device.InboundRules = Device.InboundRules.getDefaultInstance(),
    val automations: List<Device.Automation> = emptyList(),
)

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val version: Long) : SaveState
    data class Failed(val message: String) : SaveState
}

/**
 * Edits this phone's rules. The server holds them: the screen starts from the
 * copy it last sent, and saving means sending an edit and waiting for the
 * stored version to come back — which is also how a rejected edit shows, by
 * the version not moving.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val policyStore: PolicyStore,
    private val connectionManager: ConnectionManager,
    simProvider: SimProvider,
) : ViewModel() {

    val stored: StateFlow<Device.DevicePolicy?> = policyStore.policy
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val connection: StateFlow<ConnectionState> = connectionManager.state

    /** The SIMs to offer rules for, read once: a SIM swap reopens the screen anyway. */
    val sims: List<Device.SimInfo> = runCatching { simProvider.sims() }.getOrDefault(emptyList())
        .sortedBy { it.slotIndex }

    private val _draft = MutableStateFlow(PolicyDraft())
    val draft: StateFlow<PolicyDraft> = _draft.asStateFlow()

    private val _save = MutableStateFlow<SaveState>(SaveState.Idle)
    val save: StateFlow<SaveState> = _save.asStateFlow()

    // The version the draft was started from. A newer one arriving while the
    // draft is untouched replaces it; once edited, the operator's draft wins
    // until they save or leave.
    private var baseVersion = -1L
    private var dirty = false

    init {
        viewModelScope.launch {
            stored.collect { p ->
                if (p != null && p.version != baseVersion && !dirty) {
                    baseVersion = p.version
                    _draft.value = p.toDraft()
                }
            }
        }
    }

    fun edit(transform: (PolicyDraft) -> PolicyDraft) {
        dirty = true
        _save.value = SaveState.Idle
        _draft.update(transform)
    }

    fun editSim(subscriptionId: Int, transform: (SimDraft) -> SimDraft) = edit { d ->
        val current = d.sims[subscriptionId] ?: SimDraft(subscriptionId)
        d.copy(sims = d.sims + (subscriptionId to transform(current)))
    }

    fun submit() {
        val before = stored.value?.version ?: 0L
        val proto = _draft.value.toProto()
        _save.value = SaveState.Saving
        viewModelScope.launch {
            if (!connectionManager.updatePolicy(proto)) {
                _save.value = SaveState.Failed("Pas de connexion à la passerelle : les règles n'ont pas été envoyées.")
                return@launch
            }
            val echoed = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                policyStore.policy.filterNotNull().first { it.version > before }
            }
            if (echoed == null) {
                _save.value = SaveState.Failed(
                    "La passerelle n'a pas confirmé. Si elle a refusé les règles, elle a renvoyé celles en vigueur.",
                )
                dirty = false
                return@launch
            }
            dirty = false
            baseVersion = echoed.version
            _draft.value = echoed.toDraft()
            _save.value = SaveState.Saved(echoed.version)
        }
    }

    private companion object {
        const val SAVE_TIMEOUT_MS = 5_000L
    }
}

/** Mirrors the server's matching, for the "test a number" field only. */
fun AcceptDraft.accepts(number: String): Boolean? {
    val digits = number.removePrefix("+")
    if (countries.isNotEmpty() &&
        countries.none { c -> io.github.kgma74.relaix.policy.CALLING_CODES[c].orEmpty().any { digits.startsWith(it) } }
    ) {
        return false
    }
    if (pattern.isNotBlank()) {
        val re = try {
            Pattern.compile(pattern)
        } catch (_: PatternSyntaxException) {
            return null
        }
        if (!re.matcher(number).find()) return false
    }
    return true
}

/** Null when valid, else the reason. Java's syntax, close to the server's RE2 for the patterns that matter here. */
fun patternError(pattern: String): String? = if (pattern.isBlank()) {
    null
} else {
    try {
        Pattern.compile(pattern)
        null
    } catch (e: PatternSyntaxException) {
        e.description
    }
}

private fun Device.DevicePolicy.toDraft() = PolicyDraft(
    timeZone = availability.timeZone,
    windows = availability.windowsList.map { it.toDraft() },
    accept = accept.toDraft(),
    limits = LimitsDraft(limits.perHour, limits.perDay),
    inbound = inbound,
    automations = automationsList,
    sims = simsList.associate { s ->
        s.subscriptionId to SimDraft(
            subscriptionId = s.subscriptionId,
            disabled = s.disabled,
            accept = s.accept.toDraft(),
            windows = s.availability.windowsList.map { it.toDraft() },
            timeZone = s.availability.timeZone,
            limits = LimitsDraft(s.limits.perHour, s.limits.perDay),
        )
    },
)

private fun Device.TimeWindow.toDraft() = WindowDraft(daysList.toSet(), startMinute, endMinute)

private fun Device.AcceptRules.toDraft() = AcceptDraft(countriesList, recipientPattern)

private fun PolicyDraft.toProto(): Device.DevicePolicy = Device.DevicePolicy.newBuilder()
    .setAvailability(availability(timeZone, windows))
    .setAccept(accept.toProto())
    .setLimits(limits.toProto())
    .setInbound(inbound)
    .addAllAutomations(automations)
    .addAllSims(
        sims.values
            // A SIM entry that restricts nothing is not worth sending.
            .filter {
                it.disabled || it.accept.countries.isNotEmpty() || it.accept.pattern.isNotBlank() ||
                    it.windows.isNotEmpty() || it.limits.perHour > 0 || it.limits.perDay > 0
            }
            .sortedBy { it.subscriptionId }
            .map { s ->
                Device.SimPolicy.newBuilder()
                    .setSubscriptionId(s.subscriptionId)
                    .setDisabled(s.disabled)
                    .setAccept(s.accept.toProto())
                    .setAvailability(availability(s.timeZone, s.windows))
                    .setLimits(s.limits.toProto())
                    .build()
            },
    )
    .build()

private fun availability(zone: String, windows: List<WindowDraft>) = Device.Availability.newBuilder()
    .setTimeZone(zone)
    .addAllWindows(
        windows.map { w ->
            Device.TimeWindow.newBuilder()
                // All seven days is "every day", stored as none.
                .addAllDays(if (w.days.size == 7) emptyList() else w.days.sorted())
                .setStartMinute(w.start)
                .setEndMinute(w.end)
                .build()
        },
    )
    .build()

private fun AcceptDraft.toProto() = Device.AcceptRules.newBuilder()
    .addAllCountries(countries)
    .setRecipientPattern(pattern.trim())
    .build()

private fun LimitsDraft.toProto() = Device.SendLimits.newBuilder()
    .setPerHour(perHour)
    .setPerDay(perDay)
    .build()

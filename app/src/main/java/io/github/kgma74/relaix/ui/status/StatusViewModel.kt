package io.github.kgma74.relaix.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kgma74.relaix.config.EndpointStore
import io.github.kgma74.relaix.config.ServerEndpoint
import io.github.kgma74.relaix.connect.ConnectionManager
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.health.HealthProvider
import io.github.kgma74.relaix.health.SimProvider
import io.github.kgma74.relaix.jobs.JobDao
import io.github.kgma74.relaix.jobs.JobProcessor
import io.github.kgma74.relaix.jobs.JobRecord
import io.github.kgma74.relaix.policy.PolicyStore
import io.github.kgma74.relaix.security.DeviceIdentityStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import smsgateway.v1.Device
import javax.inject.Inject

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val identityStore: DeviceIdentityStore,
    private val healthProvider: HealthProvider,
    private val jobProcessor: JobProcessor,
    private val simProvider: SimProvider,
    private val endpointStore: EndpointStore,
    private val policyStore: PolicyStore,
    private val connectionManager: ConnectionManager,
    jobDao: JobDao,
) : ViewModel() {

    val connection: StateFlow<ConnectionState> = connectionManager.state

    val deviceId: StateFlow<String?> = identityStore.deviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentJobs: StateFlow<List<JobRecord>> = jobDao.recent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val endpoint: StateFlow<ServerEndpoint?> = endpointStore.endpoint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _health = MutableStateFlow<Device.DeviceHealth?>(null)
    val health: StateFlow<Device.DeviceHealth?> = _health.asStateFlow()

    // The SIMs' identity (slot, carrier, number) beside their health, which
    // only carries a subscription id. Read on the same tick: it changes when
    // a SIM is swapped, which a five-second poll catches soon enough.
    private val _sims = MutableStateFlow<List<Device.SimInfo>>(emptyList())
    val sims: StateFlow<List<Device.SimInfo>> = _sims.asStateFlow()

    init {
        // Polled rather than pushed: HealthProvider reads on demand by design,
        // and the screen only needs to look right while someone is watching
        // it. The heartbeat remains the source of truth for the server.
        viewModelScope.launch {
            while (true) {
                // sentLastHour has to be passed in, exactly as the heartbeat
                // does it: snapshot() defaults it to zero, and a screen showing
                // "0 parts" while the server was told "3" would send an
                // operator hunting for a bug that is not there. Same reasoning
                // for the per-SIM breakdown.
                val snapshot = healthProvider.snapshot(
                    jobProcessor.sentLastHour(),
                    jobProcessor.sentLastHourBySubscription(),
                )
                // What the scheduler decides readiness on. When it moves —
                // most often a permission granted from this screen or from
                // system settings — the server hears it now, not in 30 s.
                val readiness = Readiness(snapshot)
                if (lastReadiness != null && readiness != lastReadiness) {
                    connectionManager.heartbeatNow()
                }
                lastReadiness = readiness
                _health.value = snapshot
                _sims.value = simProvider.sims()
                delay(REFRESH_MILLIS)
            }
        }
    }

    /**
     * Leaves the fleet, then drops the enrollment and the server it pointed at.
     *
     * The server is told first, while the token still exists, so the device
     * disappears from the dashboard instead of lingering there offline; if the
     * phone is not connected that step is skipped and the operator removes it
     * by hand. Clearing the identity is what sends RelaixApp back to
     * enrollment and stops the service; the endpoint goes too, so the next
     * enrollment cannot inherit the old one.
     */
    fun forget() {
        viewModelScope.launch {
            connectionManager.leave()
            // Identity last: clearing it swaps the screen, which clears this
            // ViewModel and would cancel anything still queued after it.
            endpointStore.clear()
            policyStore.clear()
            identityStore.clear()
        }
    }

    private var lastReadiness: Readiness? = null

    private data class Readiness(
        val permissionsOk: Boolean,
        val simReady: Boolean,
        val hasSignal: Boolean,
        val sims: Int,
    ) {
        constructor(h: Device.DeviceHealth) :
            this(h.permissionsOk, h.simReady, h.signalStrength > 0, h.simsCount)
    }

    private companion object {
        const val REFRESH_MILLIS = 5_000L
    }
}

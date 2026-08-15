package io.github.kgma74.relaix.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kgma74.relaix.config.EndpointStore
import io.github.kgma74.relaix.config.ServerEndpoint
import io.github.kgma74.relaix.connect.ConnectionManager
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.health.HealthProvider
import io.github.kgma74.relaix.jobs.JobDao
import io.github.kgma74.relaix.jobs.JobProcessor
import io.github.kgma74.relaix.jobs.JobRecord
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
    connectionManager: ConnectionManager,
    endpointStore: EndpointStore,
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
                _health.value = healthProvider.snapshot(
                    jobProcessor.sentLastHour(),
                    jobProcessor.sentLastHourBySubscription(),
                )
                delay(REFRESH_MILLIS)
            }
        }
    }

    fun forget() {
        viewModelScope.launch { identityStore.clear() }
    }

    private companion object {
        const val REFRESH_MILLIS = 5_000L
    }
}

package io.github.kgma74.relaix.ui.enroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kgma74.relaix.connect.ConnectionManager
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.enroll.EnrollmentPayload
import io.github.kgma74.relaix.enroll.Enroller
import io.github.kgma74.relaix.security.DeviceIdentityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnrollmentUiState(
    val payloadText: String = "",
    val label: String = "",
    val isScanning: Boolean = false,
    val isEnrolling: Boolean = false,
    val enrolledDeviceId: String? = null,
    /**
     * False until the stored identity has been read once.
     *
     * Without it, `enrolledDeviceId == null` is ambiguous — "not enrolled" and
     * "not loaded yet" look identical — and acting on the second as if it were
     * the first stops the service a fraction of a second after the system
     * started it.
     */
    val identityLoaded: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = payloadText.isNotBlank() && !isEnrolling
}

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val enroller: Enroller,
    private val identityStore: DeviceIdentityStore,
    connectionManager: ConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(EnrollmentUiState())
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    /** Surfaced so the screen can show why a phone is not taking work. */
    val connection: StateFlow<ConnectionState> = connectionManager.state

    init {
        // An already-enrolled device must not silently re-enroll: the server
        // would mint a second identity and the first would linger in the
        // fleet as a phone that never reconnects.
        viewModelScope.launch {
            identityStore.deviceId.collect { existing ->
                _state.update {
                    it.copy(
                        enrolledDeviceId = existing ?: it.enrolledDeviceId,
                        identityLoaded = true,
                    )
                }
            }
        }
    }

    fun onPayloadChange(value: String) = _state.update { it.copy(payloadText = value, error = null) }

    fun onLabelChange(value: String) = _state.update { it.copy(label = value) }

    fun startScanning() = _state.update { it.copy(isScanning = true, error = null) }

    fun stopScanning() = _state.update { it.copy(isScanning = false) }

    /**
     * A decoded QR goes straight to enrollment rather than filling the field
     * and waiting for a tap: the operator already made the decision by
     * pointing the camera at the code, and the token is short-lived.
     */
    fun onScanned(payload: String) {
        _state.update { it.copy(payloadText = payload, isScanning = false, error = null) }
        enroll()
    }

    fun enroll() {
        val current = _state.value
        if (!current.canSubmit) return

        val payload = EnrollmentPayload.parse(current.payloadText).getOrElse { failure ->
            _state.update { it.copy(error = failure.message ?: "invalid payload") }
            return
        }

        _state.update { it.copy(isEnrolling = true, error = null) }
        viewModelScope.launch {
            val result = enroller.enroll(payload, current.label)
            _state.update { state ->
                result.fold(
                    onSuccess = { deviceId ->
                        state.copy(isEnrolling = false, enrolledDeviceId = deviceId, error = null)
                    },
                    onFailure = { failure ->
                        state.copy(isEnrolling = false, error = failure.message ?: "enrollment failed")
                    },
                )
            }
        }
    }

    /** Lets the operator re-enroll a phone that was removed server-side. */
    fun reset() {
        viewModelScope.launch {
            identityStore.clear()
            _state.value = EnrollmentUiState()
        }
    }
}

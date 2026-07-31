package io.github.kgma74.relaix.ui.enroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isEnrolling: Boolean = false,
    val enrolledDeviceId: String? = null,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = payloadText.isNotBlank() && !isEnrolling
}

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val enroller: Enroller,
    private val identityStore: DeviceIdentityStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EnrollmentUiState())
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    init {
        // An already-enrolled device must not silently re-enroll: the server
        // would mint a second identity and the first would linger in the
        // fleet as a phone that never reconnects.
        viewModelScope.launch {
            identityStore.deviceId.collect { existing ->
                _state.update { it.copy(enrolledDeviceId = existing ?: it.enrolledDeviceId) }
            }
        }
    }

    fun onPayloadChange(value: String) = _state.update { it.copy(payloadText = value, error = null) }

    fun onLabelChange(value: String) = _state.update { it.copy(label = value) }

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

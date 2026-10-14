package io.github.kgma74.relaix.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kgma74.relaix.health.SimProvider
import io.github.kgma74.relaix.pin.PendingTemplate
import io.github.kgma74.relaix.pin.PinApproval
import io.github.kgma74.relaix.pin.PinGuard
import io.github.kgma74.relaix.pin.PinVault
import io.github.kgma74.relaix.ussd.UssdDialogDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import smsgateway.v1.Device
import javax.inject.Inject

@HiltViewModel
class PinViewModel @Inject constructor(
    private val vault: PinVault,
    private val guard: PinGuard,
    private val simProvider: SimProvider,
    private val ussdDialog: UssdDialogDriver,
) : ViewModel() {
    private val _sims = MutableStateFlow<List<Device.SimInfo>>(emptyList())
    val sims: StateFlow<List<Device.SimInfo>> = _sims.asStateFlow()

    /**
     * Whether the service that types a menu's replies — {pin} included — into
     * the system's USSD dialog is on. Bound to this process rather than to
     * Android's setting, so what the screen says is what a job would get.
     */
    val ussdMenus: StateFlow<Boolean> = ussdDialog.enabled

    val configured: StateFlow<Set<Int>> = vault.configured.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val approvals: StateFlow<List<PinApproval>> = guard.approvals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending: StateFlow<List<PendingTemplate>> = guard.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshSims() {
        _sims.value = simProvider.sims()
    }

    fun setPin(subscriptionId: Int, pin: String) = viewModelScope.launch { vault.set(subscriptionId, pin) }

    fun clearPin(subscriptionId: Int) = viewModelScope.launch { vault.clear(subscriptionId) }

    fun approve(approval: PinApproval) = viewModelScope.launch { guard.approve(approval) }

    fun revoke(approval: PinApproval) = viewModelScope.launch { guard.revoke(approval.subscriptionId, approval.template) }
}

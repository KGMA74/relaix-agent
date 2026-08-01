package io.github.kgma74.relaix.connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.kgma74.relaix.security.DeviceIdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Brings the agent back after a reboot.
 *
 * A node that only works until the phone restarts is not a node: nobody is
 * holding these handsets, so nobody would notice they stopped taking work.
 *
 * Only an enrolled device starts the service. Starting it without an identity
 * would put up a permanent notification for a connection that cannot be
 * attempted.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var identityStore: DeviceIdentityStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync keeps the receiver alive across the DataStore read: onReceive
        // must not block, and a plain launch would be killed the moment this
        // method returns.
        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (identityStore.isEnrolled.first()) {
                    AgentService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

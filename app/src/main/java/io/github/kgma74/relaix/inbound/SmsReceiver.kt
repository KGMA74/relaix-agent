package io.github.kgma74.relaix.inbound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives every incoming SMS from the system and passes the ones the rules
 * forward to [InboundForwarder]. This app is not the default SMS app: the
 * message still lands in the user's messaging app as usual; this only reads
 * a copy.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var forwarder: InboundForwarder

    @Inject lateinit var automations: io.github.kgma74.relaix.automation.AutomationEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra(EXTRA_SUBSCRIPTION, 0))

        // A long SMS arrives as several parts in one broadcast: one message
        // per sender, its parts joined in order.
        val messages = parts.filterNotNull().groupBy { it.displayOriginatingAddress.orEmpty() }
        val pending = goAsync()
        scope.launch {
            try {
                for ((sender, pdus) in messages) {
                    if (sender.isBlank()) continue
                    val body = pdus.joinToString("") { it.displayMessageBody.orEmpty() }
                    val at = pdus.minOf { it.timestampMillis }.takeIf { it > 0 } ?: System.currentTimeMillis()
                    // Automations first: one that forwards the SMS sends it with
                    // its fields, and the plain rules need not send it again.
                    val forwarded = automations.onSms(io.github.kgma74.relaix.automation.IncomingSms(sender, body, at, subscriptionId))
                    if (!forwarded) forwarder.received(sender, body, at, subscriptionId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not keep a received SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "RelaixSmsReceiver"
        // SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX (API 30), and the older
        // name the platform still sets.
        const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
        const val EXTRA_SUBSCRIPTION = "subscription"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

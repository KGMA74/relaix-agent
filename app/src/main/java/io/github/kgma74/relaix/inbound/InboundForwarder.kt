package io.github.kgma74.relaix.inbound

import android.util.Log
import com.google.protobuf.Timestamp
import io.github.kgma74.relaix.policy.PolicyStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import smsgateway.v1.Device
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes received SMS in, and hands them to the stream until acknowledged.
 *
 * The rules decide on the phone, before anything is stored: a message they do
 * not cover never touches the disk, let alone the network.
 */
@Singleton
class InboundForwarder @Inject constructor(
    private val dao: InboundDao,
    private val policyStore: PolicyStore,
) {
    // Conflated: one wake-up answers any number of arrivals.
    private val arrivals = Channel<Unit>(Channel.CONFLATED)

    /** Keeps a received SMS for the server, if the rules forward it. */
    suspend fun received(sender: String, body: String, receivedAtMillis: Long, subscriptionId: Int) {
        val rules = policyStore.policy.first()?.inbound ?: return
        if (!forwards(rules, sender)) return
        dao.insert(InboundRecord(UUID.randomUUID().toString(), sender, body, receivedAtMillis, subscriptionId))
        arrivals.trySend(Unit)
    }

    /**
     * Sends what is pending for as long as the session lasts: everything at
     * once, then again on each arrival — and every few minutes, for messages
     * whose acknowledgement was lost. The server records each id once, so a
     * resend costs nothing but bandwidth.
     */
    suspend fun forward(send: suspend (Device.SmsReceived) -> Unit) {
        while (true) {
            for (record in dao.pending()) send(record.toProto())
            withTimeoutOrNull(RESEND_MS) { arrivals.receive() }
        }
    }

    suspend fun acknowledged(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
        Log.d(TAG, "server acknowledged ${ids.size} received SMS")
    }

    private fun InboundRecord.toProto(): Device.SmsReceived = Device.SmsReceived.newBuilder()
        .setId(id)
        .setSender(sender)
        .setBody(body)
        .setReceivedAt(Timestamp.newBuilder().setSeconds(receivedAtMillis / 1_000).setNanos(((receivedAtMillis % 1_000) * 1_000_000).toInt()))
        .setSubscriptionId(subscriptionId)
        .build()

    companion object {
        private const val TAG = "RelaixInbound"
        private const val RESEND_MS = 5 * 60_000L

        /** Same rule as the server's policy.Inbound.Forwards. */
        fun forwards(rules: Device.InboundRules, sender: String): Boolean {
            if (!rules.enabled) return false
            if (rules.sendersCount == 0) return true
            val s = sender.trim()
            return rules.sendersList.any { it.equals(s, ignoreCase = true) }
        }
    }
}

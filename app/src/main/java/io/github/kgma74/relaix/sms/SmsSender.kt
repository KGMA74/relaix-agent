package io.github.kgma74.relaix.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Outcome of handing one message to the radio.
 *
 * [partsSent] is the number of parts the platform accepted, not the number
 * requested: a long or non-GSM-7 body is split into several billed messages
 * and the caller has to reconcile cost against what actually left.
 */
data class SendOutcome(
    val success: Boolean,
    val partsSent: Int,
    val errorCode: String = "",
    val errorMessage: String = "",
)

/**
 * Sends an SMS and waits for the platform's verdict.
 *
 * The agent, not the server, splits the body: part counting depends on
 * encoding decisions only the platform can make (protocol.md §7).
 *
 * Waiting for the sent receipt rather than returning as soon as SmsManager
 * accepts the call is the whole point — `sendMultipartTextMessage` returning
 * means the request was queued, not that anything reached the network, and
 * reporting JOB_STATUS_SENT at that moment would make the server believe
 * messages that later failed had gone out.
 */
@Singleton
class SmsSender @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun send(jobId: String, recipient: String, body: String): SendOutcome =
        suspendCancellableCoroutine { continuation ->
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(body)
            val partCount = parts.size

            var received = 0
            var failureCode: Int? = null

            // The action is per job: two jobs in flight would otherwise
            // deliver each other's receipts.
            val action = "$ACTION_SENT.$jobId"

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    received++
                    if (resultCode != android.app.Activity.RESULT_OK && failureCode == null) {
                        failureCode = resultCode
                    }

                    // Every part must report before the job is decided: a
                    // message that half-sent is a failure the operator needs
                    // to see, not a success.
                    if (received < partCount) return

                    runCatching { ctx.unregisterReceiver(this) }
                    if (continuation.isActive) {
                        continuation.resume(outcome(failureCode, partCount))
                    }
                }
            }

            // NOT_EXPORTED: the receipt is ours alone, and an exported
            // receiver would let any app forge a "sent" result for a job.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }

            val sentIntents = ArrayList<PendingIntent>(partCount)
            val deliveryIntents = ArrayList<PendingIntent>(partCount)
            repeat(partCount) { index ->
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        // Distinct request codes, or the platform reuses one
                        // PendingIntent for every part and only one receipt
                        // ever arrives.
                        (jobId + index).hashCode(),
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                // Delivery reports arrive long after this call returns — often
                // minutes, sometimes never — so they cannot be awaited here.
                // They carry the job id and go to a receiver owned by the
                // service, which outlives this coroutine.
                deliveryIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        ("delivery" + jobId + index).hashCode(),
                        Intent(ACTION_DELIVERED)
                            .setPackage(context.packageName)
                            .putExtra(EXTRA_JOB_ID, jobId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }

            try {
                smsManager.sendMultipartTextMessage(
                    recipient,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents,
                )
            } catch (e: Exception) {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) {
                    continuation.resume(
                        SendOutcome(
                            success = false,
                            partsSent = 0,
                            errorCode = e::class.java.simpleName,
                            errorMessage = e.message.orEmpty(),
                        ),
                    )
                }
            }
        }

    private fun outcome(failureCode: Int?, partCount: Int): SendOutcome =
        if (failureCode == null) {
            SendOutcome(success = true, partsSent = partCount)
        } else {
            SendOutcome(
                success = false,
                partsSent = 0,
                errorCode = describe(failureCode),
                errorMessage = "SmsManager result code $failureCode",
            )
        }

    /**
     * Names the result code. The server stores this string and automation
     * matches on it, so a number alone would be useless outside a debugger.
     */
    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
        SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "SHORT_CODE_NOT_ALLOWED"
        else -> "RESULT_$code"
    }

    companion object {
        private const val ACTION_SENT = "io.github.kgma74.relaix.SMS_SENT"

        /** Single action for every job; the id travels as an extra. */
        const val ACTION_DELIVERED = "io.github.kgma74.relaix.SMS_DELIVERED"
        const val EXTRA_JOB_ID = "job_id"
    }
}

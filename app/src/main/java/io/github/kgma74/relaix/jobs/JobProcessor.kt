package io.github.kgma74.relaix.jobs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kgma74.relaix.sms.SmsSender
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import smsgateway.v1.Device
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the agent decided about a job, for the stream to report.
 */
data class JobDecision(
    val ack: Device.JobAck,
    /** Null when the job was refused outright and never attempted. */
    val shouldSend: Boolean,
)

/**
 * Turns an incoming `SendSmsJob` into an SMS, a ledger entry and the messages
 * the server expects back.
 */
@Singleton
class JobProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val jobDao: JobDao,
    private val smsSender: SmsSender,
) {
    /**
     * Decides whether to take the job, before sending anything.
     *
     * The ack is sent as soon as the job arrives because "the server wrote to
     * the stream" is not evidence the phone got it, and an explicit refusal
     * lets the scheduler reassign on the next tick instead of waiting for a
     * timeout (protocol.md §6).
     */
    suspend fun accept(job: Device.SendSmsJob): JobDecision {
        val now = System.currentTimeMillis()

        refuseReason(job, now)?.let { reason ->
            return JobDecision(ack(job.jobId, accepted = false, reason = reason), shouldSend = false)
        }

        val inserted = jobDao.insertIfNew(
            JobRecord(
                jobId = job.jobId,
                recipient = job.recipient,
                status = JobState.ACCEPTED,
                receivedAtMillis = now,
                expiresAtMillis = if (job.hasExpiresAt()) job.expiresAt.seconds * 1_000 else null,
            ),
        )

        if (inserted == -1L) {
            // Already known. Delivery is at-least-once, so this is expected
            // rather than exceptional: acknowledge it and send nothing, or the
            // recipient gets the message twice.
            val existing = jobDao.find(job.jobId)
            Log.i(TAG, "duplicate job ${job.jobId} (state=${existing?.status}); not resending")
            return JobDecision(
                ack(job.jobId, accepted = true, reason = ""),
                shouldSend = existing != null && !existing.status.isTerminal &&
                    existing.status != JobState.SENDING,
            )
        }

        return JobDecision(ack(job.jobId, accepted = true, reason = ""), shouldSend = true)
    }

    /**
     * Sends and returns the terminal result.
     *
     * Expiry is re-checked here, not only at accept time: a job can sit behind
     * another one, and delivering a stale OTP late is worse than not
     * delivering it (protocol.md §7).
     */
    suspend fun send(job: Device.SendSmsJob): Device.JobResult {
        val expiresAt = if (job.hasExpiresAt()) job.expiresAt.seconds * 1_000 else null
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            jobDao.updateOutcome(
                jobId = job.jobId,
                status = JobState.FAILED,
                partsSent = 0,
                errorCode = "EXPIRED",
                errorMessage = "job expired before it could be sent",
                completedAtMillis = System.currentTimeMillis(),
            )
            return result(job.jobId, Device.JobStatus.JOB_STATUS_FAILED, 0, "EXPIRED", "job expired")
        }

        jobDao.updateStatus(job.jobId, JobState.SENDING)
        val outcome = smsSender.send(job.jobId, job.recipient, job.body)
        val completedAt = System.currentTimeMillis()

        val state = if (outcome.success) JobState.SENT else JobState.FAILED
        jobDao.updateOutcome(
            jobId = job.jobId,
            status = state,
            partsSent = outcome.partsSent,
            errorCode = outcome.errorCode,
            errorMessage = outcome.errorMessage,
            completedAtMillis = completedAt,
        )

        return result(
            jobId = job.jobId,
            status = if (outcome.success) {
                Device.JobStatus.JOB_STATUS_SENT
            } else {
                Device.JobStatus.JOB_STATUS_FAILED
            },
            partsSent = outcome.partsSent,
            errorCode = outcome.errorCode,
            errorMessage = outcome.errorMessage,
            completedAtMillis = completedAt,
        )
    }

    /**
     * Best effort, as the contract says: once the handset has passed the
     * message to the network there is no recall, so a job already sending or
     * finished is left alone and its real outcome is reported.
     */
    suspend fun cancel(jobId: String): Device.JobResult? {
        val existing = jobDao.find(jobId) ?: return null
        if (existing.status.isTerminal || existing.status == JobState.SENDING) return null

        val now = System.currentTimeMillis()
        jobDao.updateOutcome(jobId, JobState.CANCELLED, 0, "", "", now)
        return result(jobId, Device.JobStatus.JOB_STATUS_CANCELLED, 0, "", "", now)
    }

    /**
     * Results produced outside a send call — today only delivery reports.
     *
     * A Channel rather than a SharedFlow: each result must reach the server
     * exactly once, and buffering while the stream is down is the wanted
     * behaviour. Anything still queued when the process dies is lost, which is
     * acceptable precisely for DELIVERED: the contract calls it a bonus that
     * many carriers never send at all, and the SENT result the caller relies
     * on has already gone out.
     */
    private val _lateResults = Channel<Device.JobResult>(Channel.BUFFERED)
    val lateResults: ReceiveChannel<Device.JobResult> get() = _lateResults

    /** Parts still waiting on a delivery report, per job. */
    private val pendingDeliveries = ConcurrentHashMap<String, Int>()

    /**
     * Records one part's delivery report.
     *
     * Reports arrive per part, so the job is only DELIVERED once every part
     * has reported. The counter lives in memory: a process restart loses it
     * and the job simply stays SENT, which is the honest outcome — claiming
     * delivery we did not observe would be worse than reporting none.
     */
    suspend fun onDeliveryReport(jobId: String, delivered: Boolean) {
        val record = jobDao.find(jobId) ?: return
        if (record.status == JobState.DELIVERED) return

        if (!delivered) {
            Log.i(TAG, "delivery report for $jobId says not delivered; leaving as ${record.status}")
            pendingDeliveries.remove(jobId)
            return
        }

        val remaining = pendingDeliveries.compute(jobId) { _, value ->
            (value ?: record.partsSent.coerceAtLeast(1)) - 1
        } ?: 0
        if (remaining > 0) return

        pendingDeliveries.remove(jobId)
        val completedAt = System.currentTimeMillis()
        jobDao.updateOutcome(
            jobId = jobId,
            status = JobState.DELIVERED,
            partsSent = record.partsSent,
            errorCode = "",
            errorMessage = "",
            completedAtMillis = completedAt,
        )
        _lateResults.send(
            result(
                jobId = jobId,
                status = Device.JobStatus.JOB_STATUS_DELIVERED,
                partsSent = record.partsSent,
                errorCode = "",
                errorMessage = "",
                completedAtMillis = completedAt,
            ),
        )
    }

    suspend fun inFlightJobIds(): List<String> = jobDao.inFlightJobIds()

    suspend fun dropStale(jobIds: List<String>) {
        if (jobIds.isNotEmpty()) jobDao.cancelAll(jobIds)
    }

    /** Parts billed in the trailing hour, for DeviceHealth. */
    suspend fun sentLastHour(): Int =
        jobDao.partsSentSince(System.currentTimeMillis() - ONE_HOUR_MILLIS)

    /**
     * Reasons this device knows it cannot send, checked before accepting so
     * the scheduler can reassign immediately. A device that accepts and then
     * fails everything is the failure mode this prevents.
     */
    private fun refuseReason(job: Device.SendSmsJob, now: Long): String? = when {
        context.checkSelfPermission(Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED -> "SEND_SMS permission not granted"

        context.getSystemService(TelephonyManager::class.java)?.simState !=
            TelephonyManager.SIM_STATE_READY -> "SIM not ready"

        job.recipient.isBlank() -> "recipient is empty"

        job.hasExpiresAt() && now > job.expiresAt.seconds * 1_000 -> "job already expired"

        else -> null
    }

    private fun ack(jobId: String, accepted: Boolean, reason: String): Device.JobAck =
        Device.JobAck.newBuilder()
            .setJobId(jobId)
            .setAccepted(accepted)
            .setReason(reason)
            .build()

    private fun result(
        jobId: String,
        status: Device.JobStatus,
        partsSent: Int,
        errorCode: String,
        errorMessage: String,
        completedAtMillis: Long = System.currentTimeMillis(),
    ): Device.JobResult = Device.JobResult.newBuilder()
        .setJobId(jobId)
        .setStatus(status)
        .setPartsSent(partsSent)
        .setErrorCode(errorCode)
        .setErrorMessage(errorMessage)
        .setCompletedAt(
            com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(completedAtMillis / 1_000)
                .setNanos(((completedAtMillis % 1_000) * 1_000_000).toInt())
                .build(),
        )
        .build()

    private companion object {
        const val TAG = "RelaixJobs"
        const val ONE_HOUR_MILLIS = 3_600_000L
    }
}

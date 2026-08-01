package io.github.kgma74.relaix.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kgma74.relaix.jobs.JobProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Catches delivery reports, which arrive long after the send call returned.
 *
 * Registered by the service rather than declared in the manifest: it only
 * matters while the agent is running, and a manifest receiver would wake the
 * process for a report about a job whose result the server already has.
 */
class DeliveryReportReceiver(
    private val jobProcessor: JobProcessor,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsSender.ACTION_DELIVERED) return
        val jobId = intent.getStringExtra(SmsSender.EXTRA_JOB_ID) ?: return

        // resultCode is the platform's verdict on this part. Anything other
        // than RESULT_OK means the network reported a failure to deliver.
        val delivered = resultCode == Activity.RESULT_OK

        scope.launch { jobProcessor.onDeliveryReport(jobId, delivered) }
    }
}

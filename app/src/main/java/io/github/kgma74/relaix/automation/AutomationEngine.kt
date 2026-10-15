package io.github.kgma74.relaix.automation

import android.util.Base64
import android.util.Log
import com.google.protobuf.Timestamp
import io.github.kgma74.relaix.health.SimProvider
import io.github.kgma74.relaix.inbound.InboundForwarder
import io.github.kgma74.relaix.jobs.JobDao
import io.github.kgma74.relaix.jobs.JobRecord
import io.github.kgma74.relaix.jobs.JobState
import io.github.kgma74.relaix.pin.PinDecision
import io.github.kgma74.relaix.pin.PinGuard
import io.github.kgma74.relaix.policy.PolicyStore
import io.github.kgma74.relaix.sms.SmsSender
import io.github.kgma74.relaix.ussd.UssdRunner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import smsgateway.v1.Device
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A received SMS, as the automations see it. */
data class IncomingSms(val sender: String, val body: String, val receivedAtMillis: Long, val subscriptionId: Int)

/**
 * Runs the device's automations on each received SMS, on the phone, offline
 * included (protocol.md §11). Each run is recorded before its first action,
 * so a crash never runs it twice, and reported to the gateway once over.
 */
@Singleton
class AutomationEngine @Inject constructor(
    private val policyStore: PolicyStore,
    private val runs: RunDao,
    private val forwarder: InboundForwarder,
    private val smsSender: SmsSender,
    private val jobDao: JobDao,
    private val simProvider: SimProvider,
    private val outbox: WebhookOutbox,
    private val pinGuard: PinGuard,
    private val ussdRunner: UssdRunner,
) {
    private val finished = Channel<Unit>(Channel.CONFLATED)

    /**
     * Runs every automation the SMS triggers.
     *
     * @return whether one of them forwarded it: the plain inbound rules then
     *   need not forward it a second time.
     */
    suspend fun onSms(sms: IncomingSms): Boolean {
        val automations = policyStore.policy.first()?.automationsList.orEmpty()
        var forwarded = false
        val receivedAt = Instant.ofEpochMilli(sms.receivedAtMillis).toString()
        for (a in automations) {
            val fields = AutomationRules.match(a, sms.sender, sms.body, receivedAt, sms.subscriptionId) ?: continue
            if (run(a, sms, fields)) forwarded = true
        }
        return forwarded
    }

    /** Returns whether the run forwarded the SMS. */
    private suspend fun run(a: Device.Automation, sms: IncomingSms, fields: Map<String, String>): Boolean {
        val key = smsKey(sms)
        if (runs.countFor(a.id, key) > 0) return false
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        if (!withinLimits(a, now)) {
            runs.insert(RunRecord(id, a.id, key, sms.sender, now, skipped = true))
            runs.finish(id, now, false, "limits reached", "")
            finished.trySend(Unit)
            return false
        }
        runs.insert(RunRecord(id, a.id, key, sms.sender, now))

        val steps = mutableListOf<Device.AutomationStep>()
        var forwarded = false
        var error = ""
        for ((i, action) in a.actionsList.withIndex()) {
            val step = try {
                execute(a, action, i, id, sms, fields)
            } catch (e: Exception) {
                Log.w(TAG, "automation ${a.id} step $i failed", e)
                step(actionName(action), false, e.message ?: e::class.java.simpleName)
            }
            steps += step
            if (action.hasForward() && step.ok) forwarded = true
            if (!step.ok) {
                error = "step ${i + 1} (${step.action}) failed: ${step.detail}"
                break
            }
        }
        val encoded = steps.joinToString(",") { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
        runs.finish(id, System.currentTimeMillis(), error.isEmpty(), error, encoded)
        finished.trySend(Unit)
        return forwarded
    }

    private suspend fun execute(
        a: Device.Automation,
        action: Device.AutomationAction,
        index: Int,
        runId: String,
        sms: IncomingSms,
        fields: Map<String, String>,
    ): Device.AutomationStep = when {
        action.hasForward() -> {
            forwarder.enqueue(sms.sender, sms.body, sms.receivedAtMillis, sms.subscriptionId, a.id, fields)
            step("forward", true, "")
        }

        action.hasReplySms() -> {
            val to = AutomationRules.render(action.replySms.to, fields).trim()
            val own = simProvider.sims().map { it.phoneNumber }.filter { it.isNotEmpty() }
            when {
                // Only to a real number: an alphanumeric sender cannot be
                // answered, and a bank's short code should not be.
                !to.matches(E164) -> step("reply_sms", false, "not a number in E.164: $to")
                // Never to itself: that is how auto-replies loop.
                to in own -> step("reply_sms", false, "will not reply to this phone's own number")
                else -> {
                    val jobId = "auto-$runId-$index"
                    val outcome = smsSender.send(jobId, to, AutomationRules.render(action.replySms.text, fields), sms.subscriptionId)
                    // Counted like any SMS, so the phone's hourly figure stays true.
                    jobDao.insertIfNew(
                        JobRecord(
                            jobId = jobId, recipient = to,
                            status = if (outcome.success) JobState.SENT else JobState.FAILED,
                            subscriptionId = sms.subscriptionId, partsSent = outcome.partsSent,
                            errorCode = outcome.errorCode, errorMessage = outcome.errorMessage,
                            receivedAtMillis = System.currentTimeMillis(), completedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    step("reply_sms", outcome.success, if (outcome.success) to else "$to: ${outcome.errorMessage}")
                }
            }
        }

        action.hasWebhook() -> {
            // One id per SMS, automation and action, the same however often it
            // is posted: the receiver's way to never credit a payment twice.
            val delivery = UUID.nameUUIDFromBytes("${smsKey(sms)}|${a.id}|$index".toByteArray()).toString()
            val template = action.webhook.body
            val body = when {
                template.isEmpty() -> {
                    val numbers = JSONObject()
                    for ((k, v) in AutomationRules.numbers(fields)) numbers.put(k, BigDecimal(v))
                    JSONObject()
                        .put("type", "automation")
                        .put("deliveryId", delivery)
                        .put("automationId", a.id)
                        .put("automation", a.name)
                        .put("sender", sms.sender)
                        .put("body", sms.body)
                        .put("receivedAt", fields["receivedAt"])
                        .put("sim", sms.subscriptionId)
                        .put("fields", JSONObject(fields.filterKeys { it !in AutomationRules.BUILTINS }))
                        .put("numbers", numbers)
                        .toString()
                }
                // Escaped and typed, so a quote in the SMS cannot break it.
                AutomationRules.isJsonTemplate(template) -> AutomationRules.renderJson(template, fields)
                else -> AutomationRules.render(template, fields)
            }
            outbox.enqueue(delivery, action.webhook.url, body, action.webhook.secret)
            step("webhook", true, action.webhook.url)
        }

        action.hasUssd() -> {
            val template = action.ussd.code
            val code = AutomationRules.renderUssd(template, fields)
            when (val decision = pinGuard.authorize(sms.subscriptionId, template, code, fields["amount"]?.let(AutomationRules::number))) {
                is PinDecision.Denied -> step("ussd", false, "$code — ${decision.reason}")
                is PinDecision.Granted -> {
                    val outcome = ussdRunner.run(decision.code, sms.subscriptionId)
                    // The step reports the code with {pin} unresolved.
                    Device.AutomationStep.newBuilder()
                        .setAction("ussd")
                        .setOk(outcome.success)
                        .setDetail(if (outcome.success) code else "$code — ${outcome.errorMessage}")
                        .addAllResponses(outcome.responses)
                        .build()
                }
            }
        }

        else -> step("unknown", false, "this agent does not know this action")
    }

    private suspend fun withinLimits(a: Device.Automation, now: Long): Boolean {
        val hour = a.limits.perHour
        val day = a.limits.perDay
        if (hour > 0 && runs.countSince(a.id, now - HOUR_MS) >= hour) return false
        if (day > 0 && runs.countSince(a.id, now - DAY_MS) >= day) return false
        return true
    }

    /**
     * Sends finished runs for as long as the session lasts, like received SMS:
     * all at once, then on each new one, and again every few minutes for
     * reports whose acknowledgement was lost.
     */
    suspend fun report(send: suspend (Device.AutomationRun) -> Unit) {
        runs.prune(System.currentTimeMillis() - 2 * DAY_MS)
        while (true) {
            for (r in runs.unreported()) send(r.toProto())
            withTimeoutOrNull(RESEND_MS) { finished.receive() }
        }
    }

    suspend fun acknowledged(ids: List<String>) {
        if (ids.isNotEmpty()) runs.markReported(ids)
    }

    private fun RunRecord.toProto(): Device.AutomationRun = Device.AutomationRun.newBuilder()
        .setId(id)
        .setAutomationId(automationId)
        .setSender(sender)
        .setStartedAt(timestamp(startedAtMillis))
        .setFinishedAt(timestamp(finishedAtMillis ?: startedAtMillis))
        .setOk(ok)
        .setError(error)
        .addAllSteps(
            stepsBase64.split(",").filter { it.isNotEmpty() }.map { Device.AutomationStep.parseFrom(Base64.decode(it, Base64.NO_WRAP)) },
        )
        .build()

    private fun step(action: String, ok: Boolean, detail: String) =
        Device.AutomationStep.newBuilder().setAction(action).setOk(ok).setDetail(detail).build()

    private fun actionName(a: Device.AutomationAction) = when {
        a.hasForward() -> "forward"
        a.hasReplySms() -> "reply_sms"
        a.hasWebhook() -> "webhook"
        a.hasUssd() -> "ussd"
        else -> "unknown"
    }

    private fun timestamp(millis: Long) =
        Timestamp.newBuilder().setSeconds(millis / 1_000).setNanos(((millis % 1_000) * 1_000_000).toInt()).build()

    private fun smsKey(sms: IncomingSms): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${sms.sender}|${sms.receivedAtMillis}|${sms.body}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private companion object {
        const val TAG = "RelaixAutomation"
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
        const val RESEND_MS = 5 * 60_000L
        val E164 = Regex("""^\+[1-9][0-9]{7,14}$""")
    }
}

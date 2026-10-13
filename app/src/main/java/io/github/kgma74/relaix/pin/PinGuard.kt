package io.github.kgma74.relaix.pin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.kgma74.relaix.automation.AutomationRules
import io.github.kgma74.relaix.automation.PinUseDao
import io.github.kgma74.relaix.automation.PinUseRecord
import io.github.kgma74.relaix.policy.PolicyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A code template its owner approved on this phone, for one SIM, within caps.
 *
 * Set here and only here: the gateway can ask for a code, it cannot widen
 * what may use the PIN. A template edited from the dashboard is a different
 * string, so it needs approving again.
 */
data class PinApproval(
    /** 0: any SIM that has a PIN. */
    val subscriptionId: Int,
    /** e.g. "*144*2*1*{from}*{amount}*{pin}#". */
    val template: String,
    /** Largest {amount} per operation; null: no amount cap. */
    val maxAmount: Double?,
    /** Operations per 24 hours. */
    val maxPerDay: Int,
    /** Sum of {amount} per 24 hours; null: no cap. */
    val maxTotalPerDay: Double?,
    val approvedAtMillis: Long,
)

/** Whether a code may use the PIN, and the code to dial when it may. */
sealed interface PinDecision {
    data class Granted(val code: String) : PinDecision
    data class Denied(val reason: String) : PinDecision
}

/** A template with {pin} that an automation uses and nobody approved yet. */
data class PendingTemplate(val subscriptionId: Int, val template: String, val automation: String)

@Singleton
class PinGuard @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val vault: PinVault,
    private val uses: PinUseDao,
    private val policyStore: PolicyStore,
) {
    // One decision at a time: two codes racing must not both fit under a cap.
    private val decisions = Mutex()

    val approvals: Flow<List<PinApproval>> = dataStore.data.map { decode(it[KEY]) }

    /** Templates the dashboard's automations need, not approved yet. */
    val pending: Flow<List<PendingTemplate>> = combine(policyStore.policy, approvals) { policy, approved ->
        policy?.automationsList.orEmpty().flatMap { a ->
            a.actionsList.filter { it.hasUssd() && it.ussd.code.contains(PIN) }.map { PendingTemplate(a.subscriptionId, it.ussd.code, a.name) }
        }.filter { p -> approved.none { it.template == p.template && (it.subscriptionId == p.subscriptionId || it.subscriptionId == 0) } }
            .distinct()
    }

    suspend fun approve(approval: PinApproval) = dataStore.edit { prefs ->
        val list = decode(prefs[KEY]).filterNot { it.template == approval.template && it.subscriptionId == approval.subscriptionId }
        prefs[KEY] = encode(list + approval)
    }

    suspend fun revoke(subscriptionId: Int, template: String) = dataStore.edit { prefs ->
        prefs[KEY] = encode(decode(prefs[KEY]).filterNot { it.template == template && it.subscriptionId == subscriptionId })
    }

    /**
     * Decides for a code about to be dialed on [sim].
     *
     * @param template the automation's template, when an automation asks;
     *   null for a code from the gateway, which is then matched against the
     *   approved templates.
     * @param code the code with every field filled in but {pin}.
     * @param amount the operation's amount, when known.
     */
    suspend fun authorize(sim: Int, template: String?, code: String, amount: Double? = null): PinDecision = decisions.withLock {
        if (!code.contains(PIN)) return@withLock PinDecision.Granted(code)
        val candidates = approvals.first().filter { it.subscriptionId == sim || it.subscriptionId == 0 }
        val (approval, found) = if (template != null) {
            candidates.firstOrNull { it.template == template } to amount
        } else {
            candidates.firstNotNullOfOrNull { a -> matchTemplate(a.template, code)?.let { fields -> a to fields["amount"]?.let(AutomationRules::number) } }
                ?: (null to null)
        }
        if (approval == null) return@withLock PinDecision.Denied("this code is not approved on the phone for SIM $sim")

        val since = System.currentTimeMillis() - DAY_MS
        val max = approval.maxAmount
        if (max != null && (found == null || found > max)) {
            return@withLock PinDecision.Denied("amount ${found ?: "unknown"} is above the approved ${max.toLong()}")
        }
        if (uses.countSince(sim, approval.template, since) >= approval.maxPerDay) {
            return@withLock PinDecision.Denied("${approval.maxPerDay} operation(s) per day already used")
        }
        val total = approval.maxTotalPerDay
        if (total != null && uses.totalSince(sim, approval.template, since) + (found ?: 0.0) > total) {
            return@withLock PinDecision.Denied("the daily total of ${total.toLong()} would be exceeded")
        }
        val pin = vault.get(sim) ?: vault.get(0)
            ?: return@withLock PinDecision.Denied("no PIN is set on the phone for SIM $sim")

        // Counted before dialing: an attempt the network ran but did not
        // confirm must still count against the caps.
        uses.insert(PinUseRecord(UUID.randomUUID().toString(), sim, approval.template, found ?: 0.0, System.currentTimeMillis()))
        PinDecision.Granted(code.replace(PIN, pin))
    }

    companion object {
        const val PIN = "{pin}"
        private const val DAY_MS = 24 * 3_600_000L
        private val KEY = stringPreferencesKey("pin_approvals")
        private val placeholder = Regex("""\{([A-Za-z][A-Za-z0-9]*)\}""")

        /**
         * The fields of [code] when it is [template] filled in — {pin} kept as
         * is, every other field a run of digits (or a + number). Null when it
         * is not.
         */
        fun matchTemplate(template: String, code: String): Map<String, String>? {
            val names = mutableListOf<String>()
            val regex = buildString {
                var last = 0
                for (m in placeholder.findAll(template)) {
                    append(Regex.escape(template.substring(last, m.range.first)))
                    val name = m.groupValues[1]
                    if (name == "pin") {
                        append(Regex.escape(PIN))
                    } else {
                        names += name
                        append("(\\+?[0-9]+)")
                    }
                    last = m.range.last + 1
                }
                append(Regex.escape(template.substring(last)))
            }
            val m = Regex(regex).matchEntire(code) ?: return null
            return names.mapIndexed { i, n -> n to m.groupValues[i + 1] }.toMap()
        }

        private fun encode(list: List<PinApproval>): String = JSONArray(
            list.map {
                JSONObject()
                    .put("sim", it.subscriptionId)
                    .put("template", it.template)
                    .put("maxAmount", it.maxAmount ?: JSONObject.NULL)
                    .put("maxPerDay", it.maxPerDay)
                    .put("maxTotalPerDay", it.maxTotalPerDay ?: JSONObject.NULL)
                    .put("approvedAt", it.approvedAtMillis)
            },
        ).toString()

        private fun decode(raw: String?): List<PinApproval> {
            if (raw.isNullOrEmpty()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    PinApproval(
                        subscriptionId = o.getInt("sim"),
                        template = o.getString("template"),
                        maxAmount = if (o.isNull("maxAmount")) null else o.getDouble("maxAmount"),
                        maxPerDay = o.getInt("maxPerDay"),
                        maxTotalPerDay = if (o.isNull("maxTotalPerDay")) null else o.getDouble("maxTotalPerDay"),
                        approvedAtMillis = o.getLong("approvedAt"),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}

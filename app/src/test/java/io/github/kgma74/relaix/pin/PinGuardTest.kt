package io.github.kgma74.relaix.pin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.kgma74.relaix.automation.PinUseDao
import io.github.kgma74.relaix.automation.PinUseRecord
import io.github.kgma74.relaix.policy.PolicyStore
import io.github.kgma74.relaix.security.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import smsgateway.v1.Device

class PinGuardTest {
    /** In memory: the file-backed one cannot rename over its file on Windows. */
    private val dataStore = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    /** Reversible and visibly not plaintext, enough to exercise the vault. */
    private val cipher = object : SecretCipher {
        override fun encrypt(plaintext: String) = "enc:" + plaintext.reversed()
        override fun decrypt(blob: String) = blob.removePrefix("enc:").reversed()
    }

    private val uses = object : PinUseDao {
        val rows = mutableListOf<PinUseRecord>()
        override suspend fun insert(use: PinUseRecord) {
            rows += use
        }
        override suspend fun countSince(sim: Int, template: String, since: Long) =
            rows.count { it.subscriptionId == sim && it.template == template && it.atMillis >= since }
        override suspend fun totalSince(sim: Int, template: String, since: Long) =
            rows.filter { it.subscriptionId == sim && it.template == template && it.atMillis >= since }.sumOf { it.amount }
    }

    private val vault = PinVault(dataStore, cipher)
    private val policyStore = PolicyStore(dataStore)
    private val guard = PinGuard(dataStore, vault, uses, policyStore)

    private val template = "*144*2*1*{to}*{amount}*{pin}#"

    private fun approve(maxAmount: Double? = 10_000.0, perDay: Int = 2, total: Double? = 15_000.0) = runBlocking {
        guard.approve(PinApproval(1, template, maxAmount, perDay, total, 0))
    }

    @Test
    fun `refused until approved and while no PIN is set`() = runBlocking {
        val code = "*144*2*1*70000000*5000*{pin}#"
        assertDenied(guard.authorize(1, null, code), "not approved")
        approve()
        assertDenied(guard.authorize(1, null, code), "no PIN")
        vault.set(1, "1234")
        assertEquals(PinDecision.Granted("*144*2*1*70000000*5000*1234#"), guard.authorize(1, null, code))
        // The PIN is stored encrypted, never as itself.
        assertTrue(dataStore.data.first().asMap().values.none { it == "1234" })
    }

    @Test
    fun `caps per operation, per day and on the daily total`() = runBlocking {
        vault.set(1, "1234")
        approve(maxAmount = 10_000.0, perDay = 2, total = 15_000.0)
        assertDenied(guard.authorize(1, null, "*144*2*1*70000000*20000*{pin}#"), "above")
        assertTrue(guard.authorize(1, null, "*144*2*1*70000000*9000*{pin}#") is PinDecision.Granted)
        assertDenied(guard.authorize(1, null, "*144*2*1*70000000*9000*{pin}#"), "daily total")
        assertTrue(guard.authorize(1, null, "*144*2*1*70000000*1000*{pin}#") is PinDecision.Granted)
        assertDenied(guard.authorize(1, null, "*144*2*1*70000000*1*{pin}#"), "per day")
    }

    @Test
    fun `an automation's template is pending until approved, and a changed one again`() = runBlocking {
        val automation = Device.Automation.newBuilder().setId("pay").setName("reverser").setSubscriptionId(1)
            .addActions(Device.AutomationAction.newBuilder().setUssd(Device.UssdAction.newBuilder().setCode(template)))
            .build()
        policyStore.save(Device.DevicePolicy.newBuilder().addAutomations(automation).build())
        assertEquals(listOf(PendingTemplate(1, template, "reverser")), guard.pending.first())

        approve()
        assertTrue(guard.pending.first().isEmpty())

        val edited = automation.toBuilder().setActions(0, Device.AutomationAction.newBuilder().setUssd(Device.UssdAction.newBuilder().setCode("*144*2*2*{to}*{amount}*{pin}#"))).build()
        policyStore.save(Device.DevicePolicy.newBuilder().addAutomations(edited).build())
        assertEquals(1, guard.pending.first().size)
    }

    @Test
    fun `a code without pin needs nothing`() = runBlocking {
        assertEquals(PinDecision.Granted("*144#"), guard.authorize(1, null, "*144#"))
    }

    private fun assertDenied(d: PinDecision, reason: String) {
        assertTrue("expected a refusal about '$reason', got $d", d is PinDecision.Denied && d.reason.contains(reason))
    }
}

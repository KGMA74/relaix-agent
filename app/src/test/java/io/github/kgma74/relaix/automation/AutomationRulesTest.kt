package io.github.kgma74.relaix.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import smsgateway.v1.Device

/**
 * The cases shared with the gateway and the dashboard
 * (server/policy/testdata/automation_vectors.json, copied to test resources):
 * the phone must match and extract exactly as they do.
 */
class AutomationRulesTest {

    @Test
    fun `the shared automation cases`() {
        val text = javaClass.classLoader!!.getResource("automation_vectors.json")!!.readText()
        val cases = JSONObject(text).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val name = c.getString("name")
            val sms = c.getJSONObject("sms")
            val fields = AutomationRules.match(
                automation(c.getJSONObject("automation")),
                sms.getString("sender"), sms.getString("body"), sms.getString("receivedAt"), sms.getInt("sim"),
            )
            assertEquals(name, c.getBoolean("match"), fields != null)
            c.optJSONObject("fields")?.let { want ->
                for (k in want.keys()) assertEquals("$name: $k", want.getString(k), fields!![k])
            }
            if (c.has("template")) assertEquals(name, c.getString("rendered"), AutomationRules.render(c.getString("template"), fields!!))
        }
    }

    @Test
    fun `pin is kept for the vault, and only when asked`() {
        val fields = mapOf("amount" to "5000")
        assertEquals("*1*5000*{pin}#", AutomationRules.render("*1*{amount}*{pin}#", fields, keepPin = true))
        assertEquals("*1*5000*#", AutomationRules.render("*1*{amount}*{pin}#", fields))
    }

    @Test
    fun `a USSD code gets amounts without their spaces`() {
        assertEquals("*144*70123456*5000*{pin}#", AutomationRules.renderUssd("*144*{from}*{amount}*{pin}#", mapOf("from" to "70123456", "amount" to "5 000")))
    }

    @Test
    fun `an invalid pattern never matches`() {
        val a = Device.Automation.newBuilder().setEnabled(true).setPattern("(").build()
        assertNull(AutomationRules.match(a, "x", "y", "", 0))
    }

    private fun automation(o: JSONObject): Device.Automation {
        val b = Device.Automation.newBuilder()
            .setEnabled(o.optBoolean("enabled"))
            .setSubscriptionId(o.optInt("subscriptionId"))
            .setPattern(o.optString("pattern"))
        o.optJSONArray("senders")?.let { b.addAllSenders(it.strings()) }
        o.optJSONArray("conditions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                b.addConditions(Device.AutomationCondition.newBuilder().setField(c.getString("field")).setOp(c.getString("op")).setValue(c.getString("value")))
            }
        }
        return b.build()
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}

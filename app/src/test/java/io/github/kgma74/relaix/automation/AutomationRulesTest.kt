package io.github.kgma74.relaix.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import smsgateway.v1.Device

/**
 * The cases shared with the gateway and the dashboard
 * (server/policy/testdata/automation_vectors.json, copied to test resources):
 * the phone must match and extract exactly as they do.
 */
class AutomationRulesTest {

    @Test
    fun `an amount goes into a USSD code as the network will read it`() {
        val template = "*144*2*1*{payer}*{amount}#"
        // The bug this guards: "200.0" dialled lost its dot, and 2000 left.
        assertEquals("*144*2*1*65530101*200#", AutomationRules.renderUssd(template, mapOf("payer" to "65530101", "amount" to "200.0")))
        assertEquals("*144*2*1*65530101*5000#", AutomationRules.renderUssd(template, mapOf("payer" to "65 53 01 01", "amount" to "5 000,00")))
        assertTrue(AutomationRules.isDialable(AutomationRules.renderUssd(template, mapOf("payer" to "1", "amount" to "200"))))
        assertTrue(AutomationRules.isDialable("*144*4*{pin}#"))

        // Real decimals cannot be dialled: refused, never sent as another amount.
        val decimal = AutomationRules.renderUssd(template, mapOf("payer" to "65530101", "amount" to "200.5"))
        assertEquals("*144*2*1*65530101*200.5#", decimal)
        assertFalse(AutomationRules.isDialable(decimal))
        assertFalse(AutomationRules.isDialable("*144*2*1*abc#"))
    }

    @Test
    fun `a PIN cap is about the amount dialled, calculation included`() {
        val fields = mapOf("payer" to "65530101", "amount" to "5 000")
        assertEquals(5000.0, AutomationRules.ussdAmount("*144*{payer}*{amount}*{pin}#", fields)!!, 0.0)
        assertEquals(10000.0, AutomationRules.ussdAmount("*144*{payer}*{amount * 2}*{pin}#", fields)!!, 0.0)
        assertNull(AutomationRules.ussdAmount("*144*{payer}*{pin}#", fields))
        assertEquals(listOf("amount", "fee"), AutomationRules.fieldsOf("(amount + fee) * 0.98"))
    }

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
            if (c.has("jsonTemplate")) {
                val template = c.getString("jsonTemplate")
                assertTrue(name, AutomationRules.isJsonTemplate(template))
                assertEquals(name, c.getString("jsonRendered"), AutomationRules.renderJson(template, fields!!))
            }
            if (c.has("ussdTemplate")) assertEquals(name, c.getString("ussdRendered"), AutomationRules.renderUssd(c.getString("ussdTemplate"), fields!!))
            c.optJSONObject("numbers")?.let { want ->
                assertEquals(name, want.keys().asSequence().associateWith { want.getString(it) }, AutomationRules.numbers(fields!!))
            }
        }
    }

    @Test
    fun `a body is JSON when it opens like an object or an array`() {
        assertTrue(AutomationRules.isJsonTemplate("""{"a": 1}"""))
        assertTrue(AutomationRules.isJsonTemplate(""" { "a": {amount}}"""))
        assertTrue(AutomationRules.isJsonTemplate("{}"))
        assertTrue(AutomationRules.isJsonTemplate("[{amount}]"))
        assertFalse(AutomationRules.isJsonTemplate("{amount}"))
        assertFalse(AutomationRules.isJsonTemplate("{amount} recus"))
        assertFalse(AutomationRules.isJsonTemplate(""))
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

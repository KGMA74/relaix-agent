package io.github.kgma74.relaix.pin

import io.github.kgma74.relaix.automation.WebhookOutbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinTemplateTest {

    @Test
    fun `a code from the gateway matches its approved template and yields the amount`() {
        val fields = PinGuard.matchTemplate("*144*2*1*{to}*{amount}*{pin}#", "*144*2*1*70000000*5000*{pin}#")
        assertEquals(mapOf("to" to "70000000", "amount" to "5000"), fields)
    }

    @Test
    fun `anything else does not`() {
        val t = "*144*2*1*{to}*{amount}*{pin}#"
        assertNull(PinGuard.matchTemplate(t, "*144*2*2*70000000*5000*{pin}#")) // another menu
        assertNull(PinGuard.matchTemplate(t, "*144*2*1*70000000*5000*1234#")) // a PIN typed in
        assertNull(PinGuard.matchTemplate(t, "*144*2*1*7000*5000*9*{pin}#")) // an extra step
        assertNull(PinGuard.matchTemplate(t, "*144*2*1*abc*5000*{pin}#"))
    }

    @Test
    fun `webhooks are signed like the gateway's callbacks`() {
        // callback.Sign([]byte("s3cret"), "1700000000", []byte(`{"a":1}`)) in the gateway.
        assertEquals(
            "1698a50bc74d1ff1db85c4e0a5297c2ad9fdba245d5737cdb789e4cc6e098940",
            WebhookOutbox.sign("s3cret", "1700000000", """{"a":1}""".toByteArray()),
        )
    }
}

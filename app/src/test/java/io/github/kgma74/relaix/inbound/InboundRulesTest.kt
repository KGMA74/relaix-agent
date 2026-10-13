package io.github.kgma74.relaix.inbound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import smsgateway.v1.Device

class InboundRulesTest {

    private fun rules(enabled: Boolean, vararg senders: String) =
        Device.InboundRules.newBuilder().setEnabled(enabled).addAllSenders(senders.toList()).build()

    @Test
    fun `nothing is forwarded while forwarding is off`() {
        assertFalse(InboundForwarder.forwards(rules(false, "OrangeMoney"), "OrangeMoney"))
    }

    @Test
    fun `no senders means every sender`() {
        assertTrue(InboundForwarder.forwards(rules(true), "Maman"))
    }

    @Test
    fun `named senders match whole and ignore case, like the server`() {
        val r = rules(true, "OrangeMoney", "+22670000000")
        assertTrue(InboundForwarder.forwards(r, "orangemoney"))
        assertTrue(InboundForwarder.forwards(r, " OrangeMoney "))
        assertTrue(InboundForwarder.forwards(r, "+22670000000"))
        assertFalse(InboundForwarder.forwards(r, "OrangeMoney2"))
        assertFalse(InboundForwarder.forwards(r, "MoovMoney"))
    }
}

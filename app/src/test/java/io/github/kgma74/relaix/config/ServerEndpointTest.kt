package io.github.kgma74.relaix.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEndpointTest {

    @Test
    fun `parses the cleartext dev endpoint an emulator or adb reverse uses`() {
        val endpoint = ServerEndpoint.parse("grpc://10.0.2.2:9090").getOrThrow()

        assertEquals("10.0.2.2", endpoint.host)
        assertEquals(9090, endpoint.port)
        assertEquals(false, endpoint.useTls)
        assertEquals("10.0.2.2:9090", endpoint.authority)
    }

    @Test
    fun `grpcs means TLS`() {
        val endpoint = ServerEndpoint.parse("grpcs://relaix.example:443").getOrThrow()

        assertEquals("relaix.example", endpoint.host)
        assertEquals(443, endpoint.port)
        assertEquals(true, endpoint.useTls)
    }

    @Test
    fun `round-trips through toString`() {
        val raw = "grpcs://relaix.example:8443"

        assertEquals(raw, ServerEndpoint.parse(raw).getOrThrow().toString())
    }

    @Test
    fun `scheme is case-insensitive`() {
        assertEquals(
            ServerEndpoint("relaix.example", 443, useTls = true),
            ServerEndpoint.parse("GRPCS://relaix.example:443").getOrThrow(),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            ServerEndpoint("10.0.2.2", 9090, useTls = false),
            ServerEndpoint.parse("  grpc://10.0.2.2:9090\n").getOrThrow(),
        )
    }

    @Test
    fun `a trailing slash is tolerated`() {
        assertEquals(
            ServerEndpoint("10.0.2.2", 9090, useTls = false),
            ServerEndpoint.parse("grpc://10.0.2.2:9090/").getOrThrow(),
        )
    }

    @Test
    fun `an IPv6 literal keeps its brackets as the host`() {
        // lastIndexOf(':') is what makes this work: the address itself is full
        // of colons, and only the last one separates the port.
        val endpoint = ServerEndpoint.parse("grpc://[::1]:9090").getOrThrow()

        assertEquals("[::1]", endpoint.host)
        assertEquals(9090, endpoint.port)
    }

    @Test
    fun `rejects a missing scheme rather than assuming cleartext`() {
        assertRejected("10.0.2.2:9090", "must start with")
    }

    @Test
    fun `rejects a scheme that is not grpc`() {
        assertRejected("https://relaix.example:443", "unsupported scheme")
    }

    @Test
    fun `rejects a missing port instead of guessing one`() {
        assertRejected("grpc://10.0.2.2", "must include a port")
    }

    @Test
    fun `rejects a missing host`() {
        assertRejected("grpc://:9090", "no host")
    }

    @Test
    fun `rejects an empty endpoint`() {
        assertRejected("   ", "empty")
    }

    @Test
    fun `rejects a non-numeric port`() {
        assertRejected("grpc://relaix.example:jambon", "not a number")
    }

    @Test
    fun `rejects a port outside the valid range`() {
        assertRejected("grpc://relaix.example:0", "out of range")
        assertRejected("grpc://relaix.example:70000", "out of range")
    }

    @Test
    fun `rejects a path rather than silently dropping it`() {
        assertRejected("grpc://relaix.example:9090/v1", "must not contain a path")
    }

    private fun assertRejected(raw: String, expectedMessageFragment: String) {
        val result = ServerEndpoint.parse(raw)

        assertTrue("expected '$raw' to be rejected", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "message '$message' should mention '$expectedMessageFragment'",
            message.contains(expectedMessageFragment),
        )
    }
}

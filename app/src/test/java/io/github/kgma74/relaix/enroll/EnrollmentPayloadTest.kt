package io.github.kgma74.relaix.enroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `org.json` ships as a throwing stub inside android.jar, so these tests pull
 * the real implementation in as a test-only dependency — see
 * app/build.gradle.kts. Without it every case here would fail with
 * "not mocked" rather than exercising the parser.
 */
class EnrollmentPayloadTest {

    @Test
    fun `parses what the server puts in the QR`() {
        val raw = """{"endpoint":"grpc://10.0.2.2:9090","token":"abc123"}"""

        val payload = EnrollmentPayload.parse(raw).getOrThrow()

        assertEquals("10.0.2.2", payload.endpoint.host)
        assertEquals(9090, payload.endpoint.port)
        assertEquals(false, payload.endpoint.useTls)
        assertEquals("abc123", payload.token)
    }

    @Test
    fun `accepts a TLS endpoint`() {
        val raw = """{"endpoint":"grpcs://relaix.example:443","token":"t"}"""

        assertEquals(true, EnrollmentPayload.parse(raw).getOrThrow().endpoint.useTls)
    }

    @Test
    fun `tolerates surrounding whitespace from a paste`() {
        val raw = "\n  {\"endpoint\":\"grpc://127.0.0.1:9090\",\"token\":\"t\"}  \n"

        assertEquals("127.0.0.1", EnrollmentPayload.parse(raw).getOrThrow().endpoint.host)
    }

    @Test
    fun `accepts the escaped form copied out of the curl response`() {
        // What `POST /admin/devices/enroll-token` shows in its "payload" field.
        val raw = """{\"endpoint\":\"grpc://127.0.0.1:9090\",\"token\":\"abc\"}"""

        val payload = EnrollmentPayload.parse(raw).getOrThrow()

        assertEquals("127.0.0.1", payload.endpoint.host)
        assertEquals("abc", payload.token)
    }

    @Test
    fun `rejects text that is not JSON`() {
        assertRejected("just a token", "not valid JSON")
    }

    @Test
    fun `rejects an empty payload`() {
        assertRejected("   ", "empty")
    }

    @Test
    fun `rejects a payload without an endpoint`() {
        assertRejected("""{"token":"t"}""", "no 'endpoint'")
    }

    @Test
    fun `rejects a payload without a token`() {
        assertRejected("""{"endpoint":"grpc://10.0.2.2:9090"}""", "no 'token'")
    }

    @Test
    fun `reports the endpoint problem rather than a generic failure`() {
        assertRejected("""{"endpoint":"10.0.2.2:9090","token":"t"}""", "must start with")
    }

    @Test
    fun `a JSON null endpoint is treated as missing`() {
        assertRejected("""{"endpoint":null,"token":"t"}""", "no 'endpoint'")
    }

    private fun assertRejected(raw: String, expectedMessageFragment: String) {
        val result = EnrollmentPayload.parse(raw)

        assertTrue("expected '$raw' to be rejected", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "message '$message' should mention '$expectedMessageFragment'",
            message.contains(expectedMessageFragment),
        )
    }
}

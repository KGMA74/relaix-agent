package io.github.kgma74.relaix.automation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Webhooks the phone posts itself, kept on disk until the receiver takes them
 * — the gateway does not have to be reachable, the receiver does.
 *
 * Signed like the gateway's callbacks: X-Relaix-Timestamp, and
 * X-Relaix-Signature = "sha256=" + hex HMAC-SHA256(secret, timestamp + "." + body).
 * X-Relaix-Delivery names the delivery and stays the same across retries.
 */
@Singleton
class WebhookOutbox @Inject constructor(private val dao: OutboxDao) {
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * @param delivery stable for what is being told — posted as
     *   X-Relaix-Delivery on every attempt, and the record's key, so the same
     *   delivery queued twice is posted once.
     */
    suspend fun enqueue(delivery: String, url: String, body: String, secret: String) {
        dao.insert(OutboxRecord(delivery, url, body, secret, nextAttemptAtMillis = System.currentTimeMillis()))
        wake.trySend(Unit)
    }

    /** Posts what is due, for as long as the agent runs. */
    suspend fun run() {
        while (true) {
            for (record in dao.due(System.currentTimeMillis())) deliver(record)
            val next = dao.nextDue()
            val wait = if (next == null) IDLE_MS else (next - System.currentTimeMillis()).coerceIn(1_000L, IDLE_MS)
            withTimeoutOrNull(wait) { wake.receive() }
        }
    }

    private suspend fun deliver(r: OutboxRecord) {
        val status = try {
            post(r)
        } catch (e: Exception) {
            -1 to (e.message ?: e::class.java.simpleName)
        }
        val (code, error) = status
        when {
            code in 200..299 -> dao.delete(r.id)
            // Refused outright: it will be refused again.
            code in 400..499 && code != 429 -> {
                Log.w(TAG, "webhook ${r.url} refused with $code; dropped")
                dao.delete(r.id)
            }
            r.attempts + 1 >= MAX_ATTEMPTS -> {
                Log.w(TAG, "webhook ${r.url} abandoned after ${r.attempts + 1} attempts: $error")
                dao.delete(r.id)
            }
            else -> {
                val delay = (BASE_MS shl r.attempts.coerceAtMost(10)).coerceAtMost(MAX_DELAY_MS)
                dao.retry(r.id, System.currentTimeMillis() + delay, error)
            }
        }
    }

    private suspend fun post(r: OutboxRecord): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL(r.url).openConnection() as HttpURLConnection
        try {
            val body = r.body.toByteArray(Charsets.UTF_8)
            val stamp = (System.currentTimeMillis() / 1_000).toString()
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            val json = r.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
            conn.setRequestProperty("Content-Type", if (json) "application/json" else "text/plain; charset=utf-8")
            conn.setRequestProperty("User-Agent", "relaix-agent")
            conn.setRequestProperty("X-Relaix-Timestamp", stamp)
            conn.setRequestProperty("X-Relaix-Delivery", r.id)
            if (r.secret.isNotEmpty()) conn.setRequestProperty("X-Relaix-Signature", "sha256=" + sign(r.secret, stamp, body))
            conn.outputStream.use { it.write(body) }
            conn.responseCode to "status ${conn.responseCode}"
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "RelaixWebhook"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_ATTEMPTS = 10
        private const val BASE_MS = 30_000L
        private const val MAX_DELAY_MS = 3_600_000L
        private const val IDLE_MS = 300_000L

        fun sign(secret: String, timestamp: String, body: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            mac.update(timestamp.toByteArray(Charsets.UTF_8))
            mac.update('.'.code.toByte())
            mac.update(body)
            return mac.doFinal().joinToString("") { "%02x".format(it) }
        }
    }
}

package io.github.kgma74.relaix.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {

    /** Removes the jitter so the growth curve itself can be asserted. */
    private fun noJitter() = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(from: Long, until: Long): Long = until - 1
    }

    @Test
    fun `grows exponentially from the initial delay`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 60_000, random = noJitter())

        assertEquals(1_000, backoff.nextDelayMillis())
        assertEquals(2_000, backoff.nextDelayMillis())
        assertEquals(4_000, backoff.nextDelayMillis())
        assertEquals(8_000, backoff.nextDelayMillis())
    }

    @Test
    fun `stops growing at the cap`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 10_000, random = noJitter())

        repeat(20) { backoff.nextDelayMillis() }

        assertEquals(10_000, backoff.nextDelayMillis())
    }

    @Test
    fun `stays positive after a very long outage`() {
        // Guards the shift overflow: without the exponent cap this turns
        // negative around attempt 63 and delay() would stop waiting at all.
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 60_000, random = noJitter())

        repeat(200) {
            val delay = backoff.nextDelayMillis()
            assertTrue("delay must stay positive, was $delay", delay > 0)
            assertTrue("delay must respect the cap, was $delay", delay <= 60_000)
        }
    }

    @Test
    fun `jitter only ever shortens the delay`() {
        val backoff = Backoff(initialMillis = 10_000, maxMillis = 10_000)

        repeat(100) {
            val delay = backoff.nextDelayMillis()
            // 20% downward jitter: never above the ceiling, never below 80%.
            assertTrue("delay $delay exceeded the ceiling", delay <= 10_000)
            assertTrue("delay $delay fell below the jitter floor", delay >= 8_000)
        }
    }

    @Test
    fun `spreads retries so a fleet does not return in lockstep`() {
        val delays = (1..200).map { seed ->
            Backoff(initialMillis = 10_000, maxMillis = 10_000, random = Random(seed))
                .nextDelayMillis()
        }

        assertTrue(
            "expected jitter to produce varied delays, got ${delays.distinct().size} distinct",
            delays.distinct().size > 50,
        )
    }

    @Test
    fun `reset returns to the initial delay after a successful connection`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 60_000, random = noJitter())
        repeat(5) { backoff.nextDelayMillis() }

        backoff.reset()

        assertEquals(1_000, backoff.nextDelayMillis())
    }
}

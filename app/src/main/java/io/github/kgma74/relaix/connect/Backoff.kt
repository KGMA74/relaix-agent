package io.github.kgma74.relaix.connect

import kotlin.random.Random

/**
 * Reconnection delays: exponential, capped, with jitter downwards.
 *
 * The jitter is not decoration. Every phone in a fleet loses its stream at
 * the same instant when the server restarts, so a deterministic schedule
 * would have all of them return in lockstep and do it again on the next
 * failure. Spreading the retries is what keeps a restart from being a
 * self-inflicted thundering herd.
 *
 * Jitter goes down and never up so the cap stays a real ceiling.
 */
class Backoff(
    private val initialMillis: Long = INITIAL_MILLIS,
    private val maxMillis: Long = MAX_MILLIS,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    /** Next delay, in milliseconds. Advances the sequence. */
    fun nextDelayMillis(): Long {
        // Cap the exponent before shifting: at attempt 63 the shift would
        // overflow to a negative delay, and the bug would only appear on a
        // device that had been failing for a very long time.
        val exponent = attempt.coerceAtMost(MAX_EXPONENT)
        val ceiling = (initialMillis shl exponent).coerceAtMost(maxMillis)
        attempt++

        val floor = (ceiling * (1 - JITTER_FRACTION)).toLong()
        return random.nextLong(floor, ceiling + 1)
    }

    /** Called after a successful registration, so the next drop starts short again. */
    fun reset() {
        attempt = 0
    }

    private companion object {
        const val INITIAL_MILLIS = 1_000L
        const val MAX_MILLIS = 60_000L
        const val MAX_EXPONENT = 16
        const val JITTER_FRACTION = 0.2
    }
}

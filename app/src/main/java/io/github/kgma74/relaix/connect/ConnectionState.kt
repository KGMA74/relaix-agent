package io.github.kgma74.relaix.connect

/**
 * What the UI and the service can say about the stream without knowing how it
 * is built.
 */
sealed interface ConnectionState {
    /** No enrollment yet, or the agent was asked to stop. */
    data object Idle : ConnectionState

    data object Connecting : ConnectionState

    /** Registered and acknowledged; the server may push jobs. */
    data class Connected(val heartbeatSeconds: Int) : ConnectionState

    /**
     * Dropped and waiting to retry. [reason] is kept because "why" is the
     * first question when a phone stops taking work, and [retryInSeconds]
     * because a silent wait looks identical to a hang.
     */
    data class Reconnecting(val reason: String, val retryInSeconds: Long) : ConnectionState

    /**
     * An operator disabled this device. The token is still good, so the agent
     * keeps asking, slowly: re-enabling it from the dashboard must bring the
     * phone back without anyone touching it.
     */
    data class Disabled(val retryInSeconds: Long) : ConnectionState

    /**
     * The server does not recognise this device's credential — deleted, or
     * re-enrolled elsewhere. Retrying cannot fix it, so the agent stops rather
     * than hammering a stream it will never be allowed onto.
     */
    data class Refused(val reason: String) : ConnectionState
}

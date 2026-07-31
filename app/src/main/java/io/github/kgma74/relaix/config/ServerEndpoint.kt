package io.github.kgma74.relaix.config

/**
 * Where the agent dials the control plane, and whether that connection is
 * encrypted.
 *
 * The value is not configured by hand: it arrives inside the enrollment QR
 * code, alongside the token, as a URL the server built from its own
 * `RELAIX_PUBLIC_URL` (see architecture.md §7). Parsing therefore has to be
 * strict — a phone that silently dials the wrong host or falls back to
 * cleartext is worse than one that refuses to enroll.
 */
data class ServerEndpoint(
    val host: String,
    val port: Int,
    val useTls: Boolean,
) {
    /** The `host:port` form gRPC's channel builder expects. */
    val authority: String get() = "$host:$port"

    /** Round-trips back to the URL form the server emits. */
    override fun toString(): String = "${if (useTls) TLS_SCHEME else PLAINTEXT_SCHEME}://$authority"

    companion object {
        const val PLAINTEXT_SCHEME = "grpc"
        const val TLS_SCHEME = "grpcs"

        /**
         * Parses the endpoint URL carried by the enrollment QR code.
         *
         * The port is required rather than defaulted. gRPC has no
         * well-known port to fall back on, and the dev stack (9090) and a
         * TLS deployment (443) differ, so guessing would turn a typo in the
         * server's configuration into a connection that fails much later,
         * far from its cause.
         */
        fun parse(raw: String): Result<ServerEndpoint> {
            val input = raw.trim()
            if (input.isEmpty()) return failure("endpoint is empty")

            val separator = input.indexOf("://")
            if (separator < 0) return failure("endpoint must start with $PLAINTEXT_SCHEME:// or $TLS_SCHEME://")

            val scheme = input.substring(0, separator).lowercase()
            val useTls = when (scheme) {
                PLAINTEXT_SCHEME -> false
                TLS_SCHEME -> true
                else -> return failure("unsupported scheme '$scheme': expected $PLAINTEXT_SCHEME or $TLS_SCHEME")
            }

            // A gRPC target is an authority, not a path. Tolerate the trailing
            // slash a URL builder may add; reject anything that looks like a
            // route, since silently dropping it would hide a real mistake.
            val remainder = input.substring(separator + 3).removeSuffix("/")
            if (remainder.contains('/')) return failure("endpoint must not contain a path")
            if (remainder.isEmpty()) return failure("endpoint has no host")

            val colon = remainder.lastIndexOf(':')
            if (colon < 0) return failure("endpoint must include a port, e.g. $PLAINTEXT_SCHEME://10.0.2.2:9090")

            val host = remainder.substring(0, colon)
            if (host.isEmpty()) return failure("endpoint has no host")

            val port = remainder.substring(colon + 1).toIntOrNull()
                ?: return failure("port is not a number")
            if (port !in 1..65535) return failure("port $port is out of range")

            return Result.success(ServerEndpoint(host = host, port = port, useTls = useTls))
        }

        private fun failure(message: String): Result<ServerEndpoint> =
            Result.failure(IllegalArgumentException(message))
    }
}

package io.github.kgma74.relaix.enroll

import io.github.kgma74.relaix.config.ServerEndpoint
import org.json.JSONObject

/**
 * What the enrollment QR code carries: where to dial, and the single-use token
 * that authorizes joining the fleet.
 *
 * The server builds this as JSON in `handleEnrollToken` — endpoint alongside
 * token, precisely so the phone needs no separate manual configuration step
 * (architecture.md §7).
 */
data class EnrollmentPayload(
    val endpoint: ServerEndpoint,
    val token: String,
) {
    companion object {
        /**
         * Parses the QR payload, whether it was scanned or pasted by hand.
         *
         * Errors are returned rather than thrown so the enrollment screen can
         * show which half is wrong — a mistyped host and an expired token are
         * very different problems for whoever is holding the phone.
         */
        fun parse(raw: String): Result<EnrollmentPayload> {
            val input = raw.trim()
            if (input.isEmpty()) return failure("payload is empty")

            val json = try {
                JSONObject(input)
            } catch (_: Exception) {
                // Second chance for a payload whose quotes arrive escaped.
                // That is not a corner case: `POST /admin/devices/enroll-token`
                // returns the payload *inside* a JSON field, so anyone copying
                // it out of a terminal copies {\"endpoint\":...} verbatim.
                // Rescuing it here beats an error that blames the operator for
                // the response format.
                try {
                    JSONObject(input.replace("\\\"", "\""))
                } catch (e: Exception) {
                    return failure("payload is not valid JSON: ${e.message}")
                }
            }

            val endpointRaw = json.optString("endpoint").orEmptyIfNull()
            if (endpointRaw.isEmpty()) return failure("payload has no 'endpoint'")

            val token = json.optString("token").orEmptyIfNull()
            if (token.isEmpty()) return failure("payload has no 'token'")

            val endpoint = ServerEndpoint.parse(endpointRaw)
                .getOrElse { return failure(it.message ?: "invalid endpoint") }

            return Result.success(EnrollmentPayload(endpoint = endpoint, token = token))
        }

        // optString returns the literal "null" for a JSON null, which would
        // otherwise sail through the isEmpty() checks above as a real value.
        private fun String?.orEmptyIfNull(): String =
            if (this == null || this == "null") "" else this

        private fun failure(message: String): Result<EnrollmentPayload> =
            Result.failure(IllegalArgumentException(message))
    }
}

package io.github.kgma74.relaix.enroll

import io.github.kgma74.relaix.config.EndpointStore
import io.github.kgma74.relaix.di.IoDispatcher
import io.github.kgma74.relaix.grpc.ChannelFactory
import io.github.kgma74.relaix.security.DeviceIdentityStore
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import smsgateway.v1.Device
import smsgateway.v1.DeviceGatewayGrpcKt
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trades the single-use enrollment token for a long-lived device identity.
 *
 * Called once in a device's lifetime. Unary rather than the first message of
 * the stream because it authenticates differently — a short-lived token
 * instead of a device token — which keeps the stream's rule uniform: every
 * message on `Connect` carries a `device_token`, no exceptions
 * (architecture.md §7).
 */
@Singleton
class Enroller @Inject constructor(
    private val channelFactory: ChannelFactory,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val endpointStore: EndpointStore,
    private val identityStore: DeviceIdentityStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Enrolls against the endpoint in [payload] and persists the result.
     *
     * The endpoint is saved only after the server accepts: a token that was
     * refused says nothing about whether the host was right, and storing it
     * anyway would leave the agent pointed at somewhere it has no identity.
     */
    suspend fun enroll(payload: EnrollmentPayload, label: String): Result<String> =
        withContext(ioDispatcher) {
            val channel = channelFactory.create(payload.endpoint)
            try {
                val request = Device.EnrollRequest.newBuilder()
                    .setEnrollmentToken(payload.token)
                    .setDeviceInfo(deviceInfoProvider.deviceInfo(label))
                    .build()

                val response = withTimeout(CALL_TIMEOUT_MS) {
                    DeviceGatewayGrpcKt.DeviceGatewayCoroutineStub(channel).enroll(request)
                }

                endpointStore.save(payload.endpoint)
                identityStore.save(
                    deviceId = response.deviceId,
                    deviceToken = response.deviceToken,
                )
                Result.success(response.deviceId)
            } catch (e: StatusException) {
                // The status code carries the real cause — a consumed token is
                // FAILED_PRECONDITION, an unreachable host UNAVAILABLE — and
                // flattening them into one message would make a wrong address
                // indistinguishable from an expired QR.
                Result.failure(
                    IllegalStateException(
                        "enrollment refused (${e.status.code}): ${e.status.description ?: "no detail"}",
                        e,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                channel.shutdownNow()
                // Bounded, so a server that never closes cannot hang the
                // caller's coroutine on cleanup.
                runCatching { channel.awaitTermination(SHUTDOWN_TIMEOUT_S, TimeUnit.SECONDS) }
            }
        }

    private companion object {
        const val CALL_TIMEOUT_MS = 20_000L
        const val SHUTDOWN_TIMEOUT_S = 5L
    }
}

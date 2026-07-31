package io.github.kgma74.relaix.grpc

import io.github.kgma74.relaix.config.ServerEndpoint
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds gRPC channels from a parsed endpoint.
 *
 * OkHttp rather than Netty: it is the transport gRPC ships for Android, and
 * the only one that does not drag a server-sized stack onto a handset.
 *
 * Cleartext is opt-in through the endpoint's scheme, never a fallback. A
 * channel that quietly downgrades when TLS fails would send device tokens in
 * the clear over a carrier network, so `grpc://` has to be an explicit,
 * visible choice made by whoever configured the server's public URL.
 */
@Singleton
class ChannelFactory @Inject constructor() {

    fun create(endpoint: ServerEndpoint): ManagedChannel =
        OkHttpChannelBuilder.forAddress(endpoint.host, endpoint.port)
            .apply { if (endpoint.useTls) useTransportSecurity() else usePlaintext() }
            .build()
}

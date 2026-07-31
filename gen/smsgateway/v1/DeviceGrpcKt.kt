package smsgateway.v1

import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls.bidiStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.bidiStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow
import smsgateway.v1.DeviceGatewayGrpc.getServiceDescriptor

/**
 * Holder for Kotlin coroutine-based client and server APIs for smsgateway.v1.DeviceGateway.
 */
public object DeviceGatewayGrpcKt {
  public const val SERVICE_NAME: String = DeviceGatewayGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val enrollMethod: MethodDescriptor<Device.EnrollRequest, Device.EnrollResponse>
    @JvmStatic
    get() = DeviceGatewayGrpc.getEnrollMethod()

  public val connectMethod: MethodDescriptor<Device.DeviceMessage, Device.ServerMessage>
    @JvmStatic
    get() = DeviceGatewayGrpc.getConnectMethod()

  /**
   * A stub for issuing RPCs to a(n) smsgateway.v1.DeviceGateway service as suspending coroutines.
   */
  @StubFor(DeviceGatewayGrpc::class)
  public class DeviceGatewayCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<DeviceGatewayCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): DeviceGatewayCoroutineStub = DeviceGatewayCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun enroll(request: Device.EnrollRequest, headers: Metadata = Metadata()): Device.EnrollResponse = unaryRpc(
      channel,
      DeviceGatewayGrpc.getEnrollMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * The [Flow] of requests is collected once each time the [Flow] of responses is
     * collected. If collection of the [Flow] of responses completes normally or
     * exceptionally before collection of `requests` completes, the collection of
     * `requests` is cancelled.  If the collection of `requests` completes
     * exceptionally for any other reason, then the collection of the [Flow] of responses
     * completes exceptionally for the same reason and the RPC is cancelled with that reason.
     *
     * @param requests A [Flow] of request messages.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun connect(requests: Flow<Device.DeviceMessage>, headers: Metadata = Metadata()): Flow<Device.ServerMessage> = bidiStreamingRpc(
      channel,
      DeviceGatewayGrpc.getConnectMethod(),
      requests,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the smsgateway.v1.DeviceGateway service based on Kotlin coroutines.
   */
  public abstract class DeviceGatewayCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for smsgateway.v1.DeviceGateway.Enroll.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun enroll(request: Device.EnrollRequest): Device.EnrollResponse = throw StatusException(UNIMPLEMENTED.withDescription("Method smsgateway.v1.DeviceGateway.Enroll is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for smsgateway.v1.DeviceGateway.Connect.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param requests A [Flow] of requests from the client.  This flow can be
     *        collected only once and throws [java.lang.IllegalStateException] on attempts to collect
     *        it more than once.
     */
    public open fun connect(requests: Flow<Device.DeviceMessage>): Flow<Device.ServerMessage> = throw StatusException(UNIMPLEMENTED.withDescription("Method smsgateway.v1.DeviceGateway.Connect is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = DeviceGatewayGrpc.getEnrollMethod(),
      implementation = ::enroll
    ))
      .addMethod(bidiStreamingServerMethodDefinition(
      context = this.context,
      descriptor = DeviceGatewayGrpc.getConnectMethod(),
      implementation = ::connect
    )).build()
  }
}

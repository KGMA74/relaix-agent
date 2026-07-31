package smsgateway.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class DeviceGatewayGrpc {

  private DeviceGatewayGrpc() {}

  public static final java.lang.String SERVICE_NAME = "smsgateway.v1.DeviceGateway";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<smsgateway.v1.Device.EnrollRequest,
      smsgateway.v1.Device.EnrollResponse> getEnrollMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Enroll",
      requestType = smsgateway.v1.Device.EnrollRequest.class,
      responseType = smsgateway.v1.Device.EnrollResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<smsgateway.v1.Device.EnrollRequest,
      smsgateway.v1.Device.EnrollResponse> getEnrollMethod() {
    io.grpc.MethodDescriptor<smsgateway.v1.Device.EnrollRequest, smsgateway.v1.Device.EnrollResponse> getEnrollMethod;
    if ((getEnrollMethod = DeviceGatewayGrpc.getEnrollMethod) == null) {
      synchronized (DeviceGatewayGrpc.class) {
        if ((getEnrollMethod = DeviceGatewayGrpc.getEnrollMethod) == null) {
          DeviceGatewayGrpc.getEnrollMethod = getEnrollMethod =
              io.grpc.MethodDescriptor.<smsgateway.v1.Device.EnrollRequest, smsgateway.v1.Device.EnrollResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Enroll"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  smsgateway.v1.Device.EnrollRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  smsgateway.v1.Device.EnrollResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DeviceGatewayMethodDescriptorSupplier("Enroll"))
              .build();
        }
      }
    }
    return getEnrollMethod;
  }

  private static volatile io.grpc.MethodDescriptor<smsgateway.v1.Device.DeviceMessage,
      smsgateway.v1.Device.ServerMessage> getConnectMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Connect",
      requestType = smsgateway.v1.Device.DeviceMessage.class,
      responseType = smsgateway.v1.Device.ServerMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<smsgateway.v1.Device.DeviceMessage,
      smsgateway.v1.Device.ServerMessage> getConnectMethod() {
    io.grpc.MethodDescriptor<smsgateway.v1.Device.DeviceMessage, smsgateway.v1.Device.ServerMessage> getConnectMethod;
    if ((getConnectMethod = DeviceGatewayGrpc.getConnectMethod) == null) {
      synchronized (DeviceGatewayGrpc.class) {
        if ((getConnectMethod = DeviceGatewayGrpc.getConnectMethod) == null) {
          DeviceGatewayGrpc.getConnectMethod = getConnectMethod =
              io.grpc.MethodDescriptor.<smsgateway.v1.Device.DeviceMessage, smsgateway.v1.Device.ServerMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Connect"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  smsgateway.v1.Device.DeviceMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  smsgateway.v1.Device.ServerMessage.getDefaultInstance()))
              .setSchemaDescriptor(new DeviceGatewayMethodDescriptorSupplier("Connect"))
              .build();
        }
      }
    }
    return getConnectMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DeviceGatewayStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayStub>() {
        @java.lang.Override
        public DeviceGatewayStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceGatewayStub(channel, callOptions);
        }
      };
    return DeviceGatewayStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static DeviceGatewayBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayBlockingV2Stub>() {
        @java.lang.Override
        public DeviceGatewayBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceGatewayBlockingV2Stub(channel, callOptions);
        }
      };
    return DeviceGatewayBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DeviceGatewayBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayBlockingStub>() {
        @java.lang.Override
        public DeviceGatewayBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceGatewayBlockingStub(channel, callOptions);
        }
      };
    return DeviceGatewayBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DeviceGatewayFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceGatewayFutureStub>() {
        @java.lang.Override
        public DeviceGatewayFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceGatewayFutureStub(channel, callOptions);
        }
      };
    return DeviceGatewayFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Called once in a device's lifetime. Trades a short-lived enrollment token
     * (scanned from a QR code) for a long-lived device token. Kept separate from
     * Connect because it authenticates differently, which lets the stream keep a
     * uniform rule: every message on Connect carries a device_token.
     * </pre>
     */
    default void enroll(smsgateway.v1.Device.EnrollRequest request,
        io.grpc.stub.StreamObserver<smsgateway.v1.Device.EnrollResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getEnrollMethod(), responseObserver);
    }

    /**
     * <pre>
     * Held open for the device's entire uptime. The agent dials out — phones sit
     * behind carrier NAT and can never be reached inbound — and the server pushes
     * work down the connection the device already opened.
     * </pre>
     */
    default io.grpc.stub.StreamObserver<smsgateway.v1.Device.DeviceMessage> connect(
        io.grpc.stub.StreamObserver<smsgateway.v1.Device.ServerMessage> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getConnectMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DeviceGateway.
   */
  public static abstract class DeviceGatewayImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DeviceGatewayGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DeviceGateway.
   */
  public static final class DeviceGatewayStub
      extends io.grpc.stub.AbstractAsyncStub<DeviceGatewayStub> {
    private DeviceGatewayStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceGatewayStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceGatewayStub(channel, callOptions);
    }

    /**
     * <pre>
     * Called once in a device's lifetime. Trades a short-lived enrollment token
     * (scanned from a QR code) for a long-lived device token. Kept separate from
     * Connect because it authenticates differently, which lets the stream keep a
     * uniform rule: every message on Connect carries a device_token.
     * </pre>
     */
    public void enroll(smsgateway.v1.Device.EnrollRequest request,
        io.grpc.stub.StreamObserver<smsgateway.v1.Device.EnrollResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getEnrollMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Held open for the device's entire uptime. The agent dials out — phones sit
     * behind carrier NAT and can never be reached inbound — and the server pushes
     * work down the connection the device already opened.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<smsgateway.v1.Device.DeviceMessage> connect(
        io.grpc.stub.StreamObserver<smsgateway.v1.Device.ServerMessage> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getConnectMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DeviceGateway.
   */
  public static final class DeviceGatewayBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<DeviceGatewayBlockingV2Stub> {
    private DeviceGatewayBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceGatewayBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceGatewayBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Called once in a device's lifetime. Trades a short-lived enrollment token
     * (scanned from a QR code) for a long-lived device token. Kept separate from
     * Connect because it authenticates differently, which lets the stream keep a
     * uniform rule: every message on Connect carries a device_token.
     * </pre>
     */
    public smsgateway.v1.Device.EnrollResponse enroll(smsgateway.v1.Device.EnrollRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getEnrollMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Held open for the device's entire uptime. The agent dials out — phones sit
     * behind carrier NAT and can never be reached inbound — and the server pushes
     * work down the connection the device already opened.
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<smsgateway.v1.Device.DeviceMessage, smsgateway.v1.Device.ServerMessage>
        connect() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getConnectMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service DeviceGateway.
   */
  public static final class DeviceGatewayBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DeviceGatewayBlockingStub> {
    private DeviceGatewayBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceGatewayBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceGatewayBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Called once in a device's lifetime. Trades a short-lived enrollment token
     * (scanned from a QR code) for a long-lived device token. Kept separate from
     * Connect because it authenticates differently, which lets the stream keep a
     * uniform rule: every message on Connect carries a device_token.
     * </pre>
     */
    public smsgateway.v1.Device.EnrollResponse enroll(smsgateway.v1.Device.EnrollRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getEnrollMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DeviceGateway.
   */
  public static final class DeviceGatewayFutureStub
      extends io.grpc.stub.AbstractFutureStub<DeviceGatewayFutureStub> {
    private DeviceGatewayFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceGatewayFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceGatewayFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Called once in a device's lifetime. Trades a short-lived enrollment token
     * (scanned from a QR code) for a long-lived device token. Kept separate from
     * Connect because it authenticates differently, which lets the stream keep a
     * uniform rule: every message on Connect carries a device_token.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<smsgateway.v1.Device.EnrollResponse> enroll(
        smsgateway.v1.Device.EnrollRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getEnrollMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_ENROLL = 0;
  private static final int METHODID_CONNECT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ENROLL:
          serviceImpl.enroll((smsgateway.v1.Device.EnrollRequest) request,
              (io.grpc.stub.StreamObserver<smsgateway.v1.Device.EnrollResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CONNECT:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.connect(
              (io.grpc.stub.StreamObserver<smsgateway.v1.Device.ServerMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getEnrollMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              smsgateway.v1.Device.EnrollRequest,
              smsgateway.v1.Device.EnrollResponse>(
                service, METHODID_ENROLL)))
        .addMethod(
          getConnectMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              smsgateway.v1.Device.DeviceMessage,
              smsgateway.v1.Device.ServerMessage>(
                service, METHODID_CONNECT)))
        .build();
  }

  private static abstract class DeviceGatewayBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DeviceGatewayBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return smsgateway.v1.Device.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DeviceGateway");
    }
  }

  private static final class DeviceGatewayFileDescriptorSupplier
      extends DeviceGatewayBaseDescriptorSupplier {
    DeviceGatewayFileDescriptorSupplier() {}
  }

  private static final class DeviceGatewayMethodDescriptorSupplier
      extends DeviceGatewayBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DeviceGatewayMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (DeviceGatewayGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DeviceGatewayFileDescriptorSupplier())
              .addMethod(getEnrollMethod())
              .addMethod(getConnectMethod())
              .build();
        }
      }
    }
    return result;
  }
}

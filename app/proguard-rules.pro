# R8 rules for the Relaix agent.
#
# Most libraries here ship their own consumer rules (Hilt, Room, Compose,
# ML Kit, CameraX, coroutines). What follows covers what they do not.

# --- protobuf-java (full runtime) -------------------------------------------
# The full runtime resolves field accessors by reflection (method names built
# from the .proto field names), so the generated contract must keep its
# members. Only our own package: the runtime's own classes keep what they need.
-keep class smsgateway.v1.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessage { *; }

# --- gRPC ---------------------------------------------------------------------
# Transports and name resolvers are found through ServiceLoader / reflection.
-keep class io.grpc.okhttp.OkHttpChannelProvider { *; }
-keep class io.grpc.internal.DnsNameResolverProvider { *; }
-keep class io.grpc.internal.PickFirstLoadBalancerProvider { *; }
-keep class * extends io.grpc.ManagedChannelProvider { <init>(); }
-keep class * extends io.grpc.NameResolverProvider { <init>(); }
-keep class * extends io.grpc.LoadBalancerProvider { <init>(); }

# --- ML Kit -------------------------------------------------------------------
# ML Kit looks its components up in a registry keyed by class, and its shaded
# protobuf-lite reads message fields by name. Under R8 full mode, class merging
# and renaming break both: BarcodeScanning.getClient() comes back null and the
# app crashes the moment the scanner opens. Its consumer rules don't cover this.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Optional dependencies gRPC and protobuf reference but Android never loads.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn com.google.re2j.**
-dontwarn com.squareup.okhttp.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.grpc.netty.**
-dontwarn io.netty.**

# Readable stack traces from the field: keep line numbers, hide source names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

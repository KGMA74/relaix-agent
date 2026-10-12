package io.github.kgma74.relaix.connect

import android.util.Log
import com.google.protobuf.Timestamp
import io.github.kgma74.relaix.config.EndpointStore
import io.github.kgma74.relaix.di.IoDispatcher
import io.github.kgma74.relaix.enroll.DeviceInfoProvider
import io.github.kgma74.relaix.grpc.ChannelFactory
import io.github.kgma74.relaix.health.HealthProvider
import io.github.kgma74.relaix.jobs.JobProcessor
import io.github.kgma74.relaix.policy.PolicyStore
import io.github.kgma74.relaix.security.DeviceIdentityStore
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import smsgateway.v1.Device
import smsgateway.v1.DeviceGatewayGrpcKt
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `Connect` stream for the whole process.
 *
 * A phone sits behind carrier NAT and is never reachable inbound, so the
 * device dials out and holds one bidirectional stream open for its entire
 * uptime; the server pushes jobs down the stream it already has
 * (architecture.md §2).
 *
 * This class only keeps the stream alive and registered. Handling jobs is
 * deliberately not here yet — that is the next milestone — but incoming
 * messages are already drained, because a stream nobody reads applies
 * backpressure and eventually stalls the sender.
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val channelFactory: ChannelFactory,
    private val endpointStore: EndpointStore,
    private val identityStore: DeviceIdentityStore,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val healthProvider: HealthProvider,
    private val jobProcessor: JobProcessor,
    private val policyStore: PolicyStore,
    @param:IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Conflated: however many nudges land between two heartbeats, one early
    // heartbeat answers them all.
    private val heartbeatNudges = Channel<Unit>(Channel.CONFLATED)

    /**
     * Sends a heartbeat now instead of at the next tick.
     *
     * For a change the server schedules on — a permission just granted, a SIM
     * just inserted. Waiting out the interval (30 s by default) leaves the
     * dashboard saying "not ready" about a phone that already is, which reads
     * as the grant not having worked.
     */
    fun heartbeatNow() {
        heartbeatNudges.trySend(Unit)
    }

    private var runner: Job? = null

    // The registered session's writer and credential, for leave(). Null while
    // not connected: there is then nobody to tell.
    @Volatile private var liveOutgoing: Channel<Device.DeviceMessage>? = null
    @Volatile private var liveToken: String? = null

    // Set by leave(): the stream the server is about to close must not be
    // reconnected with a token that no longer exists.
    @Volatile private var leaving = false

    /**
     * Sends rules edited on the phone. The server stores them and answers with
     * the stored version, which lands in [PolicyStore] like any other change.
     *
     * Needs the stream: rules are the server's, and an edit it has not seen is
     * not in force. Returns false when not connected.
     */
    suspend fun updatePolicy(policy: Device.DevicePolicy): Boolean {
        val out = liveOutgoing ?: return false
        val token = liveToken ?: return false
        return runCatching {
            out.send(
                envelope(token)
                    .setPolicyUpdate(Device.PolicyUpdate.newBuilder().setPolicy(policy))
                    .build(),
            )
        }.isSuccess
    }

    /**
     * Tells the server this phone is leaving the fleet, then stops.
     *
     * Best effort, by design: offline, there is no one to tell, and forgetting
     * the enrollment on the phone must work regardless — the operator can still
     * remove the device from the dashboard. Waits up to [timeoutMillis] for the
     * server to close the stream, which is its acknowledgement.
     *
     * @return whether the server was told.
     */
    suspend fun leave(timeoutMillis: Long = LEAVE_TIMEOUT_MS): Boolean {
        val out = liveOutgoing
        val token = liveToken
        if (out == null || token == null) {
            stop()
            return false
        }
        leaving = true
        val sent = runCatching {
            out.send(
                envelope(token)
                    .setLeave(Device.Leave.newBuilder().setReason("forgotten on the device"))
                    .build(),
            )
        }.isSuccess
        if (sent) withTimeoutOrNull(timeoutMillis) { runner?.join() }
        stop()
        leaving = false
        return sent
    }

    /** Starts the connect/retry loop. Safe to call twice; the second is a no-op. */
    fun start(scope: CoroutineScope) {
        if (runner?.isActive == true) return
        runner = scope.launch(ioDispatcher) { runLoop() }
    }

    fun stop() {
        runner?.cancel()
        runner = null
        _state.value = ConnectionState.Idle
    }

    private suspend fun runLoop() {
        val backoff = Backoff()

        while (currentCoroutineIsActive()) {
            val endpoint = endpointStore.endpoint.first()
            val token = identityStore.deviceToken()
            if (endpoint == null || token == null) {
                // Not enrolled yet. Nothing to retry against, so wait to be
                // told rather than spinning against a null endpoint.
                _state.value = ConnectionState.Idle
                return
            }

            _state.value = ConnectionState.Connecting
            val channel = channelFactory.create(endpoint)

            try {
                session(channel, token, backoff)
                if (leaving) {
                    _state.value = ConnectionState.Idle
                    return
                }
            } catch (e: StatusException) {
                if (e.status.code == Status.Code.PERMISSION_DENIED ||
                    e.status.code == Status.Code.UNAUTHENTICATED
                ) {
                    // The credential is not going to become valid by waiting.
                    _state.value = ConnectionState.Refused(
                        e.status.description ?: e.status.code.name,
                    )
                    return
                }
                scheduleRetry(backoff, e.status.code.name)
            } catch (e: CancellationException) {
                // Being stopped is not a failure to retry. Without rethrowing,
                // a normal shutdown logs a lost stream and schedules a
                // reconnect, which then unwinds anyway — noise that hides real
                // failures in the log.
                throw e
            } catch (e: Exception) {
                scheduleRetry(backoff, e.message ?: e::class.java.simpleName)
            } finally {
                channel.shutdownNow()
                runCatching { channel.awaitTermination(SHUTDOWN_TIMEOUT_S, TimeUnit.SECONDS) }
            }
        }
    }

    /**
     * One connection: register, then heartbeat until the stream ends.
     *
     * Outgoing messages go through a channel rather than being written
     * directly so the heartbeat loop and any future job acknowledgement share
     * a single writer — gRPC streams are not safe for concurrent sends.
     */
    private suspend fun session(channel: ManagedChannel, token: String, backoff: Backoff) {
        val outgoing = Channel<Device.DeviceMessage>(Channel.BUFFERED)
        val stub = DeviceGatewayGrpcKt.DeviceGatewayCoroutineStub(channel)

        outgoing.send(registerMessage(token))

        var heartbeatJob: Job? = null
        var lateResultsJob: Job? = null
        try {
            withContext(ioDispatcher) {
                stub.connect(outgoing.consumeAsFlow()).collect { message ->
                    when {
                        message.hasRegisterAck() -> {
                            val ack = message.registerAck
                            if (!ack.accepted) {
                                throw StatusException(
                                    Status.PERMISSION_DENIED.withDescription(
                                        ack.reason.ifBlank { "registration refused" },
                                    ),
                                )
                            }
                            backoff.reset()
                            if (ack.hasPolicy()) policyStore.save(ack.policy)
                            // Jobs the server has given up on: drop them
                            // rather than send a message it may have already
                            // re-dispatched to another handset.
                            jobProcessor.dropStale(ack.staleJobIdsList)

                            val interval = ack.heartbeatIntervalSeconds
                                .takeIf { it > 0 } ?: DEFAULT_HEARTBEAT_S
                            _state.value = ConnectionState.Connected(interval)
                            liveOutgoing = outgoing
                            liveToken = token

                            heartbeatJob?.cancel()
                            heartbeatJob = launch {
                                heartbeatLoop(outgoing, token, interval)
                            }

                            // Delivery reports land minutes later, on their own
                            // schedule. Draining the channel only once
                            // registered means anything that arrived while
                            // disconnected goes out now rather than being lost.
                            lateResultsJob?.cancel()
                            lateResultsJob = launch {
                                for (result in jobProcessor.lateResults) {
                                    outgoing.send(envelope(token).setJobResult(result).build())
                                }
                            }
                        }

                        message.hasSendSmsJob() -> {
                            // Each job gets its own coroutine: sending blocks
                            // on the radio's receipt, and a slow one must not
                            // stop the stream from reading the next message or
                            // the heartbeat from going out.
                            launch { handleJob(message.sendSmsJob, outgoing, token) }
                        }

                        // New rules: from the dashboard, or the echo of an
                        // edit made on this phone.
                        message.hasPolicy() -> policyStore.save(message.policy)

                        message.hasCancelJob() -> {
                            launch {
                                jobProcessor.cancel(message.cancelJob.jobId)?.let { result ->
                                    outgoing.send(
                                        envelope(token).setJobResult(result).build(),
                                    )
                                }
                            }
                        }

                        // Ping is answered by the next heartbeat rather than a
                        // dedicated reply: the contract says so, and it keeps
                        // one message type doing liveness.
                        message.hasPing() -> Log.d(TAG, "ping")

                        else -> Log.d(TAG, "unhandled server message: ${message.payloadCase}")
                    }
                }
            }
        } finally {
            liveOutgoing = null
            liveToken = null
            heartbeatJob?.cancel()
            lateResultsJob?.cancel()
            outgoing.close()
        }
    }

    /**
     * Acknowledge first, then send. The ack is what lets the scheduler
     * reassign a refused job immediately instead of waiting for a timeout.
     */
    private suspend fun handleJob(
        job: Device.SendSmsJob,
        outgoing: Channel<Device.DeviceMessage>,
        token: String,
    ) {
        val decision = jobProcessor.accept(job)
        outgoing.send(envelope(token).setJobAck(decision.ack).build())
        if (!decision.shouldSend) return

        val result = jobProcessor.send(job)
        outgoing.send(envelope(token).setJobResult(result).build())
    }

    private suspend fun heartbeatLoop(
        outgoing: Channel<Device.DeviceMessage>,
        token: String,
        intervalSeconds: Int,
    ) {
        while (true) {
            // The interval, cut short by a nudge.
            withTimeoutOrNull(intervalSeconds * 1_000L) { heartbeatNudges.receive() }
            val message = envelope(token)
                .setHeartbeat(
                    Device.Heartbeat.newBuilder()
                        .setHealth(healthProvider.snapshot(jobProcessor.sentLastHour(), jobProcessor.sentLastHourBySubscription()))
                        .build(),
                )
                .build()
            outgoing.send(message)
        }
    }

    private suspend fun registerMessage(token: String): Device.DeviceMessage =
        envelope(token)
            .setRegister(
                Device.Register.newBuilder()
                    .setDeviceInfo(deviceInfoProvider.deviceInfo(""))
                    .setHealth(healthProvider.snapshot(jobProcessor.sentLastHour(), jobProcessor.sentLastHourBySubscription()))
                    // Jobs this handset still holds. Reported so the server
                    // reconciles instead of blindly re-dispatching work that
                    // is already in flight here.
                    .addAllPendingJobIds(jobProcessor.inFlightJobIds())
                    .build(),
            )
            .build()

    private fun envelope(token: String): Device.DeviceMessage.Builder =
        Device.DeviceMessage.newBuilder()
            .setMessageId(UUID.randomUUID().toString())
            .setDeviceToken(token)
            .setSentAt(now())

    private fun now(): Timestamp {
        val millis = System.currentTimeMillis()
        return Timestamp.newBuilder()
            .setSeconds(millis / 1_000)
            .setNanos(((millis % 1_000) * 1_000_000).toInt())
            .build()
    }

    private suspend fun scheduleRetry(backoff: Backoff, reason: String) {
        val delayMillis = backoff.nextDelayMillis()
        _state.value = ConnectionState.Reconnecting(reason, delayMillis / 1_000)
        Log.w(TAG, "stream lost ($reason); retrying in ${delayMillis}ms")
        delay(delayMillis)
    }

    private suspend fun currentCoroutineIsActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive ?: true

    private companion object {
        const val TAG = "RelaixConnect"
        const val DEFAULT_HEARTBEAT_S = 30
        const val LEAVE_TIMEOUT_MS = 3_000L
        const val SHUTDOWN_TIMEOUT_S = 5L
    }
}

package io.github.kgma74.relaix.connect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.kgma74.relaix.MainActivity
import io.github.kgma74.relaix.R
import io.github.kgma74.relaix.jobs.JobProcessor
import io.github.kgma74.relaix.sms.DeliveryReportReceiver
import io.github.kgma74.relaix.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the `Connect` stream open for as long as the phone is a node.
 *
 * A foreground service and not WorkManager: the job here is not a piece of
 * work that finishes, it is a socket that must stay open, and the server
 * pushes down it at moments the agent cannot predict.
 *
 * The type is `specialUse`, deliberately not `dataSync`. Since Android 15,
 * `dataSync` is capped at six hours per twenty-four, after which the system
 * stops the service — fatal for a service whose entire purpose is to remain
 * connected. Play Store review of `specialUse` does not apply: this app is
 * self-hosted and sideloaded.
 */
@AndroidEntryPoint
class AgentService : Service() {

    @Inject lateinit var connectionManager: ConnectionManager

    @Inject lateinit var jobProcessor: JobProcessor

    private val scope = CoroutineScope(SupervisorJob())
    private var deliveryReceiver: DeliveryReportReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"))

        // NOT_EXPORTED: a delivery report is ours, and an exported receiver
        // would let any app claim a job was delivered.
        DeliveryReportReceiver(jobProcessor, scope).also { receiver ->
            deliveryReceiver = receiver
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(SmsSender.ACTION_DELIVERED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        scope.launch {
            connectionManager.state.collectLatest { state ->
                notificationManager().notify(NOTIFICATION_ID, buildNotification(describe(state)))
            }
        }
        connectionManager.start(scope)
    }

    /**
     * START_STICKY: if the system kills the process under memory pressure,
     * the service is what should come back — an agent that silently stops
     * being reachable is the failure this whole design exists to avoid.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        deliveryReceiver?.let { runCatching { unregisterReceiver(it) } }
        deliveryReceiver = null
        connectionManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun describe(state: ConnectionState): String = when (state) {
        ConnectionState.Idle -> "Not enrolled"
        ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Connected -> "Connected · heartbeat ${state.heartbeatSeconds}s"
        is ConnectionState.Reconnecting ->
            "Reconnecting in ${state.retryInSeconds}s (${state.reason})"
        is ConnectionState.Refused -> "Refused: ${state.reason}"
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Relaix agent")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        // LOW: the notification exists because the platform requires one for a
        // foreground service, not to interrupt anyone. It must not buzz on
        // every reconnect.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows whether the agent is connected to the control plane."
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "relaix_agent_connection"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentService::class.java))
        }
    }
}

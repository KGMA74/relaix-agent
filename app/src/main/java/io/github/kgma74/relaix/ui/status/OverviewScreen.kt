package io.github.kgma74.relaix.ui.status

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.StatTile
import io.github.kgma74.relaix.ui.components.StatusDot
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import smsgateway.v1.Device

/**
 * The one-glance answer to "is this phone working".
 *
 * Connection first and largest, then the health values the scheduler actually
 * reads. Anything that would stop work arriving is promoted to a warning
 * rather than left as a row the eye skips.
 */
@Composable
fun OverviewScreen(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status = LocalStatusColors.current

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> smsGranted = granted }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { ConnectionHero(connection) }

        if (!smsGranted) {
            item {
                WarningCard(
                    title = "SMS permission missing",
                    detail = "The server keeps this device out of the ready set until " +
                        "SEND_SMS is granted, so it stays connected and never receives a job.",
                    actionLabel = "Grant permission",
                    onAction = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                )
            }
        }

        val h = health
        if (h != null) {
            if (!h.simReady) {
                item {
                    WarningCard(
                        title = "No usable SIM",
                        detail = "A phone can be perfectly healthy and still have no SIM " +
                            "to send from. The server will not schedule work here.",
                    )
                }
            }
            if (h.signalStrength <= 0) {
                item {
                    WarningCard(
                        title = "No signal",
                        detail = "Signal level 0 excludes this device from the ready set.",
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "battery",
                        value = "${h.batteryLevel}%",
                        icon = if (h.isCharging) {
                            Icons.Default.BatteryChargingFull
                        } else {
                            Icons.Default.BatteryStd
                        },
                        accent = batteryColour(h, status.ok, status.waiting, status.bad),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "signal",
                        value = "${h.signalStrength}/4",
                        icon = Icons.Default.NetworkCell,
                        accent = if (h.signalStrength <= 0) status.bad else status.ok,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "network",
                        value = h.networkType.ifBlank { "unknown" },
                        icon = Icons.Default.NetworkCell,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "sim",
                        value = if (h.simReady) "ready" else "absent",
                        icon = Icons.Default.SimCard,
                        accent = if (h.simReady) status.ok else status.bad,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "sent last hour",
                        value = "${h.sentLastHour} parts",
                        icon = Icons.Default.Sms,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "send_sms",
                        value = if (h.permissionsOk) "granted" else "missing",
                        icon = Icons.Default.Sms,
                        accent = if (h.permissionsOk) status.ok else status.bad,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionHero(state: ConnectionState) {
    val status = LocalStatusColors.current
    val (dot, headline, detail) = when (state) {
        ConnectionState.Idle ->
            Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Idle", "Not enrolled, or stopped")
        ConnectionState.Connecting ->
            Triple(status.waiting, "Connecting", "Opening the control-plane stream")
        is ConnectionState.Connected ->
            Triple(status.ok, "Connected", "Heartbeat every ${state.heartbeatSeconds}s")
        is ConnectionState.Reconnecting ->
            Triple(status.waiting, "Reconnecting", "Retry in ${state.retryInSeconds}s · ${state.reason}")
        is ConnectionState.Refused ->
            Triple(status.bad, "Refused", state.reason)
    }

    SectionCard {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusDot(
                color = dot,
                // Only the transient states breathe; a steady light means a
                // settled state, good or bad.
                pulsing = state is ConnectionState.Connecting ||
                    state is ConnectionState.Reconnecting,
                size = 14,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.headlineSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val status = LocalStatusColors.current
    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = status.bad,
                    modifier = Modifier.size(18.dp),
                )
                Text(title, style = MaterialTheme.typography.titleSmall, color = status.bad)
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Charging changes the meaning of a low battery entirely, which is why the
 * server is told both and the colour follows the pair, not the number.
 */
private fun batteryColour(
    health: Device.DeviceHealth,
    ok: Color,
    waiting: Color,
    bad: Color,
): Color = when {
    health.isCharging -> ok
    health.batteryLevel < 20 -> bad
    health.batteryLevel < 40 -> waiting
    else -> ok
}

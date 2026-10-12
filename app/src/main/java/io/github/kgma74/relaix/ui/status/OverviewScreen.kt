package io.github.kgma74.relaix.ui.status

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.config.ServerEndpoint
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.ui.components.ListCard
import io.github.kgma74.relaix.ui.components.PageHeader
import io.github.kgma74.relaix.ui.components.RowDivider
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.SectionLabel
import io.github.kgma74.relaix.ui.components.SignalBars
import io.github.kgma74.relaix.ui.components.StatTile
import io.github.kgma74.relaix.ui.components.StatusChip
import io.github.kgma74.relaix.ui.components.StatusDot
import io.github.kgma74.relaix.ui.theme.GeistMono
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft
import smsgateway.v1.Device

/**
 * The one-glance answer to "is this phone working".
 *
 * Connection first and largest. Then the checklist the scheduler applies
 * before handing this phone a job — each line either ticked or saying what to
 * do — and only then the raw measurements.
 */
@Composable
fun OverviewScreen(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val sims by viewModel.sims.collectAsStateWithLifecycle()
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status = LocalStatusColors.current

    var smsGranted by remember { mutableStateOf(granted(context, Manifest.permission.SEND_SMS)) }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { smsGranted = it }

    // Signal, network type and the per-SIM inventory are all behind
    // READ_PHONE_STATE. Without it they read as 0 and blank — not because the
    // phone has no signal, but because it is not allowed to say.
    var phoneGranted by remember { mutableStateOf(granted(context, Manifest.permission.READ_PHONE_STATE)) }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { phoneGranted = it }

    // Re-read on every health tick: a permission granted (or revoked) from
    // system settings never comes back through the launchers above.
    LaunchedEffect(health) {
        smsGranted = granted(context, Manifest.permission.SEND_SMS)
        phoneGranted = granted(context, Manifest.permission.READ_PHONE_STATE)
    }

    val h = health
    val connected = connection is ConnectionState.Connected
    val checks = buildList {
        add(
            Check(
                title = "Connexion au serveur",
                ok = connected,
                detail = if (connected) "Flux ouvert avec la passerelle" else "Les envois arrivent par ce flux",
            ),
        )
        add(
            Check(
                title = "Permission SMS",
                ok = smsGranted,
                detail = if (smsGranted) "Accordée" else "Sans elle, le serveur n'envoie aucun job ici",
                actionLabel = "Autoriser",
                onAction = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
            ),
        )
        add(
            Check(
                title = "État du téléphone",
                ok = phoneGranted,
                detail = if (phoneGranted) "Signal et SIM lisibles" else "Nécessaire pour lire le signal et les SIM",
                actionLabel = "Autoriser",
                onAction = { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
            ),
        )
        if (h != null) {
            add(Check("Carte SIM", h.simReady, if (h.simReady) "Prête" else "Aucune SIM utilisable"))
            // Without the permission, 0 means "unreadable", already a line above.
            if (phoneGranted) {
                add(
                    Check(
                        title = "Signal réseau",
                        ok = h.signalStrength > 0,
                        detail = if (h.signalStrength > 0) {
                            "${h.signalStrength}/4 · ${h.networkType.ifBlank { "réseau inconnu" }}"
                        } else {
                            "Aucun signal : le serveur écarte ce téléphone"
                        },
                    ),
                )
            }
        }
    }
    val ready = checks.all { it.ok }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { PageHeader("État", "Ce que la passerelle voit de ce téléphone, à l'instant.") }
        item { ConnectionHero(connection, endpoint, ready) }
        item { Checklist(checks) }

        if (h != null) {
            item { SectionLabel("Mesures") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = if (h.isCharging) "Batterie · en charge" else "Batterie",
                        value = "${h.batteryLevel} %",
                        icon = if (h.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        accent = batteryColour(h, status.ok, status.waiting, status.bad),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Signal · ${h.networkType.ifBlank { "—" }}",
                        value = if (phoneGranted) "${h.signalStrength}/4" else "—",
                        icon = Icons.Default.NetworkCell,
                        accent = when {
                            !phoneGranted -> MaterialTheme.colorScheme.onSurfaceVariant
                            h.signalStrength <= 0 -> status.bad
                            h.signalStrength == 1 -> status.waiting
                            else -> status.ok
                        },
                        modifier = Modifier.weight(1f),
                        supporting = if (phoneGranted) {
                            { SignalBars(h.signalStrength) }
                        } else {
                            null
                        },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Parts envoyées",
                        value = "${h.sentLastHour}",
                        icon = Icons.Default.Sms,
                        hint = "sur la dernière heure",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = if (sims.size > 1) "Cartes SIM" else "Carte SIM",
                        value = when {
                            sims.isNotEmpty() -> "${sims.size}"
                            h.simReady -> "Prête"
                            else -> "Absente"
                        },
                        icon = Icons.Default.SimCard,
                        accent = if (h.simReady) status.ok else status.bad,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (sims.isNotEmpty()) {
                item { SimList(sims, h.simsList) }
            }
        }
    }
}

private data class Check(
    val title: String,
    val ok: Boolean,
    val detail: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
private fun ConnectionHero(state: ConnectionState, endpoint: ServerEndpoint?, ready: Boolean) {
    val status = LocalStatusColors.current
    val (colour, headline, detail) = when (state) {
        ConnectionState.Idle ->
            Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Arrêté", "Pas d'enrôlement actif")
        ConnectionState.Connecting ->
            Triple(status.waiting, "Connexion…", "Ouverture du flux avec la passerelle")
        is ConnectionState.Connected ->
            Triple(status.ok, "Connecté", "Battement toutes les ${state.heartbeatSeconds} s")
        is ConnectionState.Reconnecting ->
            Triple(status.waiting, "Reconnexion", "Nouvel essai dans ${state.retryInSeconds} s · ${state.reason}")
        is ConnectionState.Disabled ->
            Triple(
                status.waiting,
                "Désactivé",
                "Mis en pause depuis le dashboard. Le téléphone revient seul dès sa réactivation (vérifié toutes les ${state.retryInSeconds} s).",
            )
        is ConnectionState.Refused ->
            Triple(status.bad, "Refusé", state.reason)
    }

    SectionCard(color = soft(colour, 0.10f), borderColor = soft(colour, 0.35f)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(soft(colour, 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusDot(
                        color = colour,
                        // Only the transient states breathe; a steady light means a
                        // settled state, good or bad.
                        pulsing = state is ConnectionState.Connecting ||
                            state is ConnectionState.Reconnecting,
                        size = 14,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(headline, style = MaterialTheme.typography.headlineSmall, color = colour)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(
                    text = if (ready) "Prêt à envoyer" else "Pas prêt",
                    color = if (ready) status.ok else status.waiting,
                )
                if (endpoint != null) {
                    Text(
                        "${endpoint.host}:${endpoint.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GeistMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Checklist(checks: List<Check>) {
    val status = LocalStatusColors.current
    val done = checks.count { it.ok }
    ListCard(
        title = "Prêt à envoyer",
        trailing = {
            StatusChip(
                "$done / ${checks.size}",
                if (done == checks.size) status.ok else status.waiting,
            )
        },
    ) {
        Column {
            checks.forEachIndexed { index, check ->
                if (index > 0) RowDivider(Modifier.padding(start = 56.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val tint = if (check.ok) status.ok else status.bad
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(soft(tint, 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (check.ok) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (check.ok) "OK" else "À corriger",
                            tint = tint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(check.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            check.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!check.ok && check.actionLabel != null && check.onAction != null) {
                        FilledTonalButton(onClick = check.onAction) { Text(check.actionLabel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimList(sims: List<Device.SimInfo>, health: List<Device.SimHealth>) {
    val status = LocalStatusColors.current
    ListCard(title = if (sims.size > 1) "Cartes SIM" else "Carte SIM") {
        Column {
            sims.sortedBy { it.slotIndex }.forEachIndexed { index, sim ->
                val h = health.firstOrNull { it.subscriptionId == sim.subscriptionId }
                if (index > 0) RowDivider(Modifier.padding(start = 16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("SIM ${sim.slotIndex + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            sim.carrier.ifBlank { sim.displayName.ifBlank { "Opérateur inconnu" } },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val sub = listOfNotNull(
                            sim.phoneNumber.takeIf { it.isNotBlank() },
                            "par défaut".takeIf { sim.isDefaultSms },
                            h?.let { "${it.sentLastHour} / h" },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (h != null) {
                        SignalBars(
                            h.signalStrength,
                            color = if (h.simReady && h.signalStrength > 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                status.bad
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun granted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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

package io.github.kgma74.relaix.ui.status

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.ui.components.ListCard
import io.github.kgma74.relaix.ui.components.PageHeader
import io.github.kgma74.relaix.ui.components.RowDivider
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.SectionLabel
import io.github.kgma74.relaix.ui.theme.PlexMono
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft

/**
 * Identity and the one destructive action, kept away from the screens an
 * operator looks at every day.
 */
@Composable
fun DeviceScreen(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var confirming by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { PageHeader("Appareil", "L'identité de ce téléphone dans la flotte.") }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(soft(MaterialTheme.colorScheme.primary), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column {
                        Text(
                            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Android ${Build.VERSION.RELEASE ?: "—"} · API ${Build.VERSION.SDK_INT}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            ListCard(title = "Identité") {
                Column {
                    // The device id is what an operator passes as deviceId on
                    // POST /send, so copying it is the action, not reading it.
                    DetailRow(
                        label = "Identifiant de l'appareil",
                        value = deviceId ?: "—",
                        monospace = true,
                        onCopy = deviceId?.let {
                            {
                                clipboard.setPrimaryClip(ClipData.newPlainText("device id", it))
                                // Android 13+ shows its own clipboard confirmation.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Identifiant copié", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                    RowDivider(Modifier.padding(start = 16.dp))
                    DetailRow(
                        label = "Passerelle",
                        value = endpoint?.let { "${it.host}:${it.port}" + if (it.useTls) " · TLS" else "" } ?: "—",
                        monospace = true,
                    )
                }
            }
        }

        item { SectionLabel("Zone sensible") }
        item {
            SectionCard(borderColor = soft(status.bad, 0.4f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Oublier cet enrôlement", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Supprime le jeton de ce téléphone et arrête le service. Le serveur " +
                            "garde sa fiche : pour rejoindre la flotte, il faudra un nouveau QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { confirming = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = status.bad),
                        border = androidx.compose.foundation.BorderStroke(1.dp, soft(status.bad, 0.5f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Oublier", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    // Confirmed rather than immediate: the token is returned exactly once by
    // the server, so this is not undoable by any action inside the app.
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Oublier cet enrôlement ?") },
            text = {
                Text(
                    "Le jeton sera supprimé de ce téléphone. Pour le réenrôler, il faudra " +
                        "un nouveau code depuis le dashboard.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        viewModel.forget()
                    },
                ) { Text("Oublier", color = status.bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) PlexMono else FontFamily.Default,
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Box(Modifier.size(12.dp))
        }
    }
}

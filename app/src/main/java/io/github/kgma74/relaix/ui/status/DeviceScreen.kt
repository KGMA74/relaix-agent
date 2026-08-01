package io.github.kgma74.relaix.ui.status

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
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
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.theme.LocalStatusColors

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
    val clipboard = LocalContext.current
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var confirming by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Identity", style = MaterialTheme.typography.titleMedium)
                    // The device id is what an operator passes as deviceId on
                    // POST /send, so copying it is the action, not reading it.
                    DetailRow(
                        label = "device id",
                        value = deviceId ?: "—",
                        monospace = true,
                        onCopy = deviceId?.let {
                            {
                                clipboard.setPrimaryClip(ClipData.newPlainText("device id", it))
                            }
                        },
                    )
                    DetailRow("endpoint", endpoint?.toString() ?: "—", monospace = true)
                }
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Handset", style = MaterialTheme.typography.titleMedium)
                    DetailRow("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    DetailRow("android", Build.VERSION.RELEASE ?: "—")
                }
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Danger zone",
                        style = MaterialTheme.typography.titleMedium,
                        color = status.bad,
                    )
                    Text(
                        "Forgetting the enrollment deletes the device token from this " +
                            "phone. The server keeps its record, so the device would have " +
                            "to be enrolled again with a fresh token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { confirming = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Forget this enrollment") }
                }
            }
        }
    }

    // Confirmed rather than immediate: the token is returned exactly once by
    // the server, so this is not undoable by any action inside the app.
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Forget this enrollment?") },
            text = {
                Text(
                    "The device token will be deleted from this phone. Re-enrolling " +
                        "needs a new token from the server.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        viewModel.forget()
                    },
                ) { Text("Forget", color = status.bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f),
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

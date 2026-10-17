package io.github.kgma74.relaix.ui.enroll

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.ui.components.BrandMark
import io.github.kgma74.relaix.ui.components.FormSheet
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.theme.PlexMono
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft

/**
 * First screen a handset ever shows: joining the fleet.
 *
 * Scanning is the offered path and manual entry the fallback below it. The
 * fallback exists on purpose — it made the whole enrollment path testable
 * against a real server before the camera worked, and it still rescues a
 * screen too dim or a code that will not focus.
 */
@Composable
fun EnrollmentScreen(
    modifier: Modifier = Modifier,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status = LocalStatusColors.current
    var pasting by remember { mutableStateOf(false) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        // A refusal is not a dead end: manual entry stays, so the screen just
        // stops offering the scanner instead of blocking enrollment.
        if (granted) viewModel.startScanning()
    }

    if (state.isScanning) {
        ScanningView(
            onScanned = viewModel::onScanned,
            onCancel = viewModel::stopScanning,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(8.dp))
            Text("Relaix", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Ce téléphone ne fait pas encore partie d'une flotte. Dans le dashboard, " +
                    "ouvrez Appareils → Ajouter un téléphone, puis scannez le QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (cameraGranted) viewModel.startScanning()
                else cameraLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Scanner le QR code")
        }

        OutlinedButton(
            onClick = { pasting = true },
            enabled = !state.isEnrolling,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Coller la configuration")
        }

        if (state.isEnrolling) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        }

        state.error?.let { message ->
            SectionCard(color = soft(status.bad, 0.10f), borderColor = soft(status.bad, 0.4f)) {
                Text(
                    message,
                    modifier = Modifier.padding(14.dp),
                    color = status.bad,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // What was pasted lives in the view model: a failed enrollment reopens
    // on the same text, only the error to read on the screen behind.
    if (pasting) {
        FormSheet(
            title = "Coller la configuration",
            subtitle = "Le bouton « Copier la configuration » sous le QR code, ou le champ payload " +
                "de POST /admin/devices/enroll-token. Les guillemets échappés sont acceptés.",
            onDismiss = { pasting = false },
            action = "Enrôler",
            actionEnabled = state.canSubmit,
            onAction = {
                pasting = false
                viewModel.enroll()
            },
        ) {
            OutlinedTextField(
                value = state.payloadText,
                onValueChange = viewModel::onPayloadChange,
                label = { Text("Configuration") },
                placeholder = { Text("""{"endpoint":"grpc://…","token":"…"}""", fontFamily = PlexMono) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = PlexMono),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Nom (facultatif)") },
                placeholder = { Text("téléphone du bureau") },
                supportingText = { Text("Le nom de ce téléphone dans le dashboard.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScanningView(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Visez le QR code", style = MaterialTheme.typography.headlineSmall)
        Text(
            "L'enrôlement démarre dès que le code est lu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QrScanner(
            onScanned = onScanned,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Annuler")
        }
    }
}

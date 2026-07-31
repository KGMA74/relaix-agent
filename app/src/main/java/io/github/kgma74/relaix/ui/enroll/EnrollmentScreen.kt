package io.github.kgma74.relaix.ui.enroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Enrollment by pasting the QR payload.
 *
 * Manual entry exists before the camera on purpose: it makes the whole
 * enrollment path — parse, dial, `Enroll`, persist — testable against a real
 * server without a scanner in the way, and it stays afterwards as the fallback
 * when a screen is too dim or a code will not focus.
 */
@Composable
fun EnrollmentScreen(
    modifier: Modifier = Modifier,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Relaix agent", style = MaterialTheme.typography.headlineSmall)

        val enrolledId = state.enrolledDeviceId
        if (enrolledId != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Enrolled", style = MaterialTheme.typography.titleMedium)
                    Text("device id", style = MaterialTheme.typography.labelMedium)
                    Text(enrolledId, fontFamily = FontFamily.Monospace)
                    Text(
                        "The device token is stored encrypted. Nothing connects yet — " +
                            "the Connect stream is the next milestone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedButton(onClick = viewModel::reset) { Text("Forget this enrollment") }
            return@Column
        }

        Text(
            "Paste the payload from the enrollment QR code " +
                "(POST /admin/devices/enroll-token).",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.payloadText,
            onValueChange = viewModel::onPayloadChange,
            label = { Text("QR payload") },
            placeholder = { Text("""{"endpoint":"grpc://127.0.0.1:9090","token":"..."}""") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::onLabelChange,
            label = { Text("Label (optional)") },
            placeholder = { Text("desk phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::enroll,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isEnrolling) "Enrolling…" else "Enroll")
        }

        if (state.isEnrolling) {
            CircularProgressIndicator()
        }

        state.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

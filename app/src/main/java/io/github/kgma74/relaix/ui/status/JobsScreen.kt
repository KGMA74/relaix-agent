package io.github.kgma74.relaix.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.jobs.JobRecord
import io.github.kgma74.relaix.jobs.JobState
import io.github.kgma74.relaix.ui.components.EmptyState
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.StatTile
import io.github.kgma74.relaix.ui.components.StatusChip
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything this handset has been asked to send, newest first.
 *
 * The platform error code is on screen and not only in a log: it is the
 * string automation matches on, and it is the first thing anyone asks for
 * when a message did not arrive.
 */
private enum class JobFilter(val label: String) {
    All("Tous"),
    Sent("Envoyés"),
    Failed("Échecs"),
    ;

    fun accepts(state: JobState): Boolean = when (this) {
        All -> true
        Sent -> state == JobState.SENT || state == JobState.DELIVERED
        Failed -> state == JobState.FAILED || state == JobState.CANCELLED
    }
}

@Composable
fun JobsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val jobs by viewModel.recentJobs.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(JobFilter.All) }

    if (jobs.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.Message,
            title = "Aucun envoi",
            detail = "Les messages confiés à ce téléphone apparaîtront ici.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val shown = jobs.filter { filter.accepts(it.status) }
    val failed = jobs.count { it.status == JobState.FAILED }

    Column(modifier = modifier.fillMaxSize()) {
        Summary(total = jobs.size, failed = failed)

        // Failures are what an operator opens this screen for, so the filter
        // sits above the list rather than behind a menu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JobFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }

        if (shown.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Message,
                title = "Rien ici",
                detail = "Aucun envoi ne correspond à ce filtre.",
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(shown, key = { it.jobId }) { job -> JobCard(job) }
        }
    }
}

/**
 * Counts first: "how many, and how many went wrong" is the question, and
 * scrolling a list to answer it is work the screen should have done.
 */
@Composable
private fun Summary(total: Int, failed: Int) {
    val status = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = "Envois récents",
            value = "$total",
            icon = Icons.AutoMirrored.Filled.Message,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Échecs",
            value = "$failed",
            icon = Icons.Default.ErrorOutline,
            accent = if (failed > 0) status.bad else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun JobCard(job: JobRecord) {
    val status = LocalStatusColors.current
    val colour = colourFor(job.status, status.ok, status.waiting, status.bad)

    SectionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    job.recipient,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                )
                StatusChip(labelFor(job.status), colour)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    timestamp(job.completedAtMillis ?: job.receivedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val meta = listOfNotNull(
                    // Parts are what the carrier bills, so they belong next to
                    // the outcome rather than hidden in a detail view.
                    job.partsSent.takeIf { it > 0 }?.let { "$it part" + if (it > 1) "s" else "" },
                    job.subscriptionId.takeIf { it > 0 }?.let { "SIM $it" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (job.errorCode.isNotBlank() || job.errorMessage.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(soft(status.bad, 0.10f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (job.errorCode.isNotBlank()) {
                        Text(
                            job.errorCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = status.bad,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (job.errorMessage.isNotBlank()) {
                        Text(
                            job.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun colourFor(state: JobState, ok: Color, waiting: Color, bad: Color): Color =
    when (state) {
        JobState.SENT, JobState.DELIVERED -> ok
        JobState.FAILED -> bad
        // Cancelled is a decision, not a fault: quiet, not red.
        JobState.CANCELLED -> waiting
        JobState.ACCEPTED, JobState.SENDING -> waiting
    }

private fun labelFor(state: JobState): String = when (state) {
    JobState.ACCEPTED -> "Reçu"
    JobState.SENDING -> "Envoi…"
    JobState.SENT -> "Envoyé"
    JobState.DELIVERED -> "Délivré"
    JobState.FAILED -> "Échoué"
    JobState.CANCELLED -> "Annulé"
}

private fun timestamp(millis: Long): String =
    SimpleDateFormat("d MMM · HH:mm:ss", Locale.getDefault()).format(Date(millis))

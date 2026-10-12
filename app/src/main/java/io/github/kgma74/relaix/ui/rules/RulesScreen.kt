package io.github.kgma74.relaix.ui.rules

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.policy.CALLING_CODES
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.SectionLabel
import io.github.kgma74.relaix.ui.components.StatusChip
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft
import java.util.Locale

private val DAYS = listOf(1 to "L", 2 to "M", 3 to "M", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
private val FRENCH = Locale.FRENCH

private fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(FRENCH).ifBlank { code }

private fun hhmm(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)

/**
 * When this phone takes work and for which numbers — the same rules the
 * dashboard edits, held by the server. Saving needs the connection: an edit
 * the server has not stored is not in force.
 */
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val save by viewModel.save.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val connected = connection is ConnectionState.Connected

    val invalid = patternError(draft.accept.pattern) != null ||
        draft.sims.values.any { patternError(it.accept.pattern) != null }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        text = stored?.let { "Version ${it.version}" } ?: "Pas encore reçues",
                        color = if (stored != null) status.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Appliquées par la passerelle, modifiables ici ou depuis le dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionLabel("Téléphone") }
            item {
                AvailabilityCard(
                    windows = draft.windows,
                    onChange = { w -> viewModel.edit { it.copy(windows = w) } },
                    zone = draft.timeZone.ifBlank { java.time.ZoneId.systemDefault().id },
                    inherits = false,
                )
            }
            item {
                AcceptCard(
                    value = draft.accept,
                    onChange = { a -> viewModel.edit { it.copy(accept = a) } },
                )
            }

            items(viewModel.sims, key = { it.subscriptionId }) { sim ->
                val sd = draft.sims[sim.subscriptionId] ?: SimDraft(sim.subscriptionId)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("SIM ${sim.slotIndex + 1} · ${sim.carrier.ifBlank { sim.displayName.ifBlank { "SIM" } }}")
                    SectionCard {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.SimCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text("Utiliser cette SIM", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Ses règles s'ajoutent à celles du téléphone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = !sd.disabled,
                                onCheckedChange = { on -> viewModel.editSim(sim.subscriptionId) { it.copy(disabled = !on) } },
                            )
                        }
                    }
                    if (!sd.disabled) {
                        AvailabilityCard(
                            windows = sd.windows,
                            onChange = { w -> viewModel.editSim(sim.subscriptionId) { it.copy(windows = w) } },
                            zone = draft.timeZone.ifBlank { java.time.ZoneId.systemDefault().id },
                            inherits = true,
                        )
                        AcceptCard(
                            value = sd.accept,
                            onChange = { a -> viewModel.editSim(sim.subscriptionId) { it.copy(accept = a) } },
                        )
                    }
                }
            }
        }

        // The save bar: always in reach, and it says why it cannot save.
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = save) {
                    is SaveState.Saved -> Text(
                        "Enregistrées (version ${s.version}) et appliquées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = status.ok,
                    )
                    is SaveState.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = status.bad)
                    else -> if (!connected) {
                        Text(
                            "Hors ligne : connectez le téléphone à la passerelle pour enregistrer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = status.waiting,
                        )
                    }
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = connected && !invalid && save !is SaveState.Saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (save is SaveState.Saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    }
                    Text("Enregistrer", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityCard(
    windows: List<WindowDraft>,
    onChange: (List<WindowDraft>) -> Unit,
    zone: String,
    inherits: Boolean,
) {
    val context = LocalContext.current
    val always = windows.isEmpty()

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60 % 24, initial % 60, true).show()
    }

    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(
                Icons.Default.Schedule,
                "Disponibilité",
                if (inherits) "Plages propres à cette SIM" else "Hors plages, aucun envoi ; les messages en file attendent. Fuseau : $zone",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (inherits) "Mêmes horaires que le téléphone" else "Toujours disponible",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = always,
                    onCheckedChange = { on ->
                        onChange(if (on) emptyList() else listOf(WindowDraft(setOf(1, 2, 3, 4, 5), 8 * 60, 20 * 60)))
                    },
                )
            }
            if (!always) {
                windows.forEachIndexed { i, w ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAYS.forEach { (n, label) ->
                            val on = w.days.isEmpty() || n in w.days
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    )
                                    .clickable {
                                        val current = w.days.ifEmpty { (1..7).toSet() }
                                        val next = if (on) current - n else current + n
                                        onChange(windows.toMutableList().also { it[i] = w.copy(days = next) })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeChip(hhmm(w.start)) { pickTime(w.start) { m -> onChange(windows.toMutableList().also { it[i] = w.copy(start = m) }) } }
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TimeChip(hhmm(w.end)) { pickTime(w.end) { m -> onChange(windows.toMutableList().also { it[i] = w.copy(end = m) }) } }
                        Box(Modifier.weight(1f))
                        IconButton(onClick = { onChange(windows.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Default.Delete, "Supprimer la plage", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (w.end <= w.start && !(w.start == 0 && w.end == 0)) {
                        Text(
                            "Passe minuit : se termine le lendemain à ${hhmm(w.end)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { onChange(windows + WindowDraft(emptySet(), 8 * 60, 20 * 60)) }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Ajouter une plage", Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcceptCard(value: AcceptDraft, onChange: (AcceptDraft) -> Unit) {
    val status = LocalStatusColors.current
    var picking by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf("") }
    val error = patternError(value.pattern)

    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Default.Public, "Destinataires acceptés", "Pays cochés et motif du numéro : les deux doivent correspondre.")

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (value.countries.isEmpty()) {
                    Text("Tous les pays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                value.countries.forEach { c ->
                    InputChip(
                        selected = false,
                        onClick = { onChange(value.copy(countries = value.countries - c)) },
                        label = { Text("${countryName(c)} +${CALLING_CODES[c]?.firstOrNull().orEmpty()}") },
                        trailingIcon = { Icon(Icons.Default.Close, "Retirer", Modifier.size(16.dp)) },
                    )
                }
            }
            TextButton(onClick = { picking = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Text("Ajouter un pays", Modifier.padding(start = 6.dp))
            }

            OutlinedTextField(
                value = value.pattern,
                onValueChange = { onChange(value.copy(pattern = it)) },
                label = { Text("Motif du numéro (regex)") },
                placeholder = { Text("^\\+2267") },
                isError = error != null,
                supportingText = { Text(error ?: "Vide : tout numéro.") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = test,
                    onValueChange = { test = it },
                    label = { Text("Tester un numéro") },
                    placeholder = { Text("+22670000000") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (test.isNotBlank()) {
                    when (value.accepts(test.trim())) {
                        true -> StatusChip("Accepté", status.ok)
                        false -> StatusChip("Refusé", status.bad)
                        null -> {}
                    }
                }
            }
        }
    }

    if (picking) {
        CountryPicker(
            exclude = value.countries,
            onPick = { code ->
                onChange(value.copy(countries = (value.countries + code).sorted()))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CountryPicker(exclude: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        CALLING_CODES.keys.map { it to countryName(it) }.sortedBy { it.second }
    }
    val q = query.trim().lowercase(FRENCH)
    val shown = all.filter { (code, name) ->
        code !in exclude && (
            q.isEmpty() || name.lowercase(FRENCH).contains(q) || code.lowercase() == q ||
                CALLING_CODES[code].orEmpty().any { ("+$it").startsWith(if (q.startsWith("+")) q else "+$q") }
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un pays") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Nom, code ou +indicatif") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .background(soft(MaterialTheme.colorScheme.primary), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "+" + CALLING_CODES[code].orEmpty().joinToString(", +"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

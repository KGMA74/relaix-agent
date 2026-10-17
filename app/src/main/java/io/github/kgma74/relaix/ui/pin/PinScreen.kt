package io.github.kgma74.relaix.ui.pin

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.pin.PinApproval
import io.github.kgma74.relaix.ui.components.FormSheet
import io.github.kgma74.relaix.ui.components.ListCard
import io.github.kgma74.relaix.ui.components.PageHeader
import io.github.kgma74.relaix.ui.components.RowDivider
import io.github.kgma74.relaix.ui.components.SectionCard
import io.github.kgma74.relaix.ui.components.SheetSection
import io.github.kgma74.relaix.ui.theme.PlexMono
import io.github.kgma74.relaix.ui.theme.soft
import smsgateway.v1.Device

/**
 * The SIMs' PINs and the codes allowed to use them. Everything here stays on
 * the phone; every change asks for the screen lock first.
 */
@Composable
fun PinScreen(
    modifier: Modifier = Modifier,
    viewModel: PinViewModel = hiltViewModel(),
) {
    val sims by viewModel.sims.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val approvals by viewModel.approvals.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val menus by viewModel.ussdMenus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val secure = keyguard.isDeviceSecure

    LaunchedEffect(Unit) { viewModel.refreshSims() }

    // An action waiting for the screen lock to be confirmed.
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) afterUnlock?.invoke()
        afterUnlock = null
    }
    fun guarded(action: () -> Unit) {
        @Suppress("DEPRECATION") // the replacement, BiometricPrompt, needs a FragmentActivity
        val intent = keyguard.createConfirmDeviceCredentialIntent("Relaix", "Confirmez pour modifier le coffre")
        if (intent == null) return
        afterUnlock = action
        unlock.launch(intent)
    }

    var pinFor by remember { mutableStateOf<Int?>(null) }
    var approving by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            PageHeader(
                "Coffre",
                "Les PIN de vos SIM restent sur ce téléphone : la passerelle n'envoie que {pin}, remplacé ici, et seulement pour les codes approuvés ici.",
            )
        }
        if (!secure) {
            item {
                SectionCard {
                    Text(
                        "Définissez d'abord un verrouillage d'écran (code, schéma ou empreinte) : il protège le coffre.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            ListCard(title = "PIN des SIM") {
                Column {
                    if (sims.isEmpty()) Text("Aucune SIM lisible.", modifier = Modifier.padding(16.dp))
                    sims.forEachIndexed { i, sim ->
                        if (i > 0) RowDivider(Modifier.padding(start = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("SIM ${sim.slotIndex + 1} · ${sim.carrier.ifBlank { sim.displayName }}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (sim.subscriptionId in configured) "PIN enregistré" else "Aucun PIN",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(shape = MaterialTheme.shapes.small, enabled = secure, onClick = { guarded { pinFor = sim.subscriptionId } }) {
                                Text(if (sim.subscriptionId in configured) "Changer" else "Définir")
                            }
                            if (sim.subscriptionId in configured) {
                                TextButton(onClick = { guarded { viewModel.clearPin(sim.subscriptionId) } }) { Text("Effacer") }
                            }
                        }
                    }
                }
            }
        }

        if (pending.isNotEmpty()) {
            item {
                ListCard(title = "À approuver") {
                    Column {
                        pending.forEachIndexed { i, p ->
                            if (i > 0) RowDivider(Modifier.padding(start = 16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.template, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "« ${p.automation} » · ${if (p.subscriptionId == 0) "toute SIM" else "SIM ${p.subscriptionId}"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(shape = MaterialTheme.shapes.small, enabled = secure, onClick = { approving = p.subscriptionId to p.template }) { Text("Approuver") }
                            }
                        }
                    }
                }
            }
        }

        item {
            ListCard(
                title = "Codes autorisés",
                trailing = { TextButton(enabled = secure, onClick = { approving = 0 to "" }) { Text("Ajouter") } },
            ) {
                Column {
                    if (approvals.isEmpty()) {
                        Text(
                            "Aucun : un code avec {pin} est refusé tant qu'il n'est pas approuvé ici.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    approvals.forEachIndexed { i, a ->
                        if (i > 0) RowDivider(Modifier.padding(start = 16.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.template, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                Text(caps(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { guarded { viewModel.revoke(a) } }) { Text("Retirer") }
                        }
                    }
                }
            }
        }

        item {
            ListCard(title = "Réponses dans les menus USSD") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (menus) {
                            "Service actif : pendant un code avec des réponses, Relaix lit l'écran " +
                                "du téléphone et tape chaque réponse — le PIN à la fin du menu, " +
                                "compris — dans la boîte de dialogue USSD du système."
                        } else {
                            "Service inactif : les jobs qui demandent à répondre à un menu " +
                                "(le PIN saisi à la dernière étape, par exemple) sont refusés tant " +
                                "qu'il n'est pas allumé. Android veut que ce soit vous qui " +
                                "l'activiez : Paramètres › Accessibilité › Relaix."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Rien n'est lu ni tapé hors d'une session USSD demandée par la passerelle, " +
                            "et le PIN ne quitte jamais ce téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(shape = MaterialTheme.shapes.small,
                        onClick = { openAccessibilitySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (menus) "Ouvrir les réglages" else "Activer dans les réglages")
                    }
                }
            }
        }
    }

    pinFor?.let { sim ->
        val info = sims.firstOrNull { it.subscriptionId == sim }
        PinSheet(
            simLabel = info?.let { "SIM ${it.slotIndex + 1} · ${it.carrier.ifBlank { it.displayName }}" } ?: "SIM $sim",
            replacing = sim in configured,
            onDismiss = { pinFor = null },
            onSave = { pin ->
                viewModel.setPin(sim, pin)
                pinFor = null
            },
        )
    }
    approving?.let { (sim, template) ->
        ApproveSheet(
            sims = sims,
            initialSim = sim,
            initialTemplate = template,
            onDismiss = { approving = null },
            onApprove = { approval ->
                approving = null
                guarded { viewModel.approve(approval) }
            },
        )
    }
}

private fun caps(a: PinApproval): String = buildList {
    add(if (a.subscriptionId == 0) "toute SIM" else "SIM ${a.subscriptionId}")
    a.maxAmount?.let { add("≤ ${it.toLong()} par opération") }
    add("${a.maxPerDay}/jour")
    a.maxTotalPerDay?.let { add("≤ ${it.toLong()} par jour") }
}.joinToString(" · ")

/**
 * Into Android's accessibility list, where this app's switch lives.
 *
 * Only the user can throw it — no API turns a service on from inside the app
 * that owns it, and rightly so — so what the app owes them is the way there
 * and the name to look for.
 */
private fun openAccessibilitySettings(context: Context) {
    val started = runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    started.onFailure {
        Toast.makeText(context, "Ouvrez Paramètres › Accessibilité", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun PinSheet(simLabel: String, replacing: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val ok = pin.matches(Regex("[0-9]{4,8}"))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    FormSheet(
        title = if (replacing) "Changer le PIN" else "PIN de la SIM",
        subtitle = "$simLabel · gardé chiffré sur ce téléphone, jamais envoyé à la passerelle.",
        onDismiss = onDismiss,
        action = "Enregistrer",
        actionEnabled = ok,
        onAction = { onSave(pin) },
    ) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = { Text("PIN") },
            supportingText = { Text("4 à 8 chiffres · ${pin.length}/8") },
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { shown = !shown }) {
                    Icon(
                        if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (shown) "Masquer le PIN" else "Afficher le PIN",
                    )
                }
            },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = PlexMono, letterSpacing = 6.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** A variable in a template: {to}, {amount}, {pin}. */
private val VARIABLE = Regex("""\{([A-Za-z][A-Za-z0-9]*)\}""")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproveSheet(
    sims: List<Device.SimInfo>,
    initialSim: Int,
    initialTemplate: String,
    onDismiss: () -> Unit,
    onApprove: (PinApproval) -> Unit,
) {
    val fixed = initialTemplate.isNotEmpty()
    var template by remember { mutableStateOf(initialTemplate) }
    var sim by remember { mutableIntStateOf(initialSim) }
    var maxAmount by remember { mutableStateOf("") }
    var perDay by remember { mutableStateOf("5") }
    var totalPerDay by remember { mutableStateOf("") }

    val shaped = template.matches(Regex("""^[*#]([0-9*#+]|\{[A-Za-z][A-Za-z0-9]*\})*#$"""))
    val templateOk = shaped && template.contains("{pin}")
    val variables = VARIABLE.findAll(template).map { it.groupValues[1] }.distinct().toList()
    val hasAmount = "amount" in variables
    val ok = templateOk && (perDay.toIntOrNull() ?: 0) > 0

    FormSheet(
        title = "Autoriser un code",
        subtitle = "Le téléphone ne remplira {pin} que pour ce modèle, sur cette SIM, dans ces plafonds.",
        onDismiss = onDismiss,
        action = "Approuver",
        actionEnabled = ok,
        onAction = {
            onApprove(
                PinApproval(
                    subscriptionId = sim,
                    template = template,
                    // Without {amount} there is no amount to cap: none is kept.
                    maxAmount = if (hasAmount) maxAmount.toDoubleOrNull() else null,
                    maxPerDay = perDay.toInt(),
                    maxTotalPerDay = if (hasAmount) totalPerDay.toDoubleOrNull() else null,
                    approvedAtMillis = System.currentTimeMillis(),
                ),
            )
        },
    ) {
        SheetSection("Modèle") {
            if (fixed) {
                // Asked for by an automation: approved as it is or not at all.
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        template,
                        fontFamily = PlexMono,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            } else {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it.trim() },
                    placeholder = { Text("*144*2*1*{to}*{amount}*{pin}#", fontFamily = PlexMono) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = PlexMono),
                    isError = template.isNotEmpty() && !templateOk,
                    supportingText = {
                        Text(
                            when {
                                template.isEmpty() -> "Le code tout-en-un, ou le code suivi des réponses d'un menu, {pin} à sa place."
                                !shaped -> "Commence par * ou #, finit par #, chiffres et {variables} seulement."
                                !templateOk -> "Il manque {pin} : sans lui, rien à autoriser."
                                else -> "Un champ comme {to} ou {amount} accepte n'importe quels chiffres."
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (variables.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    variables.sortedBy { it != "pin" }.forEach { v ->
                        VariableTag(
                            name = v,
                            meaning = when (v) {
                                "pin" -> "PIN du coffre"
                                "amount" -> "montant plafonné"
                                else -> "libre"
                            },
                            accent = v == "pin",
                        )
                    }
                }
            }
        }

        SheetSection("SIM") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sim == 0, onClick = { sim = 0 }, label = { Text("Toutes") })
                sims.forEach { s ->
                    FilterChip(
                        selected = sim == s.subscriptionId,
                        onClick = { sim = s.subscriptionId },
                        label = { Text("SIM ${s.slotIndex + 1} · ${s.carrier.ifBlank { s.displayName }}") },
                    )
                }
                // An automation's SIM that is not in the phone right now.
                if (sim != 0 && sims.none { it.subscriptionId == sim }) {
                    FilterChip(selected = true, onClick = {}, label = { Text("SIM $sim") })
                }
            }
        }

        SheetSection("Plafonds sur 24 h") {
            NumberField("Opérations max", perDay, Modifier.fillMaxWidth(), suffix = "par jour") { perDay = it }
            if (hasAmount) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Par opération", maxAmount, Modifier.weight(1f), placeholder = "Sans limite") { maxAmount = it }
                    NumberField("Total du jour", totalPerDay, Modifier.weight(1f), placeholder = "Sans limite") { totalPerDay = it }
                }
            } else {
                Text(
                    "Pas de {amount} dans le modèle : seul le nombre d'opérations est plafonné.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VariableTag(name: String, meaning: String, accent: Boolean) {
    val color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .background(soft(color), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("{$name}", fontFamily = PlexMono, style = MaterialTheme.typography.labelMedium, color = color)
        Text(meaning, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(9)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

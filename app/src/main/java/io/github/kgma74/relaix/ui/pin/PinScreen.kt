package io.github.kgma74.relaix.ui.pin

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kgma74.relaix.pin.PinApproval
import io.github.kgma74.relaix.ui.components.ListCard
import io.github.kgma74.relaix.ui.components.PageHeader
import io.github.kgma74.relaix.ui.components.RowDivider
import io.github.kgma74.relaix.ui.components.SectionCard

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
                            OutlinedButton(enabled = secure, onClick = { guarded { pinFor = sim.subscriptionId } }) {
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
                                OutlinedButton(enabled = secure, onClick = { approving = p.subscriptionId to p.template }) { Text("Approuver") }
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
                    OutlinedButton(
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
        PinDialog(onDismiss = { pinFor = null }, onSave = { pin ->
            viewModel.setPin(sim, pin)
            pinFor = null
        })
    }
    approving?.let { (sim, template) ->
        ApproveDialog(
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
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val ok = pin.matches(Regex("[0-9]{4,8}"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN de la SIM") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                label = { Text("4 à 8 chiffres") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(pin) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ApproveDialog(initialSim: Int, initialTemplate: String, onDismiss: () -> Unit, onApprove: (PinApproval) -> Unit) {
    var template by remember { mutableStateOf(initialTemplate) }
    var sim by remember { mutableStateOf(if (initialSim == 0) "" else initialSim.toString()) }
    var maxAmount by remember { mutableStateOf("") }
    var perDay by remember { mutableStateOf("5") }
    var totalPerDay by remember { mutableStateOf("") }
    val templateOk = template.matches(Regex("""^[*#]([0-9*#+]|\{[A-Za-z][A-Za-z0-9]*\})*#$""")) && template.contains("{pin}")
    val ok = templateOk && (perDay.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autoriser un code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it.trim() },
                    label = { Text("Modèle, ex. *144*2*1*{to}*{amount}*{pin}#") },
                    enabled = initialTemplate.isEmpty(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sim, onValueChange = { sim = it.filter(Char::isDigit) },
                    label = { Text("SIM (subscription id) — vide : toute SIM") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = maxAmount, onValueChange = { maxAmount = it.filter(Char::isDigit) },
                    label = { Text("Montant max par opération ({amount})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = perDay, onValueChange = { perDay = it.filter(Char::isDigit) },
                    label = { Text("Opérations max par 24 h") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = totalPerDay, onValueChange = { totalPerDay = it.filter(Char::isDigit) },
                    label = { Text("Total max par 24 h") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    "Sans montant max, toute opération passe tant que le nombre par jour le permet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onApprove(
                    PinApproval(
                        subscriptionId = sim.toIntOrNull() ?: 0,
                        template = template,
                        maxAmount = maxAmount.toDoubleOrNull(),
                        maxPerDay = perDay.toInt(),
                        maxTotalPerDay = totalPerDay.toDoubleOrNull(),
                        approvedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }) { Text("Approuver") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

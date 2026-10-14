package io.github.kgma74.relaix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A form, as a sheet from the bottom of the screen.
 *
 * Not a dialog: a form is typed into, and on a phone the keyboard takes the
 * lower half — a centred dialog ends up squeezed between the two, where a
 * sheet sits on the keyboard with its fields and its button in thumb's reach.
 * Dialogs stay for what is only a yes or no.
 *
 * Opens fully expanded: a half-open form is one whose button is off screen.
 * [onAction] and [onDismiss] run once the sheet is down, so whatever they
 * open next — the screen lock, most often — never lands under a sheet that
 * is still leaving.
 *
 * @param scrollable false for a sheet whose content scrolls by itself (a
 *   LazyColumn), which a scrolling parent would break.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                modifier = if (scrollable) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            action?.let {
                Button(
                    onClick = { close(onAction) },
                    enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(it) }
            }
        }
    }
}

/** A group of fields in a sheet, under a quiet caption. */
@Composable
fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

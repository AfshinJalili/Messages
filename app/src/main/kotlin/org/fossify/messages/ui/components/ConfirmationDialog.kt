package org.fossify.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val DialogShape = RoundedCornerShape(24.dp)
private val DialogMaxWidth = 400.dp

/** The shared dialog frame: a 24dp-cornered surfaceContainer card with a heading, scrolling when short of room. */
@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier
                .widthIn(max = DialogMaxWidth)
                .semantics { paneTitle = title },
            shape = DialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                content()
            }
        }
    }
}

/**
 * Component / confirmation dialog: a title, what will happen, the confirming action and Cancel
 * stacked full width. [destructive] paints the confirmation coral for permanent actions.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    DialogFrame(title, onDismiss) {
        Text(explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        OpenLineButton(
            confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            kind = if (destructive) ButtonKind.DANGER else ButtonKind.SECONDARY,
        )
        OpenLineButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
    }
}

/** The dialog frame with a title, text and one way out: for information, not decisions. */
@Composable
fun MessageDialog(title: String, text: String, dismissLabel: String, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        OpenLineButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
    }
}

/**
 * 39 · Conversation / rename, as a dialog (D03): the name field, who sees it, then Save name.
 * [current] is the custom name, or empty to start blank over [placeholder]. Save needs a name.
 */
@Composable
fun RenameDialog(
    title: String,
    current: String,
    placeholder: String,
    explanation: String,
    confirmLabel: String,
    dismissLabel: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    val save = { name.trim().takeIf { it.isNotEmpty() }?.let(onRename) }
    DialogFrame(title, onDismiss) {
        TextFieldSlab(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.focusRequester(focus),
            placeholder = placeholder,
            accessibilityLabel = title,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            containerColor = MaterialTheme.colorScheme.surface,
        )
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OpenLineButton(
            confirmLabel,
            onClick = { save() },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
        OpenLineButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

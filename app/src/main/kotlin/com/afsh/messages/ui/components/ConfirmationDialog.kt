package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection

/**
 * Component / confirmation dialog: a title, what will happen, then Cancel and the confirming
 * action. [destructive] paints the confirmation in the error role for permanent actions.
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
    AfshDialog(title, onDismiss, dismissLabel, DialogConfirm(confirmLabel, onConfirm, destructive = destructive)) {
        Text(explanation, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A dialog with a title, text and one way out: for information, not decisions. */
@Composable
fun MessageDialog(title: String, text: String, dismissLabel: String, onDismiss: () -> Unit) {
    AfshDialog(title, onDismiss, dismissLabel) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
    AfshDialog(title, onDismiss, dismissLabel, DialogConfirm(confirmLabel, { save() }, enabled = name.isNotBlank())) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = title },
            placeholder = { Text(placeholder) },
            supportingText = { Text(explanation) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            singleLine = true,
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * A one-field dialog that checks its value as it is typed: [error] returns why the value cannot
 * be saved, or null, shown under the field in place of [explanation]. Used for spam patterns,
 * which read left to right in a fixed-width face.
 */
@Composable
fun TextInputDialog(
    title: String,
    current: String,
    explanation: String,
    confirmLabel: String,
    dismissLabel: String,
    error: (String) -> String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    val problem = remember(value) { if (value.isBlank()) null else error(value) }
    val canSave = value.isNotBlank() && problem == null
    val save = { if (canSave) onSave(value) }
    AfshDialog(title, onDismiss, dismissLabel, DialogConfirm(confirmLabel, { save() }, enabled = canSave)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = title },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
            supportingText = { Text(problem ?: explanation) },
            isError = problem != null,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

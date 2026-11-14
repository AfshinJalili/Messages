package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The confirming action of a dialog. [destructive] paints it in the error role, for a permanent
 * action; [enabled] is false while the dialog still waits for a valid value.
 */
@Immutable
data class DialogConfirm(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/**
 * Component / dialog: the one way the app shows a dialog, the stock Material 3 alert dialog with
 * its theme roles (surfaceContainerHigh, the extraLarge shape, headlineSmall title, 24dp padding).
 * [content] sits in a column spaced 16dp and scrolls when short of room unless [scroll] is false
 * (a content that scrolls itself, like a choice list, passes false and weights its own list).
 *
 * The actions are Material text buttons at the end: [dismissLabel] closes the dialog through
 * [onDismiss], and [confirm], when given, beside it. Do not build a dialog from `Dialog`,
 * `AlertDialog`, `DatePickerDialog` or a commons dialog: use this, [AfshDatePickerDialog], or one
 * of the dialogs built on them in this package (`ConfirmationDialog`, `MessageDialog`,
 * `ChoiceDialog`, `RenameDialog`, `TextInputDialog`). From an activity outside a Compose tree, use
 * `showAfshDialog`, `confirm`, `inform` or `choose` in `DialogHost.kt`.
 */
@Composable
fun AfshDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String,
    confirm: DialogConfirm? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        title = { Text(title, Modifier.semantics { heading() }) },
        text = {
            Column(
                if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        },
        confirmButton = { confirm?.let { ConfirmButton(it) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * The stock Material 3 date picker dialog with the same actions as [AfshDialog]. [content] is the
 * `DatePicker`; give it its own title, since this frame has no title slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfshDatePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String,
    confirm: DialogConfirm,
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { ConfirmButton(confirm) },
        modifier = Modifier.semantics { paneTitle = title },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        content = content,
    )
}

@Composable
private fun ConfirmButton(confirm: DialogConfirm) {
    TextButton(
        onClick = confirm.onClick,
        enabled = confirm.enabled,
        colors = if (confirm.destructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) { Text(confirm.label) }
}

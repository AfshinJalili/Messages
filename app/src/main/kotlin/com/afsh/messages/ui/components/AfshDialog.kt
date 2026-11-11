package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val DialogShape = RoundedCornerShape(24.dp)
private val DialogMaxWidth = 400.dp
private val DialogPadding = 24.dp

/** A wide dialog (the calendar) keeps 12dp at its sides so a 360dp picker fits on a 384dp card. */
private val WideDialogPadding = 12.dp
private val WideDialogMaxWidth = 384.dp
private val WideDialogMargin = 12.dp

/**
 * Component / dialog: the one frame every dialog in the app is drawn in. A 24dp-cornered
 * surfaceContainer card, a headlineSmall heading, then [content] in a column spaced 16dp.
 * The content scrolls when short of room unless [scroll] is false (a content that scrolls itself,
 * like a choice list, passes false and weights its own list).
 *
 * Actions go last, stacked full width, through [DialogActions]. Do not build a dialog from
 * `Dialog`, `AlertDialog`, `DatePickerDialog` or a commons dialog: use this frame, or one of the
 * dialogs built on it in this package (`ConfirmationDialog`, `MessageDialog`, `ChoiceDialog`,
 * `RenameDialog`, `TextInputDialog`). From an activity outside a Compose tree, use
 * `showAfshDialog`, `confirm` or `inform` in `DialogHost.kt`.
 */
@Composable
fun AfshDialog(
    title: String,
    onDismiss: () -> Unit,
    scroll: Boolean = true,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val padding: Dp = if (wide) WideDialogPadding else DialogPadding
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !wide),
    ) {
        Surface(
            Modifier
                .then(if (wide) Modifier.padding(horizontal = WideDialogMargin) else Modifier)
                .widthIn(max = if (wide) WideDialogMaxWidth else DialogMaxWidth)
                .semantics { paneTitle = title },
            shape = DialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .then(if (wide) Modifier.padding(horizontal = DialogPadding - WideDialogPadding) else Modifier)
                        .semantics { heading() },
                )
                content()
            }
        }
    }
}

/**
 * A dialog's way out, stacked full width: the confirming action above, the dismissing one below.
 * [confirmKind] is SECONDARY for an ordinary decision, PRIMARY when the dialog exists to collect a
 * value (a name, a date), and DANGER for a permanent action. Without [confirmLabel] only the
 * dismissing button shows.
 */
@Composable
fun DialogActions(
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmKind: ButtonKind = ButtonKind.SECONDARY,
    confirmEnabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (confirmLabel != null) {
            AfshButton(
                confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                kind = confirmKind,
                enabled = confirmEnabled,
            )
        }
        AfshButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
    }
}

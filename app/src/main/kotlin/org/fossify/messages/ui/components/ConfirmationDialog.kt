package org.fossify.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val DialogShape = RoundedCornerShape(24.dp)
private val DialogMaxWidth = 400.dp

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
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OpenLineButton(
                    confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    kind = if (destructive) ButtonKind.DANGER else ButtonKind.SECONDARY,
                )
                OpenLineButton(
                    dismissLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.QUIET,
                )
            }
        }
    }
}

/** The dialog frame with a title, text and one way out: for information, not decisions. */
@Composable
fun MessageDialog(title: String, text: String, dismissLabel: String, onDismiss: () -> Unit) {
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
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                OpenLineButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
            }
        }
    }
}

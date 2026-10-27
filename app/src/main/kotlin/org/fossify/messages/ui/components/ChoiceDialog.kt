package org.fossify.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val DialogShape = RoundedCornerShape(24.dp)
private val DialogMaxWidth = 400.dp

/** One option of a [ChoiceDialog]. */
@Immutable
data class Choice<T>(val value: T, val label: String, val detail: String? = null)

/**
 * A single choice in the confirmation dialog's frame: the title, the options as radio rows
 * (choosing one closes the dialog), and Cancel. [selected] may be null when nothing is chosen yet.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    dismissLabel: String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier
                .widthIn(max = DialogMaxWidth)
                .semantics { paneTitle = title },
            shape = DialogShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    choices.forEach { choice ->
                        RadioSetting(
                            label = choice.label,
                            selected = choice.value == selected,
                            onClick = { onChoose(choice.value) },
                            value = choice.detail,
                        )
                    }
                }
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

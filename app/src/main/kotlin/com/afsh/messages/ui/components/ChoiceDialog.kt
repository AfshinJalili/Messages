package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/** One option of a [ChoiceDialog]. */
@Immutable
data class Choice<T>(val value: T, val label: String, val detail: String? = null)

/**
 * A single choice in the shared [AfshDialog] frame: the title, the options as radio rows
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
    AfshDialog(title, onDismiss, scroll = false) {
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
        DialogActions(dismissLabel = dismissLabel, onDismiss = onDismiss)
    }
}

package org.fossify.messages.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fossify.messages.ui.OpenLine

private val ButtonPadding = PaddingValues(16.dp)
private val MinTarget = 48.dp

/** The button hierarchy of board 00b: one primary per screen, secondary, quiet, and destructive. */
enum class ButtonKind { PRIMARY, SECONDARY, QUIET, DANGER }

@Immutable
private data class ButtonPalette(val container: Color, val content: Color)

@Composable
private fun ButtonKind.palette(): ButtonPalette {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        ButtonKind.PRIMARY -> ButtonPalette(OpenLine.colors.accent, OpenLine.colors.onAccent)
        ButtonKind.SECONDARY -> ButtonPalette(colors.primaryContainer, colors.onPrimaryContainer)
        ButtonKind.QUIET -> ButtonPalette(colors.surfaceContainer, colors.primary)
        ButtonKind.DANGER -> ButtonPalette(colors.error, colors.onError)
    }
}

/** Component / Primary, Secondary and Quiet button: a 16dp slab with a centred label. */
@Composable
fun OpenLineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val palette = kind.palette()
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.container,
            contentColor = palette.content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = null,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** An inline text action with a 48dp target, such as Restore and Delete forever on a card. */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTarget),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

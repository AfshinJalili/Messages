package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh

private val ButtonPadding = PaddingValues(16.dp)
private val MinTarget = 48.dp
private val ButtonIconSize = 24.dp

/** The button hierarchy of board 00b: one primary per screen, secondary, quiet, and destructive. */
enum class ButtonKind { PRIMARY, SECONDARY, QUIET, DANGER }

@Composable
private fun ButtonKind.colors(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        ButtonKind.PRIMARY -> ButtonDefaults.buttonColors(containerColor = Afsh.colors.accent, contentColor = Afsh.colors.onAccent)
        ButtonKind.SECONDARY -> ButtonDefaults.filledTonalButtonColors(
            containerColor = Afsh.colors.secondaryButton.container,
            contentColor = Afsh.colors.secondaryButton.content,
        )
        ButtonKind.QUIET -> ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainer, contentColor = colors.primary)
        ButtonKind.DANGER -> ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
    }.copy(disabledContainerColor = colors.surfaceContainerHigh, disabledContentColor = colors.onSurfaceVariant)
}

/**
 * Component / Primary, Secondary, Quiet and Danger button. Primary and Danger are a Material 3 filled [Button],
 * Secondary and Quiet a [FilledTonalButton]; each reads its colours from its own theme role.
 */
@Composable
fun AfshButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val sized = modifier.defaultMinSize(minHeight = MinTarget)
    val content: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    when (kind) {
        ButtonKind.PRIMARY, ButtonKind.DANGER -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            colors = kind.colors(),
            elevation = null,
            contentPadding = ButtonPadding,
        ) { content() }

        ButtonKind.SECONDARY, ButtonKind.QUIET -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            colors = kind.colors(),
            elevation = null,
            contentPadding = ButtonPadding,
        ) { content() }
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

/**
 * Component / floating button, on a Material 3 [ExtendedFloatingActionButton]: the new-message button (primary)
 * and jump to latest (secondary). [description] is what TalkBack reads, so a collapsed button keeps its name and
 * a count in [text] is read in full.
 */
@Composable
fun FloatingButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    expanded: Boolean = text != null,
    secondary: Boolean = false,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded && text != null,
        modifier = modifier.semantics { contentDescription = description },
        shape = if (secondary) CircleShape else MaterialTheme.shapes.medium,
        containerColor = if (secondary) Afsh.colors.secondaryButton.container else Afsh.colors.accent,
        contentColor = if (secondary) Afsh.colors.secondaryButton.content else Afsh.colors.onAccent,
        icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonIconSize)) },
        text = { Text(text.orEmpty(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {}) },
    )
}

/** Component / send button: a Material 3 [FilledIconButton] in the accent role, named by [description]. */
@Composable
fun SendButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Afsh.colors.accent,
            contentColor = Afsh.colors.onAccent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonIconSize))
    }
}

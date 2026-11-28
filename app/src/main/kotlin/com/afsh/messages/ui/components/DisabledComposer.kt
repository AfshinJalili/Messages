package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.ContentFontVisualTransformation

private val TargetSize = 48.dp
private val SendSlabWidth = 64.dp

/**
 * Component / disabled composer: the composer itself, switched off. The same + button, outlined field and send
 * slab with the same send arrow, each in Material's disabled state, and [reason] where the hint goes. 05 shows
 * it before there is a recipient, 67 when the sender is blocked. The caller handles window insets.
 */
@Composable
fun DisabledComposer(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
    ) {
        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(TargetSize, fieldMinHeight())) {
            Icon(AfshIcon.Add.painter(), contentDescription = null)
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            readOnly = true,
            singleLine = true,
            // Unspecified: the field's own disabled placeholder colour, so the hint reads as switched off.
            placeholder = { DisabledComposerText(reason, color = Color.Unspecified) },
            visualTransformation = ContentFontVisualTransformation,
            shape = MaterialTheme.shapes.medium,
            colors = afshFieldColors(MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = fieldMinHeight()),
        )
        SendButton(
            icon = AfshIcon.SendArrow,
            description = reason,
            onClick = {},
            enabled = false,
            modifier = Modifier.size(SendSlabWidth, fieldMinHeight()),
        )
    }
}

/**
 * The text of every switched-off composer: one line, cut with an ellipsis, so large fonts never
 * make the composer grow. TalkBack still reads the whole text.
 */
@Composable
fun DisabledComposerText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

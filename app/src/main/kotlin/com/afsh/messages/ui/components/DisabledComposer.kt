package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afsh.messages.R

private val IconSize = 24.dp
private val TargetSize = 48.dp
private val SendSlabWidth = 64.dp

/**
 * Component / disabled composer: the composer's shape, switched off, with [reason] where the text
 * would go. 05 shows it before there is a recipient, 67 when the sender is blocked. The caller
 * handles window insets.
 */
@Composable
fun DisabledComposer(reason: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = colors.surfaceContainer, contentColor = colors.onSurfaceVariant) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(TargetSize), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_lucide_plus), contentDescription = null, modifier = Modifier.size(IconSize))
            }
            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = colors.surface) {
                DisabledComposerText(reason, Modifier.padding(12.dp))
            }
            Surface(Modifier.size(SendSlabWidth, TargetSize), shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHigh, contentColor = colors.onSurfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_lucide_send), contentDescription = null, modifier = Modifier.size(IconSize))
                }
            }
        }
    }
}

/**
 * The text of every switched-off composer: one line, cut with an ellipsis, so large fonts never
 * make the composer grow. TalkBack still reads the whole text.
 */
@Composable
fun DisabledComposerText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

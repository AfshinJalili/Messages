package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.messages.R

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
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(TargetSize), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_lucide_plus), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(IconSize))
        }
        Text(
            reason,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surface)
                .padding(12.dp),
        )
        Box(
            Modifier
                .size(SendSlabWidth, TargetSize)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_lucide_send), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(IconSize))
        }
    }
}

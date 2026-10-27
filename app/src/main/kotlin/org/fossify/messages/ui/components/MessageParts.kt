package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val BubbleCorner = 16.dp
private val BubbleTail = 4.dp

/**
 * Component / incoming and outgoing message: 16dp corners with a 4dp tail at the bottom on the
 * sender's side. A bubble followed by one from the same sender drops the tail.
 */
fun messageBubbleShape(incoming: Boolean, groupedBelow: Boolean = false): RoundedCornerShape {
    val tail = if (groupedBelow) BubbleCorner else BubbleTail
    return if (incoming) {
        RoundedCornerShape(BubbleCorner, BubbleCorner, BubbleCorner, tail)
    } else {
        RoundedCornerShape(BubbleCorner, BubbleCorner, tail, BubbleCorner)
    }
}

/** Component / conversation date pill. */
@Composable
fun DatePill(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

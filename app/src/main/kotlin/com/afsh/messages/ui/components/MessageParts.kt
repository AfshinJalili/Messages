package com.afsh.messages.ui.components

import com.afsh.messages.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import com.afsh.messages.ui.AfshCorners
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Component / incoming and outgoing message: corners and tail come from the theme (`AfshCorners.bubble`). */
fun messageBubbleShape(incoming: Boolean, groupedBelow: Boolean = false): Shape = AfshCorners.bubble(incoming, groupedBelow)

/** Component / conversation date pill: a label on a round Material 3 [Surface]. */
@Composable
fun DatePill(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

/** The "New messages" line above the first message that was unread when the page opened: two Material 3 dividers around the label. */
@Composable
fun UnreadDivider() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
        Text(stringResource(R.string.new_messages), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
    }
}

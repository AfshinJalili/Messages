package com.afsh.messages.ui.components

import com.afsh.messages.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshCorners
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Component / incoming and outgoing message: corners and tail come from the theme (`AfshCorners.bubble`). */
fun messageBubbleShape(incoming: Boolean, groupedBelow: Boolean = false): Shape = AfshCorners.bubble(incoming, groupedBelow)

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
            .padding(horizontal = Afsh.spacing.md, vertical = Afsh.spacing.xs),
    )
}

/** The "New messages" line above the first message that was unread when the page opened. */
@Composable
fun UnreadDivider() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outlineVariant)
        )
        Text(stringResource(R.string.new_messages), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outlineVariant)
        )
    }
}

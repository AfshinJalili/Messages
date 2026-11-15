package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.ColorRole

/** Which color role a bubble takes. Selection wins, then a failed send, then who sent it. */
enum class BubbleTone {
    Received,
    Sent,
    Failed,
    Selected,
    ;

    companion object {
        fun of(incoming: Boolean, failed: Boolean = false, selected: Boolean = false): BubbleTone = when {
            selected -> Selected
            failed -> Failed
            incoming -> Received
            else -> Sent
        }
    }
}

/** The container and content colors of [tone], from the theme. */
@Composable
fun BubbleTone.role(): ColorRole {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        BubbleTone.Received -> ColorRole(colors.surfaceContainer, colors.onSurface)
        BubbleTone.Sent -> Afsh.colors.sentBubble
        BubbleTone.Failed -> ColorRole(colors.errorContainer, colors.onErrorContainer)
        BubbleTone.Selected -> ColorRole(colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

/** A bubble's inner padding, the same in the conversation, Spam and message details. */
val BubblePadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * Component / incoming and outgoing message: a Material 3 [Surface] in the [tone]'s color role and
 * [shape] (`AfshCorners.bubble` in a conversation). Content stacks with an 8 dp gap and reads
 * [LocalContentColor][androidx.compose.material3.LocalContentColor] from the role. Gestures and
 * semantics go on [modifier]; clip it to [shape] before any clickable so the ripple keeps the corners.
 */
@Composable
fun MessageBubble(
    tone: BubbleTone,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = BubblePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val role = tone.role()
    Surface(modifier, shape = shape, color = role.container, contentColor = role.content) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

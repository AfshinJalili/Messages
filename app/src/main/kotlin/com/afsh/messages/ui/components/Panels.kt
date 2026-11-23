package com.afsh.messages.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh

private val MarkSize = 46.dp
private val MarkIconSize = 21.dp
private val DestinationHeight = 75.dp
private val ProminentPanelPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = Afsh.spacing.md, vertical = Afsh.spacing.lg)
private val CompactPanelPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md)

/**
 * Library row, on a Material 3 [Card] holding a [ListItem]: a secondaryContainer mark, a label with its
 * description, an optional count badge and a disclosure chevron. The card is the touch target.
 */
@Composable
fun DestinationCard(
    icon: AfshIcon,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DestinationHeight),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer, contentColor = colors.onSurface),
    ) {
        ListItem(
            headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
            leadingContent = {
                Surface(Modifier.size(MarkSize), shape = MaterialTheme.shapes.medium, color = colors.secondaryContainer, contentColor = colors.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon.painter(), contentDescription = null, modifier = Modifier.size(MarkIconSize))
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
                    if (badge > 0) CountBadge(badge)
                    Icon(AfshIcon.ChevronForward.painter(), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Afsh.colors.transparent,
                headlineColor = colors.onSurface,
                supportingColor = colors.onSurfaceVariant,
                trailingIconColor = colors.onSurfaceVariant,
            ),
        )
    }
}

/** A Material 3 [Card] in [containerColor] with [content] stacked inside [padding]: the frame of every panel below. */
@Composable
internal fun PanelCard(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    padding: PaddingValues = PaddingValues(Afsh.spacing.lg),
    spacing: Dp = Afsh.spacing.xs,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/**
 * A secondaryContainer panel that explains a page: the Library principle (compact) or a call to action such as
 * Archive's "Need it in your inbox?" (prominent).
 */
@Composable
fun InfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    contentPadding: PaddingValues = if (prominent) ProminentPanelPadding else CompactPanelPadding,
) {
    val colors = MaterialTheme.colorScheme
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier,
        shape = if (prominent) MaterialTheme.shapes.medium else MaterialTheme.shapes.small,
        padding = contentPadding,
    ) {
        Text(
            title,
            style = if (prominent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            },
        )
        Text(body, style = MaterialTheme.typography.bodySmall, color = Afsh.colors.onSecondaryContainerVariant)
    }
}

/** One sentence on the secondaryContainer panel, when a page needs a line of context and no title. */
@Composable
fun InfoPanel(body: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier,
        shape = MaterialTheme.shapes.small,
        padding = CompactPanelPadding,
    ) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Afsh.colors.onSecondaryContainerVariant)
    }
}

/** Component / empty state, on a Material 3 [Card]. [icon] is omitted when the card is only text. */
@Composable
fun EmptyState(icon: AfshIcon? = null, title: String, modifier: Modifier = Modifier, text: String? = null) {
    val colors = MaterialTheme.colorScheme
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier,
        padding = PaddingValues(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.xl),
        spacing = Afsh.spacing.sm,
    ) {
        if (icon != null) {
            Icon(icon.painter(), contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Afsh.colors.onSecondaryContainerVariant)
        }
    }
}

/** Component / attention notice and validation notice, on a Material 3 [Card]: errorContainer, title and detail. */
@Composable
fun AttentionNotice(title: String, detail: String, modifier: Modifier = Modifier, icon: AfshIcon? = null) {
    val colors = MaterialTheme.colorScheme
    PanelCard(colors.errorContainer, colors.onErrorContainer, modifier, spacing = if (icon != null) Afsh.spacing.sm else Afsh.spacing.xs) {
        if (icon != null) {
            Icon(icon.painter(), contentDescription = null, tint = colors.error, modifier = Modifier.size(26.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Fades whichever end of a horizontal scroll still has content, so a clipped chip reads as
 * more of the row rather than a broken label. The mask is outside the scroll so it stays
 * on the viewport. [Afsh.colors.mask] and [Afsh.colors.transparent] are the blend stops, not painted colors.
 * In RTL, `horizontalScroll` starts at the right (`value == 0`), so the faded edges reverse.
 */
@Composable
fun Modifier.fadingScrollEdges(scrollState: ScrollState, edge: Dp = 20.dp): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val transparent = Afsh.colors.transparent
    val mask = Afsh.colors.mask
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edgePx = edge.toPx().coerceAtMost(size.width / 2f)
            if (edgePx <= 0f) return@drawWithContent
            val fadeLeft = if (layoutDirection == LayoutDirection.Ltr) {
                scrollState.value > 0
            } else {
                scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
            }
            val fadeRight = if (layoutDirection == LayoutDirection.Ltr) {
                scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
            } else {
                scrollState.value > 0
            }
            if (fadeLeft) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to transparent,
                        1f to mask,
                        startX = 0f,
                        endX = edgePx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (fadeRight) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to mask,
                        1f to transparent,
                        startX = size.width - edgePx,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

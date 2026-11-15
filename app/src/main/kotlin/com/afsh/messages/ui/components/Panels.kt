package com.afsh.messages.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh

private val MarkSize = 46.dp
private val MarkIconSize = 21.dp
private val DestinationHeight = 75.dp
private val ProminentPanelPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
private val CompactPanelPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * Library row, on a Material 3 [Card] holding a [ListItem]: a lilac mark, a label with its description, an
 * optional count badge and a disclosure chevron. The card is the touch target.
 */
@Composable
fun DestinationCard(
    icon: Int,
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
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(MarkIconSize))
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (badge > 0) CountBadge(badge)
                    Icon(painterResource(R.drawable.ic_lucide_chevron_right), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
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
    padding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 4.dp,
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
 * A lilac panel that explains a page: the Library principle (compact) or a call to action such as
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

/** Component / empty state, on a Material 3 [Card]. */
@Composable
fun EmptyState(icon: Int, title: String, modifier: Modifier = Modifier, text: String? = null) {
    val colors = MaterialTheme.colorScheme
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier,
        padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        spacing = 8.dp,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Afsh.colors.onSecondaryContainerVariant)
        }
    }
}

/** Component / attention notice and validation notice, on a Material 3 [Card]: coral container, title and detail. */
@Composable
fun AttentionNotice(title: String, detail: String, modifier: Modifier = Modifier, icon: Int? = null) {
    val colors = MaterialTheme.colorScheme
    PanelCard(colors.errorContainer, colors.onErrorContainer, modifier, spacing = if (icon != null) 8.dp else 4.dp) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.error, modifier = Modifier.size(26.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

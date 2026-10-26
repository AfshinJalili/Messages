package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine

private val MarkSize = 46.dp
private val MarkIconSize = 21.dp
private val DestinationHeight = 75.dp
private val ProminentPanelPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
private val CompactPanelPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** Library row: a lilac mark, a label with its description, and a disclosure chevron. */
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
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DestinationHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(MarkSize)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(MarkIconSize),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (badge > 0) CountBadge(badge)
        Icon(
            painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
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
    Column(
        modifier
            .fillMaxWidth()
            .clip(if (prominent) MaterialTheme.shapes.medium else MaterialTheme.shapes.small)
            .background(colors.secondaryContainer)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = if (prominent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            },
            color = colors.onSecondaryContainer,
        )
        Text(body, style = MaterialTheme.typography.bodySmall, color = OpenLine.colors.onSecondaryContainerVariant)
    }
}

/** Component / empty state. */
@Composable
fun EmptyState(icon: Int, title: String, modifier: Modifier = Modifier, text: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.onSecondaryContainer,
        )
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = OpenLine.colors.onSecondaryContainerVariant)
        }
    }
}

/** Component / attention notice and validation notice: coral container, title and detail. */
@Composable
fun AttentionNotice(title: String, detail: String, modifier: Modifier = Modifier, icon: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (icon != null) 8.dp else 4.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.error, modifier = Modifier.size(26.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.onErrorContainer,
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
    }
}

/** Component / decision option: a lilac choice with a heading and its consequence. */
@Composable
fun DecisionOption(title: String, explanation: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSecondaryContainer)
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = OpenLine.colors.onSecondaryContainerVariant,
        )
    }
}

package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fossify.messages.ui.Avatar

private val ActionIconSize = 22.dp
private val DetailRowHeight = 40.dp

/** The identity card of 09 and 14: avatar, the name in title-lg, and one line about it. */
@Composable
fun IdentityCard(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    isGroup: Boolean = false,
    avatarSize: Dp = 60.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(name, photoUri, isGroup, size = avatarSize)
        Text(
            name,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** 09's quick actions: an icon over a short label on a surfaceContainer tile. Share a row with weight. */
@Composable
fun ActionTile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(ActionIconSize))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, textAlign = TextAlign.Center)
    }
}

/** 13's fact rows: the label on the start, the value in bold on the end. Long values wrap. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DetailRowHeight)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 14's contact fields: an uppercase caption over the value. [onClick] makes the field an action (call, email). */
@Composable
fun LabeledField(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
    }
}

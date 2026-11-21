package com.afsh.messages.ui.components

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.Avatar
import com.afsh.messages.ui.withContentFonts
import com.afsh.messages.ui.withNumberOrder

private val ActionIconSize = 22.dp
private val DetailRowHeight = 40.dp

/** The identity card of 09 and 14, on a Material 3 [Card]: avatar, the name in title-lg, and one line about it. */
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
    PanelCard(
        colors.surfaceContainer,
        colors.onSurface,
        modifier,
        padding = PaddingValues(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.xl),
        spacing = Afsh.spacing.sm,
    ) {
        Avatar(name, photoUri, isGroup, size = avatarSize)
        Text(
            remember(name) { AnnotatedString(name).withContentFonts() },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                fontWeight = FontWeight.ExtraBold,
                textDirection = TextDirection.Content,
            ).withNumberOrder(name),
            modifier = Modifier.semantics { heading() },
        )
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * 09's quick actions, each a Material 3 [Card] the whole of which is the button: an icon over a short label.
 * Share a row with weight.
 */
@Composable
fun ActionTile(icon: AfshIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer, contentColor = colors.onSurface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Afsh.spacing.sm, vertical = Afsh.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            Icon(icon.painter(), contentDescription = null, tint = colors.primary, modifier = Modifier.size(ActionIconSize))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 13's fact rows: the label on the start, the value in bold on the end. Long values wrap. Custom: Material 3's
 * [androidx.compose.material3.ListItem] puts trailing content in a fixed slot, so a long value could not wrap at the end.
 */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DetailRowHeight)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            value,
            // Names and numbers keep their own order; the column still ends at the row's end.
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
                .withNumberOrder(value),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 14's contact fields: an uppercase caption over the value. [onClick] makes the field an action (call, email).
 * Custom: it lines up with the identity card's 4dp text inset, which Material 3's 16dp list item inset would break.
 */
@Composable
fun LabeledField(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Afsh.spacing.xs, vertical = Afsh.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content).withNumberOrder(value), color = colors.onSurface)
    }
}

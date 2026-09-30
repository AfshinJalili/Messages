package org.fossify.messages.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.OpenLineTheme

/** Outline the actual focused control, including text fields and keyboard/switch targets. */
fun Modifier.openLineFocus(shape: Shape) = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = OpenLine.colors.focusRing
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(2.dp, ring, shape) else Modifier)
}

@Composable
fun OpenLineIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.size(48.dp).clip(CircleShape).openLineFocus(CircleShape)
            .background(if (pressed) OpenLine.colors.statePressed else Color.Transparent)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun OpenLineFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.heightIn(min = 48.dp).clip(CircleShape).openLineFocus(CircleShape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.outlineVariant, CircleShape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimaryContainer else colors.primary)
    }
}

enum class OpenLineButtonKind { PRIMARY, SECONDARY, QUIET, PERMANENT }

@Composable
fun OpenLineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: OpenLineButtonKind = OpenLineButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val fill = when (kind) {
        OpenLineButtonKind.PRIMARY -> OpenLine.colors.accent
        OpenLineButtonKind.SECONDARY -> colors.surfaceContainer
        OpenLineButtonKind.QUIET -> Color.Transparent
        OpenLineButtonKind.PERMANENT -> colors.errorContainer
    }
    val ink = when (kind) {
        OpenLineButtonKind.PRIMARY -> OpenLine.colors.onAccent
        OpenLineButtonKind.PERMANENT -> colors.error
        else -> colors.primary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) fill else colors.surfaceContainerHigh,
        contentColor = if (enabled) ink else colors.onSurfaceVariant,
        border = if (kind == OpenLineButtonKind.SECONDARY) BorderStroke(1.dp, colors.outlineVariant) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 48.dp).openLineFocus(MaterialTheme.shapes.medium),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OpenLineEmptyState(title: String, body: String, icon: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(colors.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), null, tint = colors.onSecondaryContainer, modifier = Modifier.size(34.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSecondaryContainer,
            modifier = Modifier.semantics { heading() })
        if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = OpenLine.colors.onSecondaryVariant)
    }
}

@Composable
fun OpenLineLoadingResult(label: String, modifier: Modifier = Modifier) {
    val colors = OpenLine.colors
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.skeleton))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(colors.skeleton))
            Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(colors.skeletonHigh))
        }
    }
}

@Composable
fun OpenLineSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val actionColor = OpenLine.colors.inverseAccent
    SnackbarHost(state, modifier) { data ->
        Surface(color = colors.inverseSurface, contentColor = colors.inverseOnSurface,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = data::performAction, modifier = Modifier.widthIn(min = 80.dp).heightIn(min = 48.dp)
                        .openLineFocus(MaterialTheme.shapes.medium)) {
                        Text(label, color = actionColor, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, showBackground = true)
@Preview(widthDp = 390, showBackground = true, locale = "fa")
@Composable
private fun ComponentsLightPreview() = ComponentsPreview(dark = false)

@Preview(widthDp = 390, showBackground = true)
@Preview(widthDp = 390, showBackground = true, locale = "fa")
@Composable
private fun ComponentsDarkPreview() = ComponentsPreview(dark = true)

@Composable
private fun ComponentsPreview(dark: Boolean) {
    OpenLineTheme(dark = dark) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OpenLineButtonKind.entries.forEach { kind -> OpenLineButton(kind.name, {}, kind = kind) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpenLineFilterChip("All", true, {})
                    OpenLineFilterChip("Unread", false, {})
                }
                OpenLineEmptyState("You're all caught up", "New messages appear here.", R.drawable.ic_ol_check_check)
                OpenLineLoadingResult("Loading messages")
            }
        }
    }
}

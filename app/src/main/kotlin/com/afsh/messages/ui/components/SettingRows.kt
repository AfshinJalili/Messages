package com.afsh.messages.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afsh.messages.R

private val RowMinHeight = 48.dp
private val SettingIconSize = 24.dp

/** Component / section heading: an uppercase meta label above a group of settings. */
@Composable
fun SectionHeading(label: String, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Component / setting list item, on a Material 3 [ListItem]. The row is the touch target; [trailing] holds the
 * disclosure, a switch, a radio, a checkbox or nothing. Rows reach [SettingRowBleed] past their parent on each
 * side so the ripple fills the page while the label stays where the 4dp-inset rows put it.
 */
@Composable
fun SettingItem(
    label: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    value: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    bleed: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val labelColor = when {
        !enabled -> colors.onSurfaceVariant
        destructive -> colors.error
        else -> colors.onSurface
    }
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
        modifier = (if (bleed) Modifier.bleed(SettingRowBleed) else Modifier)
            .then(modifier)
            .heightIn(min = RowMinHeight),
        supportingContent = value?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = icon?.let {
            { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(SettingIconSize)) }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = labelColor,
            supportingColor = colors.onSurfaceVariant,
            leadingIconColor = if (destructive) colors.error else colors.primary,
            trailingIconColor = colors.onSurfaceVariant,
        ),
    )
}

/** How far a row reaches past its parent: [ListItem]'s 16dp inset minus the old rows' 4dp. */
private val SettingRowBleed = 12.dp

/** Lays the content out [horizontal] wider on each side than its parent allows, centred on the same spot. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val wider = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wider)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.placeRelative(-(horizontal.roundToPx()), 0) }
}

/** A setting that opens a page or dialog. */
@Composable
fun NavigationSetting(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    value: String? = null,
    disclosure: Boolean = true,
    destructive: Boolean = false,
    disclosureIcon: Int = R.drawable.ic_lucide_chevron_right,
) {
    SettingItem(
        label = label,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        icon = icon,
        value = value,
        destructive = destructive,
        trailing = if (disclosure) {
            { Icon(painterResource(disclosureIcon), contentDescription = null, modifier = Modifier.size(SettingIconSize)) }
        } else {
            null
        },
    )
}

/** A setting with Component / switch. The whole row toggles. */
@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    SettingItem(
        label = label,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        icon = icon,
        value = value,
        enabled = enabled,
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = afshSwitchColors())
    }
}

/** One choice of a group, with Component / radio. */
@Composable
fun RadioSetting(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    SettingItem(
        label = label,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        value = value,
    ) {
        RadioButton(selected = selected, onClick = null)
    }
}

/** A multi-choice item with Component / checkbox. */
@Composable
fun CheckboxSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    SettingItem(
        label = label,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        value = value,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** Component / switch: pine track with a white thumb when on. */
@Composable
fun afshSwitchColors() = with(MaterialTheme.colorScheme) {
    SwitchDefaults.colors(
        checkedTrackColor = primary,
        checkedThumbColor = surfaceContainer,
        checkedBorderColor = primary,
        uncheckedTrackColor = surfaceContainerHigh,
        uncheckedThumbColor = onSurfaceVariant,
        uncheckedBorderColor = outlineVariant,
    )
}

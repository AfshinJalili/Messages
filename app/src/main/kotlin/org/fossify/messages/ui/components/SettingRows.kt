package org.fossify.messages.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.messages.R

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
 * Component / setting list item. The row is the touch target; [trailing] holds the disclosure,
 * a switch or nothing.
 */
@Composable
fun SettingItem(
    label: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    value: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val labelColor = when {
        !enabled -> colors.onSurfaceVariant
        destructive -> colors.error
        else -> colors.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (destructive) colors.error else colors.primary,
                modifier = Modifier.size(SettingIconSize),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        trailing()
    }
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
) {
    SettingItem(
        label = label,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        icon = icon,
        value = value,
        destructive = destructive,
    ) {
        if (disclosure) {
            Icon(
                painterResource(R.drawable.ic_lucide_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SettingIconSize),
            )
        }
    }
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = openLineSwitchColors())
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
        Box(Modifier.size(RowMinHeight), contentAlignment = Alignment.Center) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
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
        Box(Modifier.size(RowMinHeight), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Component / switch: pine track with a white thumb when on. */
@Composable
fun openLineSwitchColors() = with(MaterialTheme.colorScheme) {
    SwitchDefaults.colors(
        checkedTrackColor = primary,
        checkedThumbColor = surfaceContainer,
        checkedBorderColor = primary,
        uncheckedTrackColor = surfaceContainerHigh,
        uncheckedThumbColor = onSurfaceVariant,
        uncheckedBorderColor = outlineVariant,
    )
}

package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The navigation bar's height without insets: Material 3's navigation bar container height. This is the
 * one place it is written. Bars that stand in for the navigation (the selection bar, the conversation's
 * selection bar) read it so swapping them never moves the list; `NavigationChecks` pins it to the stock bar.
 */
val BottomBarHeight: Dp = 80.dp

private val NavIconSize = 24.dp
private const val MAX_BADGE = 99
private val BadgeTextSize = 11.sp
private val BadgeLineHeight = 14.sp

// The badge stops growing at this font scale, so at 2× text it still sits inside its navigation item.
private const val BADGE_MAX_FONT_SCALE = 1.3f

// Labels shrink to fit their item before they ellipsize; at normal sizes they never shrink.
private val LabelMinSize = 8.sp

// Insets before the width: a landscape cutout must widen the rail, not squeeze its labels.
private val RailInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

/**
 * Component / primary navigation: Material 3's navigation bar at the bottom, or its navigation rail on
 * wide and short windows. Items are tabs. The Library item carries the unread spam count.
 */
@Composable
fun PrimaryNavigation(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    unreadSpam: Int = 0,
    vertical: Boolean = false,
) {
    fun badge(destination: TopDestination) = if (destination == TopDestination.LIBRARY) unreadSpam else 0
    val colors = MaterialTheme.colorScheme
    if (vertical) {
        NavigationRail(modifier, containerColor = colors.surfaceContainer, windowInsets = RailInsets) {
            CenteredItems {
                TopDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onSelect(destination) },
                        icon = { NavIcon(destination, badge(destination)) },
                        label = { NavLabel(destination, isSelected) },
                        colors = railItemColors(),
                    )
                }
            }
        }
    } else {
        NavigationBar(modifier, containerColor = colors.surfaceContainer) {
            TopDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = { NavIcon(destination, badge(destination)) },
                    label = { NavLabel(destination, isSelected) },
                    colors = barItemColors(),
                )
            }
        }
    }
}

// The selected item is primary on a secondaryContainer pill; the rest are onSurfaceVariant. Bar and rail agree.
@Composable
private fun barItemColors() = MaterialTheme.colorScheme.let {
    NavigationBarItemDefaults.colors(
        selectedIconColor = it.primary,
        selectedTextColor = it.primary,
        indicatorColor = it.secondaryContainer,
        unselectedIconColor = it.onSurfaceVariant,
        unselectedTextColor = it.onSurfaceVariant,
    )
}

@Composable
private fun railItemColors() = MaterialTheme.colorScheme.let {
    NavigationRailItemDefaults.colors(
        selectedIconColor = it.primary,
        selectedTextColor = it.primary,
        indicatorColor = it.secondaryContainer,
        unselectedIconColor = it.onSurfaceVariant,
        unselectedTextColor = it.onSurfaceVariant,
    )
}

/** Rail items sit in the middle of the rail rather than at its top. */
@Composable
private fun ColumnScope.CenteredItems(items: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.weight(1f))
    items()
    Spacer(Modifier.weight(1f))
}

@Composable
private fun NavIcon(destination: TopDestination, badge: Int) {
    BadgedBox(badge = { if (badge > 0) CountBadge(badge) }) {
        Icon(destination.icon.painter(), contentDescription = null, modifier = Modifier.size(NavIconSize))
    }
}

@Composable
private fun NavLabel(destination: TopDestination, selected: Boolean) {
    val style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
    Text(
        stringResource(destination.label),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = LabelMinSize, maxFontSize = style.fontSize),
    )
}

/**
 * Badge text keeps its own compact line height; the Persian 1.7 leading would otherwise push the
 * badge past the nav item. It scales with the font only up to [BADGE_MAX_FONT_SCALE]. Digits are
 * localized (۸۴ in Persian).
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val fontScale = LocalDensity.current.fontScale
    val shrink = minOf(fontScale, BADGE_MAX_FONT_SCALE) / fontScale
    val label = String.format(locale, "%d", count.coerceAtMost(MAX_BADGE)) + if (count > MAX_BADGE) "+" else ""
    val colors = MaterialTheme.colorScheme
    Badge(modifier, containerColor = colors.error, contentColor = colors.onError) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = BadgeTextSize * shrink, lineHeight = BadgeLineHeight * shrink))
    }
}

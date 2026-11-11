package com.afsh.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavIconSize = 21.dp
private val NavItemHeight = 64.dp
private val NavBarVerticalPadding = 8.dp

/** The navigation bar's height without insets; bars that replace it use the same so nothing jumps. */
val BottomBarHeight = NavItemHeight + NavBarVerticalPadding * 2
private val RailWidth = 96.dp
private const val MAX_BADGE = 99
private val BadgeTextSize = 11.sp
private val BadgeLineHeight = 14.sp
private val BadgeMinHeight = 16.dp

// How far the badge's centre sits below the icon's top edge.
private val BadgeDrop = 4.dp
// How much of the icon the badge covers at its end side.
private val BadgeOverlap = 6.dp

// Labels shrink to fit their item before they ellipsize; at normal sizes they never shrink.
private val LabelMinSize = 8.sp

// Insets before the width: a landscape cutout must widen the rail, not squeeze its labels.
private val RailInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

/**
 * Component / primary navigation: a bar at the bottom, or a rail on wide and short windows. The
 * Library item carries the unread spam count.
 */
@Composable
fun PrimaryNavigation(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    unreadSpam: Int = 0,
    vertical: Boolean = false,
) {
    @Composable
    fun Items(modifier: Modifier = Modifier) = TopDestination.entries.forEach { destination ->
        NavItem(
            destination = destination,
            selected = destination == selected,
            onClick = { onSelect(destination) },
            modifier = modifier,
            badge = if (destination == TopDestination.LIBRARY) unreadSpam else 0,
        )
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        if (vertical) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(RailInsets)
                    .width(RailWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
                    .selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Items(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = NavBarVerticalPadding)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Items(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = NavItemHeight)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        BadgedIcon(badge = if (badge > 0) ({ CountBadge(badge) }) else null) {
            Icon(
                painterResource(destination.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(NavIconSize),
            )
        }
        val style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
        Text(
            stringResource(destination.label),
            style = style,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = LabelMinSize, maxFontSize = style.fontSize),
        )
    }
}

/**
 * The icon with its count badge at the top end. Every item reserves the space the badge needs above
 * the icon, whether it has one or not, so the badge stays inside the item's clip at any font size and
 * the icons stay level across the bar.
 */
@Composable
private fun BadgedIcon(badge: (@Composable () -> Unit)?, icon: @Composable () -> Unit) {
    val reserve = with(LocalDensity.current) { (maxOf(BadgeMinHeight, BadgeLineHeight.toDp()) / 2 - BadgeDrop).coerceAtLeast(0.dp) }
    Layout(content = { icon(); badge?.invoke() }) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val badgePlaceable = measurables.getOrNull(1)?.measure(Constraints())
        val iconTop = reserve.roundToPx()
        val badgeTop = badgePlaceable?.let { (iconTop + BadgeDrop.roundToPx() - it.height / 2).coerceAtLeast(0) } ?: 0
        val height = maxOf(iconTop + iconPlaceable.height, badgeTop + (badgePlaceable?.height ?: 0))
        // Take the item's full width so a wide badge ("99+" at a large font) slides inward instead of
        // being cut by the item's rounded clip.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else iconPlaceable.width
        val iconStart = (width - iconPlaceable.width) / 2
        layout(width, height) {
            iconPlaceable.placeRelative(iconStart, iconTop)
            badgePlaceable?.let {
                val start = (iconStart + iconPlaceable.width - BadgeOverlap.roundToPx()).coerceAtMost(width - it.width).coerceAtLeast(0)
                it.placeRelative(start, badgeTop)
            }
        }
    }
}

/**
 * Badge text keeps its own compact line height; the Persian 1.7 leading would otherwise push the
 * badge past the nav item's clip. Digits are localized (۸۴ in Persian).
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val label = String.format(locale, "%d", count.coerceAtMost(MAX_BADGE)) + if (count > MAX_BADGE) "+" else ""
    val colors = MaterialTheme.colorScheme
    Badge(modifier, containerColor = colors.error, contentColor = colors.onError) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = BadgeTextSize, lineHeight = BadgeLineHeight))
    }
}

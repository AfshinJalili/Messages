package org.fossify.messages.ui.components

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.messages.R

private val NavIconSize = 21.dp
private val NavItemHeight = 64.dp
private val RailWidth = 96.dp
private const val MAX_BADGE = 99

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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
        BadgedBox(badge = { if (badge > 0) CountBadge(badge) }) {
            Icon(
                painterResource(destination.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(NavIconSize),
            )
        }
        Text(
            stringResource(destination.label),
            style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp))
    }
}

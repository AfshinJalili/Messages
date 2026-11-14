package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.afsh.messages.R
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh

private const val COMPACT_HEIGHT_DP = 480
private const val EXPANDED_WIDTH_DP = 600

/** Content padding of every page body: 20dp sides, 20dp top, 24dp bottom. */
val PageContentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)

/** Where a page sits in the primary navigation, or null for a page outside it (a flow step). */
class PageNavigation(
    val selected: TopDestination,
    val onSelect: (TopDestination) -> Unit,
    val unreadSpam: Int = 0,
)

/** The navigation is a rail on wide and on short windows, the same rule the inbox uses. */
@Composable
fun usesNavigationRail(): Boolean {
    val configuration = LocalConfiguration.current
    return LocalListPane.current || configuration.screenHeightDp < COMPACT_HEIGHT_DP || configuration.screenWidthDp >= EXPANDED_WIDTH_DP
}

/**
 * True when the page is the list pane of 85's list-detail split: narrow, but the window is wide,
 * so the navigation is the rail, never a bottom bar under one pane.
 */
val LocalListPane = staticCompositionLocalOf { false }

/**
 * Selection mode of a page, owned by [PageScaffold]: while it is set, the app bar shows "[count] selected" with
 * Close and Select all, [SelectionBar] rises over the navigation with [actions] and [overflow], and Back clears.
 * Screens describe what is selected and what can be done; they never draw selection chrome themselves.
 */
@Immutable
class PageSelection(
    val count: Int,
    val onClear: () -> Unit,
    val actions: List<BarAction>,
    val overflow: List<BarAction> = emptyList(),
    val onSelectAll: (() -> Unit)? = null,
)

/**
 * A secondary page: [PageAppBar], the page body and the primary navigation, with one snackbar
 * host for Undo. The app bar follows the body's scroll (Material 3 pinned behavior). [selection] puts the page in selection mode (see [PageSelection]). [bottomBar] replaces the
 * navigation for anything else that sits at the bottom, such as a disabled composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    upAction: UpAction = UpAction.BACK,
    navigation: PageNavigation? = null,
    snackbarHostState: SnackbarHostState? = null,
    selection: PageSelection? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = selection != null) { selection?.onClear?.invoke() }
    // The leaving bar keeps its icons until it has sunk, instead of blanking first.
    val leavingSelection = keptWhileLeaving(selection ?: NoSelection, empty = selection == null)
    val activeBar: (@Composable () -> Unit)? = if (selection != null) {
        { SelectionBar(leavingSelection.actions, overflow = leavingSelection.overflow) }
    } else {
        bottomBar
    }
    val rail = navigation != null && activeBar == null && usesNavigationRail()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        // The app bar and navigation handle the top and bottom insets themselves.
        contentWindowInsets = if (rail || (navigation == null && activeBar == null)) {
            WindowInsets.navigationBars
        } else {
            WindowInsets(0)
        },
        snackbarHost = { snackbarHostState?.let { AfshSnackbarHost(it) } },
        bottomBar = {
            // A selection bar rises over the navigation instead of snapping in, as on the conversation list.
            val bar = when {
                activeBar != null -> PageBottomBar.CUSTOM
                navigation != null && !rail -> PageBottomBar.NAVIGATION
                else -> PageBottomBar.NONE
            }
            val leaving = keptWhileLeaving(activeBar ?: NoBottomBar, empty = activeBar == null)
            BottomBarSwitch(bar) {
                when (it) {
                    PageBottomBar.CUSTOM -> leaving()
                    PageBottomBar.NAVIGATION -> navigation?.let { nav -> PrimaryNavigation(nav) }
                    PageBottomBar.NONE -> Unit
                }
            }
        },
    ) { padding ->
        Row(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            if (rail && navigation != null) {
                PrimaryNavigation(navigation, vertical = true)
            }
            Column(Modifier.weight(1f)) {
                if (selection != null) {
                    PageAppBar(
                        selectionTitle(selection.count),
                        onBack = selection.onClear,
                        upAction = UpAction.CLOSE,
                        scrollBehavior = scrollBehavior,
                    ) {
                        selection.onSelectAll?.let { selectAll ->
                            PageAppBarAction(AfshIcon.CheckAll, stringResource(org.fossify.commons.R.string.select_all), onClick = selectAll)
                        }
                    }
                } else {
                    PageAppBar(
                        title,
                        subtitle = subtitle,
                        onBack = onBack,
                        upAction = upAction,
                        scrollBehavior = scrollBehavior,
                        actions = actions,
                    )
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/** "3 selected": the title of every screen in selection mode. */
@Composable
fun selectionTitle(count: Int): String = pluralStringResource(R.plurals.inbox_selected, count, count)

private val NoSelection = PageSelection(0, onClear = {}, actions = emptyList())

private enum class PageBottomBar { CUSTOM, NAVIGATION, NONE }

private val NoBottomBar: @Composable () -> Unit = {}

@Composable
private fun PrimaryNavigation(navigation: PageNavigation, vertical: Boolean = false) =
    PrimaryNavigation(navigation.selected, navigation.onSelect, unreadSpam = navigation.unreadSpam, vertical = vertical)

/** Component / undo snackbar: ink slab, bold message, lime action. */
@Composable
fun AfshSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = Afsh.colors.inverseAccent,
        )
    }
}

package org.fossify.messages.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import org.fossify.messages.ui.OpenLine

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
    return configuration.screenHeightDp < COMPACT_HEIGHT_DP || configuration.screenWidthDp >= EXPANDED_WIDTH_DP
}

/**
 * A secondary page: [PageAppBar], the page body and the primary navigation, with one snackbar
 * host for Undo. [bottomBar] replaces the navigation, for selection bars.
 */
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigation: PageNavigation? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val rail = navigation != null && bottomBar == null && usesNavigationRail()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // The app bar and navigation handle the top and bottom insets themselves.
        contentWindowInsets = if (rail || (navigation == null && bottomBar == null)) {
            WindowInsets.navigationBars
        } else {
            WindowInsets(0)
        },
        snackbarHost = { snackbarHostState?.let { OpenLineSnackbarHost(it) } },
        bottomBar = {
            when {
                bottomBar != null -> bottomBar()
                navigation != null && !rail -> PrimaryNavigation(navigation)
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
                PageAppBar(title, subtitle = subtitle, onBack = onBack, actions = actions)
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
private fun PrimaryNavigation(navigation: PageNavigation, vertical: Boolean = false) =
    PrimaryNavigation(navigation.selected, navigation.onSelect, unreadSpam = navigation.unreadSpam, vertical = vertical)

/** Component / undo snackbar: ink slab, bold message, lime action. */
@Composable
fun OpenLineSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = OpenLine.colors.inverseAccent,
        )
    }
}

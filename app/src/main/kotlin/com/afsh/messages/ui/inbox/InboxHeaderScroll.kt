package com.afsh.messages.ui.inbox

import androidx.compose.animation.core.animate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue

/**
 * The inbox header's collapse (D01, design 80): the stock Material 3 enter-always app bar
 * behavior. Every pixel the finger moves first moves the header, then the list; a header let go
 * half-way settles to the nearer state, and any scroll up brings it back. The state is saved, so
 * a restored inbox keeps its collapse.
 *
 * [enabled] is false while searching or selecting; [listCanScroll] keeps a list that fits on
 * screen from collapsing the header over nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberInboxHeaderScroll(enabled: Boolean, listCanScroll: () -> Boolean): TopAppBarScrollBehavior {
    val linked by rememberUpdatedState(enabled)
    val canScroll = remember { { linked && listCanScroll() } }
    return TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState(), canScroll)
}

/**
 * Gives the header its measured collapsible height in px. A changed height (rotation, font
 * scale, first measure after a restore) keeps the fraction collapsed, not the pixel offset.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun TopAppBarState.fitCollapsibleHeight(px: Float) {
    val limit = -px
    if (heightOffsetLimit == limit) return
    val keep = collapsedFraction
    heightOffsetLimit = limit
    heightOffset = keep * limit
}

/** Opens the header, as searching and selecting need it. */
@OptIn(ExperimentalMaterial3Api::class)
internal suspend fun TopAppBarState.expand() {
    if (heightOffset == 0f) return
    animate(heightOffset, 0f) { value, _ -> heightOffset = value }
}

/** Past this collapsed fraction the header counts as collapsed: the FAB shrinks and the search button shows. */
const val HEADER_HALF = 0.5f

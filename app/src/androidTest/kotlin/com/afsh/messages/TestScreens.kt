package com.afsh.messages

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.inbox.InboxScreen
import com.afsh.messages.ui.inbox.InboxUiState
import com.afsh.messages.ui.search.SearchUiState

/**
 * [content] in the AFSH theme with the direction and sizes a check needs. [fontScale] is the
 * system font scale; [textScale] is the app's own font-size setting.
 */
@Composable
fun TestTheme(
    rtl: Boolean = false,
    dark: Boolean = false,
    fontScale: Float? = null,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides if (fontScale == null) density else Density(density.density, fontScale),
    ) {
        AfshTheme(dark = dark, textScale = textScale, content = content)
    }
}

/** The inbox screen with every callback a no-op except the ones a check passes. */
@Composable
fun TestInbox(
    state: InboxUiState,
    search: SearchUiState = SearchUiState(),
    onSearch: () -> Unit = {},
    onSearchBack: () -> Unit = {},
    onRetryRefresh: () -> Unit = {},
) = InboxScreen(
    state = state,
    snackbarHostState = remember { SnackbarHostState() },
    onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
    onSwipe = { _, _ -> }, onFilter = {}, onSearch = onSearch, onNewMessage = {}, onLibrary = {}, onSettings = {},
    search = search,
    onSearchBack = onSearchBack,
    onRetryRefresh = onRetryRefresh,
)

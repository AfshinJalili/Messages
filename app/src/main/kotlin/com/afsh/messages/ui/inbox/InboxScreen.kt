package com.afsh.messages.ui.inbox

import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.imePadding
import com.afsh.messages.ui.search.SearchContent
import com.afsh.messages.ui.search.SearchInput
import com.afsh.messages.ui.search.SearchUiState
import com.afsh.messages.ui.search.SearchFilter
import com.afsh.messages.ui.search.SEARCH_TRANSITION_MS
import com.afsh.messages.models.SearchResult
import android.content.res.Configuration
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.derivedStateOf
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import com.afsh.messages.ui.components.FloatingButton
import com.afsh.messages.ui.components.ImportNotice
import com.afsh.messages.ui.components.TabSwitch
import com.afsh.messages.ui.components.LocalListPane
import com.afsh.messages.ui.components.ConversationRow
import com.afsh.messages.ui.components.ConversationRowMaxWidth
import com.afsh.messages.ui.components.PrimaryNavigation
import com.afsh.messages.ui.components.SwipeGesture
import com.afsh.messages.ui.components.SwipeTone
import com.afsh.messages.ui.components.SwipeableRow
import com.afsh.messages.ui.components.TopDestination
import com.afsh.messages.ui.components.BottomBarSwitch
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.SelectionBar
import com.afsh.messages.ui.components.actionLabel
import com.afsh.messages.ui.components.selectionTitle
import com.afsh.messages.ui.components.keptWhileLeaving
import com.afsh.messages.ui.library.LibraryScreen
import com.afsh.messages.helpers.InboxFilter
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshTheme
import java.util.Calendar

private val TargetSize = 48.dp

// Room under the last row for the extended new-message button; geometry, not a spacing step.
private val FabClearance = 88.dp
private const val COMPACT_HEIGHT_DP = 480
private const val EXPANDED_WIDTH_DP = 600

// Both controls share a minimum that grows with the text scale.
private val BandControlHeight = 56.dp
private const val BandTitle = "band-title"
private const val BandBody = "band-body"
private const val BandTitleCollapsedScale = 25f / 31f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: InboxUiState,
    snackbarHostState: SnackbarHostState,
    onOpen: (InboxRow) -> Unit,
    onToggleSelection: (InboxRow) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAction: (InboxAction) -> Unit,
    onSwipe: (InboxRow, SwipeAction) -> Unit,
    onFilter: (InboxFilter) -> Unit,
    onSearch: () -> Unit,
    onNewMessage: () -> Unit,
    onLibrary: (LibraryDestination) -> Unit,
    selectedTab: TopDestination = TopDestination.INBOX,
    onSelectTab: (TopDestination) -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    search: SearchUiState = SearchUiState(),
    onSearchQuery: (String) -> Unit = {},
    onSearchFilter: (SearchFilter) -> Unit = {},
    onSearchResult: (SearchResult) -> Unit = {},
    onSearchBack: () -> Unit = {},
    onSearchRetry: () -> Unit = {},
    onRetryRefresh: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < COMPACT_HEIGHT_DP
    val forcedCollapsed = configuration.screenHeightDp < 320 || LocalDensity.current.fontScale >= 1.5f
    // D01 / 80: the header follows the scroll (Material 3 enter-always); searching and selecting keep it open.
    val linked = !search.open && !state.selecting && !forcedCollapsed && !compactHeight
    val header = rememberInboxHeaderScroll(linked) { listState.canScrollForward || listState.canScrollBackward }
    LaunchedEffect(linked) { if (!linked) header.state.expand() }
    // Derived, so the screen recomposes when the FAB should change, not on every scrolled pixel.
    val headerCollapsed by remember(header) { derivedStateOf { header.state.collapsedFraction > HEADER_HALF } }
    val collapsed = forcedCollapsed || headerCollapsed
    val showRail = (compactHeight || configuration.screenWidthDp >= EXPANDED_WIDTH_DP || LocalListPane.current) && !state.selecting
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        // With no bottom bar, the FAB, snackbar and list tail must clear the navigation bar themselves.
        contentWindowInsets = if (showRail) WindowInsets.navigationBars else WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.selecting && selectedTab == TopDestination.INBOX && !search.open) {
                NewMessageButton(expanded = !collapsed, onClick = onNewMessage)
            }
        },
        bottomBar = {
            // The selection bar rises over the navigation instead of snapping in.
            val bar = when {
                state.selecting -> InboxBottomBar.SELECTION
                !showRail -> InboxBottomBar.NAVIGATION
                else -> InboxBottomBar.NONE
            }
            BottomBarSwitch(bar) {
                when (it) {
                    InboxBottomBar.SELECTION -> InboxSelectionBar(state, onAction)
                    InboxBottomBar.NAVIGATION ->
                        PrimaryNavigation(selectedTab, onSelectTab, counts = state.navCounts)
                    InboxBottomBar.NONE -> Unit
                }
            }
        },
    ) { padding ->
        Row(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            if (showRail) {
                PrimaryNavigation(
                    selectedTab,
                    onSelectTab,
                    counts = state.navCounts,
                    vertical = true,
                )
            }
            // All three top-level destinations share one tab motion.
            TabSwitch(selectedTab, tabIndex = selectedTab.ordinal, Modifier.weight(1f)) { tab ->
                when (tab) {
                    TopDestination.LIBRARY -> LibraryScreen(
                        destinations = LibraryDestination.entries.filter { it.isAvailable(state) },
                        unreadSpam = state.unreadSpam,
                        onOpen = onLibrary,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TopDestination.SETTINGS -> settingsContent()
                    TopDestination.INBOX -> Column(Modifier.fillMaxSize().nestedScroll(header.nestedScrollConnection)) {
                        IdentityBand(
                            state, compactHeight, forcedCollapsed, header, onSearch, onSelectAll, onClearSelection,
                            search, onSearchQuery, onSearchBack,
                        )
                        if (search.open) {
                            SearchContent(search, onSearchQuery, onSearchFilter, onSearchResult, onSearchRetry)
                        } else {
                            InboxList(state, listState, onOpen, onToggleSelection, onSwipe, onFilter, onRetryRefresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxList(
    state: InboxUiState,
    listState: LazyListState,
    onOpen: (InboxRow) -> Unit,
    onToggleSelection: (InboxRow) -> Unit,
    onSwipe: (InboxRow, SwipeAction) -> Unit,
    onFilter: (InboxFilter) -> Unit,
    onRetryRefresh: () -> Unit,
) {
    val sections = remember(state.rows) {
        val now = Calendar.getInstance()
        state.rows.groupBy { it.section(now) }
    }
    // Clears the extended FAB so the last row stays reachable.
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = FabClearance), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "filters", contentType = "filters") {
            FilterRow(state.filter, onFilter)
        }
        if (state.refreshFailed) {
            item(key = "refresh-failed", contentType = "notice") {
                RefreshFailedNotice(onRetryRefresh, Modifier.widthIn(max = 640.dp).fillMaxWidth().animateItem())
            }
        }
        state.importProgress?.let { progress ->
            item(key = "import", contentType = "notice") {
                ImportNotice(progress, Modifier.widthIn(max = 640.dp).fillMaxWidth().animateItem().padding(horizontal = Afsh.spacing.xl, vertical = Afsh.spacing.sm))
            }
        }
        when {
            // The import notice already shows progress, so a second bar would only repeat it.
            state.loading -> if (state.importProgress == null) item(key = "loading") { LoadingState() }
            state.rows.isEmpty() -> item(key = "empty") { EmptyState(state.filter) }
            else -> sections.forEach { (section, rows) ->
                item(key = "section-${section.name}", contentType = "section") {
                    SectionHeader(section, Modifier.widthIn(max = 640.dp).fillMaxWidth().animateItem())
                }
                items(rows, key = { it.threadId }, contentType = { "row" }) { row ->
                    val selected = row.threadId in state.selected
                    SwipeableRow(
                        left = state.swipeLeft.gesture(),
                        right = state.swipeRight.gesture(),
                        enabled = !state.selecting,
                        onSwipe = { towardsRight -> onSwipe(row, if (towardsRight) state.swipeRight else state.swipeLeft) },
                        modifier = Modifier.widthIn(max = ConversationRowMaxWidth).fillMaxWidth().animateItem(),
                    ) {
                        ConversationRow(
                            row = row,
                            selected = selected,
                            selecting = state.selecting,
                            onClick = { if (state.selecting) onToggleSelection(row) else onOpen(row) },
                            onLongClick = { onToggleSelection(row) },
                            actions = rowActions(row, state.archiveAvailable) { onSwipe(row, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Selection swaps the search field for same-height controls, so rows do not jump under the finger. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityBand(
    state: InboxUiState,
    compactHeight: Boolean,
    forcedCollapsed: Boolean,
    header: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    search: SearchUiState,
    onSearchQuery: (String) -> Unit,
    onSearchBack: () -> Unit,
) {
    val title = if (state.selecting) {
        selectionTitle(state.selected.size)
    } else {
        stringResource(R.string.messages)
    }
    val subtitle = when {
        state.selecting -> stringResource(R.string.inbox_selection_hint)
        state.unreadMessages > 0 ->
            pluralStringResource(R.plurals.inbox_new_messages, state.unreadMessages, state.unreadMessages)
        else -> stringResource(R.string.no_unread_conversations)
    }
    val bandModifier = Modifier
        .fillMaxWidth()
        .background(Afsh.colors.header.container)
        .windowInsetsPadding(WindowInsets.statusBars)
    // Very short windows and very large text never have room for the full band.
    if (!search.open && (forcedCollapsed || compactHeight)) {
        StaticBar(state, title, if (forcedCollapsed) 64.dp else 104.dp, onSearch, onSelectAll, onClearSelection, bandModifier)
        return
    }
    val showSubtitle = !compactHeight && LocalConfiguration.current.screenWidthDp >= 360 && LocalDensity.current.fontScale < 1.5f
    val searchShown by remember(header) { derivedStateOf { header.state.collapsedFraction > HEADER_HALF } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        modifier = bandModifier.clipToBounds(),
        content = {
            // The collapsible part slides up under the title row, fading as it goes.
            Column(
                Modifier
                    .layoutId(BandBody)
                    .graphicsLayer { alpha = 1f - header.state.collapsedFraction }
                    .padding(start = Afsh.spacing.xl, end = Afsh.spacing.xl, bottom = Afsh.spacing.xl),
            ) {
                AnimatedVisibility(
                    visible = showSubtitle && !search.open,
                    enter = fadeIn(tween(SEARCH_TRANSITION_MS)) + expandVertically(tween(SEARCH_TRANSITION_MS)),
                    exit = fadeOut(tween(SEARCH_TRANSITION_MS)) + shrinkVertically(tween(SEARCH_TRANSITION_MS)),
                ) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Afsh.colors.onBandVariant,
                        modifier = Modifier.padding(bottom = Afsh.spacing.md),
                    )
                }
                when {
                    search.open -> SearchInput(search.query, onSearchQuery, onSearchBack)
                    state.selecting -> SelectionControls(onSelectAll, onClearSelection)
                    else -> SearchField(onSearch)
                }
            }
            // The wrapper stays when search hides the title, so the layout always finds it.
            Box(Modifier.layoutId(BandTitle)) {
                AnimatedVisibility(
                    visible = !search.open,
                    enter = fadeIn(tween(SEARCH_TRANSITION_MS)) + expandVertically(tween(SEARCH_TRANSITION_MS)),
                    exit = fadeOut(tween(SEARCH_TRANSITION_MS)) + shrinkVertically(tween(SEARCH_TRANSITION_MS)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Afsh.colors.header.container)
                            .heightIn(min = 64.dp)
                            .padding(start = Afsh.spacing.xl, end = Afsh.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Afsh.colors.header.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    // Draw-only shrink from headline (31) to title-lg (25): no text layout per frame.
                                    val titleScale = BandTitleCollapsedScale + (1f - BandTitleCollapsedScale) * (1f - header.state.collapsedFraction)
                                    scaleX = titleScale
                                    scaleY = titleScale
                                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                                }
                                .semantics { heading() },
                        )
                        // 80: the search button arrives as the field leaves, so search is always one tap away.
                        IconButton(
                            onClick = onSearch,
                            enabled = searchShown && !state.selecting,
                            modifier = Modifier
                                .graphicsLayer { alpha = if (state.selecting) 0f else header.state.collapsedFraction }
                                // Not announced while it isn't there to tap.
                                .then(if (searchShown && !state.selecting) Modifier else Modifier.clearAndSetSemantics {}),
                        ) {
                            Icon(AfshIcon.Search.painter(), stringResource(R.string.inbox_search_hint), tint = Afsh.colors.header.content)
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val titleRow = measurables.first { it.layoutId == BandTitle }.measure(loose)
        val body = measurables.first { it.layoutId == BandBody }.measure(loose)
        header.state.fitCollapsibleHeight(body.height.toFloat())
        val offset = header.state.heightOffset.roundToInt()
        val height = titleRow.height + (body.height + offset).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            body.place(0, titleRow.height + offset)
            // Placed second, so the body slides under it.
            titleRow.place(0, 0)
        }
    }
}

/** The fixed one-row bar for windows too short for the band (D09) and for text at 1.5×. */
@Composable
private fun StaticBar(
    state: InboxUiState,
    title: String,
    minHeight: androidx.compose.ui.unit.Dp,
    onSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.heightIn(min = minHeight).padding(horizontal = Afsh.spacing.xl, vertical = Afsh.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.xl),
    ) {
        if (state.selecting) {
            IconButton(onClick = onClearSelection) {
                Icon(AfshIcon.Close.painter(), stringResource(org.fossify.commons.R.string.close), tint = Afsh.colors.header.content)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Afsh.colors.header.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (state.selecting) {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(org.fossify.commons.R.string.select_all), style = MaterialTheme.typography.labelMedium, color = Afsh.colors.header.content)
            }
        } else {
            IconButton(onClick = onSearch) {
                Icon(AfshIcon.Search.painter(), stringResource(R.string.inbox_search_hint), tint = Afsh.colors.header.content)
            }
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BandControlHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)),
    ) {
        Row(
            Modifier.padding(horizontal = Afsh.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            Icon(
                AfshIcon.Search.painter(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(stringResource(R.string.inbox_search_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectionControls(onSelectAll: () -> Unit, onClearSelection: () -> Unit) {
    val onBand = Afsh.colors.header.content
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BandControlHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(
                AfshIcon.Close.painter(),
                contentDescription = stringResource(org.fossify.commons.R.string.close),
                tint = onBand,
            )
        }
        Box(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) {
            Text(stringResource(org.fossify.commons.R.string.select_all), style = MaterialTheme.typography.labelMedium, color = onBand)
        }
    }
}

@Composable
private fun FilterRow(current: InboxFilter, onFilter: (InboxFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Afsh.spacing.xl, end = Afsh.spacing.xl, top = Afsh.spacing.xl, bottom = Afsh.spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
    ) {
        InboxFilter.entries.forEach { filter ->
            val selected = filter == current
            Box(
                Modifier
                    .heightIn(min = TargetSize)
                    .clip(CircleShape)
                    .background(if (selected) Afsh.colors.selected.container else colors.surfaceContainer)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onFilter(filter) })
                    .padding(horizontal = Afsh.spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(filter.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Afsh.colors.selected.content else colors.primary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(section: InboxSection, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(section.label).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = Afsh.spacing.xl, end = Afsh.spacing.xl, top = Afsh.spacing.lg, bottom = Afsh.spacing.xs)
            .semantics { heading() },
    )
}

/** The panel a configured swipe reveals; [SwipeAction.NONE] reveals nothing. */
private fun SwipeAction.gesture(): SwipeGesture? = when (this) {
    SwipeAction.NONE -> null
    SwipeAction.DELETE -> SwipeGesture(label, AfshIcon.Delete, SwipeTone.DESTRUCTIVE)
    SwipeAction.MUTE -> SwipeGesture(label, AfshIcon.NotificationsOff, SwipeTone.QUIET)
    SwipeAction.ARCHIVE -> SwipeGesture(label, AfshIcon.Archive, SwipeTone.PRIMARY)
}

/** TalkBack equivalents of the swipe actions, available whatever the swipe settings are. */
@Composable
private fun rowActions(row: InboxRow, archiveAvailable: Boolean, onAction: (SwipeAction) -> Unit): List<CustomAccessibilityAction> {
    val archive = stringResource(R.string.archive)
    val delete = stringResource(org.fossify.commons.R.string.delete)
    val mute = stringResource(if (row.muted) R.string.unmute_conversation else R.string.mute_conversation)
    return listOfNotNull(
        CustomAccessibilityAction(archive) { onAction(SwipeAction.ARCHIVE); true }.takeIf { archiveAvailable },
        CustomAccessibilityAction(delete) { onAction(SwipeAction.DELETE); true },
        CustomAccessibilityAction(mute) { onAction(SwipeAction.MUTE); true },
    )
}

/** 73 · Inbox / refresh failed (L8): inline, with Retry; the cached list stays below. No toast. */
@Composable
private fun RefreshFailedNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = Afsh.spacing.xl, vertical = Afsh.spacing.sm)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.errorContainer)
            .padding(start = Afsh.spacing.lg, top = Afsh.spacing.sm, end = Afsh.spacing.sm, bottom = Afsh.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
    ) {
        Icon(AfshIcon.CloudOff.painter(), contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xxs)) {
            Text(stringResource(R.string.refresh_failed_title), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.onErrorContainer)
            Text(stringResource(R.string.refresh_failed_text), style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
        }
        TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.textButtonColors(containerColor = colors.surfaceContainer, contentColor = colors.primary),
            contentPadding = PaddingValues(horizontal = Afsh.spacing.lg),
        ) {
            Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun LoadingState() {
    Column(Modifier.padding(Afsh.spacing.xl), verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            stringResource(R.string.loading_messages),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(filter: InboxFilter) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(Afsh.spacing.xl)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .padding(Afsh.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
    ) {
        Icon(
            (if (filter == InboxFilter.UNREAD) AfshIcon.CheckAll else AfshIcon.Chat).painter(),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(34.dp),
        )
        Text(
            stringResource(filter.emptyTitle()),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.onSecondaryContainer,
        )
        if (filter == InboxFilter.ALL) {
            Text(stringResource(R.string.inbox_empty_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
        }
    }
}

@Composable
private fun NewMessageButton(expanded: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.new_message)
    FloatingButton(AfshIcon.Chat, description = label, onClick = onClick, text = label, expanded = expanded)
}

@Composable
private fun InboxSelectionBar(state: InboxUiState, onAction: (InboxAction) -> Unit) {
    val count = state.selected.size.coerceAtLeast(1)
    val actions = remember(state.selected, state.rows, state.archiveAvailable) {
        availableActions(state.selectedRows, state.archiveAvailable).splitForBar()
    }
    val (bar, overflow) = keptWhileLeaving(actions, empty = state.selected.isEmpty())
    SelectionBar(
        bar.map { it.toBarAction(count, onAction) },
        overflow = overflow.map { it.toBarAction(count, onAction) },
    )
}

@Composable
private fun InboxAction.toBarAction(count: Int, onAction: (InboxAction) -> Unit) =
    BarAction(
        actionLabel(label, count, labelIsQuantity),
        icon,
        destructive = this == InboxAction.BLOCK || this == InboxAction.DELETE,
    ) { onAction(this) }

private enum class InboxBottomBar { SELECTION, NAVIGATION, NONE }

private fun previewRow(id: Long, title: String, snippet: String, minutesAgo: Int, read: Boolean, draft: String? = null, pinned: Boolean = false, muted: Boolean = false) =
    InboxRow(
        Conversation(
            threadId = id,
            snippet = snippet,
            date = ((System.currentTimeMillis() / 1000) - minutesAgo * 60).toInt(),
            read = read,
            title = title,
            photoUri = "",
            isGroupConversation = false,
            phoneNumber = "",
        ),
        draft = draft,
        pinned = pinned,
        muted = muted,
    )

private val previewState = InboxUiState(
    rows = listOf(
        previewRow(1, "Mina Farahani", "The train leaves at six.", 5, read = false, pinned = true),
        previewRow(2, "Dad", "Photo • That’s the one!", 60, read = false),
        previewRow(3, "Book club", "Thursday works for me", 60 * 24, read = true, draft = "Can you bring the tickets?", muted = true),
        previewRow(4, "+98 912 555 0199", "Your verification code is 482913", 60 * 24 * 3, read = true),
    ),
    unreadMessages = 2,
    unreadSpam = 3,
    swipeLeft = SwipeAction.ARCHIVE,
)

@Composable
private fun InboxPreviewContent(state: InboxUiState, dark: Boolean) {
    AfshTheme(dark = dark) {
        InboxScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
            onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InboxPreview() = InboxPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InboxDarkPreview() = InboxPreviewContent(previewState, dark = true)

@Preview(widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun InboxRtlPreview() = InboxPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InboxSelectionPreview() = InboxPreviewContent(previewState.copy(selected = setOf(1, 2)), dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InboxEmptyPreview() = InboxPreviewContent(InboxUiState(), dark = false)

package org.fossify.messages.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.imePadding
import org.fossify.messages.ui.search.SearchContent
import org.fossify.messages.ui.search.SearchInput
import org.fossify.messages.ui.search.SearchUiState
import org.fossify.messages.ui.search.SearchFilter
import org.fossify.messages.ui.search.SEARCH_TRANSITION_MS
import org.fossify.messages.models.SearchResult
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import android.graphics.Bitmap
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.messages.R
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.Avatar
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.OpenLineTheme
import java.util.Calendar

// A deliberate half-row drag commits. Flicks do not: the default fling velocity made a short flick
// delete or archive a conversation.
private const val SWIPE_COMMIT_FRACTION = 0.5f
private val AvatarSize = 50.dp
private val TargetSize = 48.dp
private const val COMPACT_HEIGHT_DP = 480
private const val EXPANDED_WIDTH_DP = 600

// Both controls share a minimum that grows with the text scale.
private val BandControlHeight = 56.dp
private enum class BandLayout { EXPANDED, COMPACT, COLLAPSED }

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
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    search: SearchUiState = SearchUiState(),
    onSearchQuery: (String) -> Unit = {},
    onSearchFilter: (SearchFilter) -> Unit = {},
    onSearchResult: (SearchResult) -> Unit = {},
    onSearchBack: () -> Unit = {},
    onSearchRetry: () -> Unit = {},
    libraryOpen: Boolean = false,
    onLibraryTab: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var scrolledDown by rememberSaveable { mutableStateOf(false) }
    val searchOpen by rememberUpdatedState(search.open)
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!searchOpen && source == NestedScrollSource.UserInput && available.y != 0f) {
                    scrolledDown = available.y < 0f
                }
                return Offset.Zero
            }
        }
    }
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < COMPACT_HEIGHT_DP
    val collapsed = scrolledDown || configuration.screenHeightDp < 320 || LocalDensity.current.fontScale >= 1.5f
    val showRail = (compactHeight || configuration.screenWidthDp >= EXPANDED_WIDTH_DP) && !state.selecting
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        // With no bottom bar, the FAB, snackbar and list tail must clear the navigation bar themselves.
        contentWindowInsets = if (showRail) WindowInsets.navigationBars else WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.selecting && !libraryOpen && !search.open) {
                NewMessageButton(expanded = !collapsed, onClick = onNewMessage)
            }
        },
        bottomBar = {
            if (state.selecting) {
                SelectionBar(state, onAction)
            } else if (!showRail) {
                InboxNavigation(state.unreadSpam, onLibrary = { onLibraryTab(true) }, onSettings = onSettings, librarySelected = libraryOpen, onInbox = { onLibraryTab(false) })
            }
        },
    ) { padding ->
        Row(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            if (showRail) {
                InboxNavigation(
                    state.unreadSpam,
                    onLibrary = { onLibraryTab(true) },
                    onSettings = onSettings,
                    vertical = true,
                    librarySelected = libraryOpen,
                    onInbox = { onLibraryTab(false) },
                )
            }
            if (libraryOpen) {
                LibraryPage(state, onLibrary, Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f).nestedScroll(scrollConnection)) {
                    IdentityBand(
                        state, compactHeight, collapsed, onSearch, onSelectAll, onClearSelection,
                        search, onSearchQuery, onSearchBack,
                    )
                    if (search.open) {
                        SearchContent(search, onSearchQuery, onSearchFilter, onSearchResult, onSearchRetry)
                    } else {
                        InboxList(state, listState, onOpen, onToggleSelection, onSwipe, onFilter)
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
) {
    val sections = remember(state.rows) {
        val now = Calendar.getInstance()
        state.rows.groupBy { it.section(now) }
    }
    // Clears the extended FAB so the last row stays reachable.
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "filters", contentType = "filters") {
            FilterRow(state.filter, onFilter)
        }
        when {
            state.loading -> item(key = "loading") { LoadingState() }
            state.rows.isEmpty() -> item(key = "empty") { EmptyState(state.filter) }
            else -> sections.forEach { (section, rows) ->
                item(key = "section-${section.name}", contentType = "section") {
                    SectionHeader(section, Modifier.widthIn(max = 640.dp).fillMaxWidth().animateItem())
                }
                items(rows, key = { it.threadId }, contentType = { "row" }) { row ->
                    val selected = row.threadId in state.selected
                    SwipeableRow(
                        swipeLeft = state.swipeLeft,
                        swipeRight = state.swipeRight,
                        enabled = !state.selecting,
                        onSwipe = { onSwipe(row, it) },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().animateItem(),
                    ) {
                        ConversationRow(
                            row = row,
                            selected = selected,
                            selecting = state.selecting,
                            onClick = { if (state.selecting) onToggleSelection(row) else onOpen(row) },
                            onLongClick = { onToggleSelection(row) },
                            archiveAvailable = state.archiveAvailable,
                            onAccessibleAction = { onSwipe(row, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Selection swaps the search field for same-height controls, so rows do not jump under the finger. */
@Composable
private fun IdentityBand(
    state: InboxUiState,
    compactHeight: Boolean,
    collapsed: Boolean,
    onSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    search: SearchUiState,
    onSearchQuery: (String) -> Unit,
    onSearchBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = if (state.selecting) {
        pluralStringResource(R.plurals.inbox_selected, state.selected.size, state.selected.size)
    } else {
        stringResource(R.string.messages)
    }
    val subtitle = when {
        state.selecting -> stringResource(R.string.inbox_selection_hint)
        state.unreadMessages > 0 ->
            pluralStringResource(R.plurals.inbox_new_messages, state.unreadMessages, state.unreadMessages)
        else -> stringResource(R.string.no_unread_conversations)
    }
    val controls: @Composable () -> Unit = {
        if (state.selecting) {
            SelectionControls(onSelectAll, onClearSelection)
        } else {
            SearchField(onSearch)
        }
    }
    val bandModifier = Modifier
        .fillMaxWidth()
        .background(colors.primaryContainer)
        .windowInsetsPadding(WindowInsets.statusBars)

    val layout = when {
        search.open -> BandLayout.EXPANDED
        collapsed -> BandLayout.COLLAPSED
        compactHeight -> BandLayout.COMPACT
        else -> BandLayout.EXPANDED
    }
    AnimatedContent(
        targetState = layout,
        modifier = bandModifier,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(120))) using SizeTransform(clip = true)
        },
        label = "inbox band",
    ) { visibleLayout ->
        if (visibleLayout != BandLayout.EXPANDED) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (visibleLayout == BandLayout.COLLAPSED) 64.dp else 104.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.selecting) {
                IconButton(onClick = onClearSelection) {
                    Icon(painterResource(org.fossify.commons.R.drawable.ic_cross_vector), stringResource(org.fossify.commons.R.string.close), tint = colors.onPrimaryContainer)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (state.selecting) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(org.fossify.commons.R.string.select_all), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                }
            } else {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(org.fossify.commons.R.drawable.ic_search_vector), stringResource(R.string.inbox_search_hint), tint = colors.onPrimaryContainer)
                }
            }
        }
        } else {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = !search.open,
                enter = fadeIn(tween(SEARCH_TRANSITION_MS)) + expandVertically(tween(SEARCH_TRANSITION_MS)),
                exit = fadeOut(tween(SEARCH_TRANSITION_MS)) + shrinkVertically(tween(SEARCH_TRANSITION_MS)),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
                if (!compactHeight && LocalConfiguration.current.screenWidthDp >= 360 && LocalDensity.current.fontScale < 1.5f) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OpenLine.colors.onBandVariant)
                }
            }
            }
            if (search.open) SearchInput(search.query, onSearchQuery, onSearchBack) else controls()
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
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(org.fossify.commons.R.drawable.ic_search_vector),
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
    val onBand = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BandControlHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(
                painterResource(org.fossify.commons.R.drawable.ic_cross_vector),
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
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InboxFilter.entries.forEach { filter ->
            val selected = filter == current
            Box(
                Modifier
                    .heightIn(min = TargetSize)
                    .clip(CircleShape)
                    .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onFilter(filter) })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(filter.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.primary,
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
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * SwipeToDismissBox works in layout direction, but the swipe settings are physical, so the mapping
 * flips under RTL.
 */
@Composable
private fun SwipeableRow(
    swipeLeft: SwipeAction,
    swipeRight: SwipeAction,
    enabled: Boolean,
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val startToEnd = if (rtl) swipeLeft else swipeRight
    val endToStart = if (rtl) swipeRight else swipeLeft
    var widthPx by remember { mutableFloatStateOf(0f) }
    val stateRef = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    // The deprecated overload is the only one that can veto a dismiss; used here to ignore flicks
    // that were released short of the commit distance.
    @Suppress("DEPRECATION")
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val released = runCatching { stateRef[0]?.requireOffset() }.getOrNull() ?: 0f
            value == SwipeToDismissBoxValue.Settled || abs(released) >= widthPx * SWIPE_COMMIT_FRACTION
        },
        positionalThreshold = { it * SWIPE_COMMIT_FRACTION },
    ).also { stateRef[0] = it }
    val armed by remember {
        derivedStateOf {
            val offset = runCatching { state.requireOffset() }.getOrNull() ?: 0f
            widthPx > 0f && abs(offset) >= widthPx * SWIPE_COMMIT_FRACTION
        }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat() },
        enableDismissFromStartToEnd = enabled && startToEnd != SwipeAction.NONE,
        enableDismissFromEndToStart = enabled && endToStart != SwipeAction.NONE,
        onDismiss = { value ->
            onSwipe(if (value == SwipeToDismissBoxValue.StartToEnd) startToEnd else endToStart)
            // Mute keeps the row; archive and delete remove it, and Undo must bring it back settled.
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val action = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> startToEnd
                SwipeToDismissBoxValue.EndToStart -> endToStart
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            if (action != SwipeAction.NONE) {
                SwipeBackground(action, armed, alignStart = direction == SwipeToDismissBoxValue.StartToEnd)
            }
        },
        content = content,
    )
}

/** The tonal panel reveals its label only once releasing would commit the action. */
@Composable
private fun SwipeBackground(action: SwipeAction, armed: Boolean, alignStart: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (actionColor, onAction) = when (action) {
        SwipeAction.DELETE -> colors.errorContainer to colors.error
        SwipeAction.MUTE -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    val background by animateColorAsState(actionColor, label = "swipe background")
    val tint by animateColorAsState(onAction, label = "swipe icon")
    val scale by animateFloatAsState(if (armed) 1.25f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "swipe icon scale")
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 24.dp),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(action.icon), contentDescription = stringResource(action.label), tint = tint,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
            if (armed) Text(stringResource(action.label), color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    row: InboxRow,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    archiveAvailable: Boolean,
    onAccessibleAction: (SwipeAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val conversation = row.conversation
    val unread = !conversation.read
    val fontScale = LocalDensity.current.fontScale
    val archiveLabel = stringResource(R.string.archive)
    val deleteLabel = stringResource(org.fossify.commons.R.string.delete)
    val muteLabel = stringResource(if (row.muted) R.string.unmute_conversation else R.string.mute_conversation)
    val unreadLabel = if (unread) pluralStringResource(R.plurals.inbox_row_unread, conversation.unreadCount.coerceAtLeast(1), conversation.unreadCount.coerceAtLeast(1)) else null
    val mutedLabel = stringResource(R.string.muted)
    val pinnedLabel = stringResource(R.string.inbox_section_pinned)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val time = remember(conversation.date, configuration) {
        (conversation.date * 1000L).formatDateOrTime(context, hideTimeOnOtherDays = true, showCurrentYear = false)
    }
    val description = listOfNotNull(conversation.title, unreadLabel, row.draft ?: conversation.snippet, time,
        mutedLabel.takeIf { row.muted }, pinnedLabel.takeIf { row.pinned }).joinToString(", ")
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.inbox_select),
            )
            .semantics(mergeDescendants = true) {
                if (selecting) this.selected = selected
                contentDescription = description
                if (!selecting) customActions = listOfNotNull(
                    CustomAccessibilityAction(archiveLabel) { onAccessibleAction(SwipeAction.ARCHIVE); true }.takeIf { archiveAvailable },
                    CustomAccessibilityAction(deleteLabel) { onAccessibleAction(SwipeAction.DELETE); true },
                    CustomAccessibilityAction(muteLabel) { onAccessibleAction(SwipeAction.MUTE); true },
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(AvatarSize), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(AvatarSize).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(org.fossify.commons.R.drawable.ic_check_vector), null, tint = colors.onPrimary)
                }
            } else {
                Avatar(conversation.title, conversation.photoUri, conversation.isGroupConversation, AvatarSize)
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = if (fontScale >= 1.3f) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fontScale >= 1.5f) Metadata(row, unread, inline = true)
            SnippetText(row, unread)
        }
        if (fontScale < 1.5f) Box(Modifier.clearAndSetSemantics { }) { Metadata(row, unread) }
    }
}

@Composable
internal fun SnippetText(row: InboxRow, unread: Boolean) {
    val colors = MaterialTheme.colorScheme
    val draft = row.draft
    if (!draft.isNullOrEmpty()) {
        val label = stringResource(R.string.inbox_draft_label)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.error, fontWeight = FontWeight.Bold)) { append(label) }
                append(' ')
                append(draft)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else if (row.conversation.isScheduled) {
        val time = android.text.format.DateFormat.format("EEE HH:mm", row.conversation.date * 1000L).toString()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(org.fossify.commons.R.drawable.ic_clock_vector), null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.inbox_scheduled_at, time), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text(
            row.conversation.snippet,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (row.conversation.isScheduled) FontStyle.Italic else FontStyle.Normal,
            color = if (unread) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Metadata(row: InboxRow, unread: Boolean, inline: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val date = row.conversation.date
    val time = remember(date) {
        (date * 1000L).formatDateOrTime(context, hideTimeOnOtherDays = true, showCurrentYear = false)
    }
    val signals: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.pinned) Icon(painterResource(org.fossify.commons.R.drawable.ic_pin_vector), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            if (row.muted) Icon(painterResource(R.drawable.ic_bell_off_vector), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            if (unread) {
                if (row.conversation.unreadCount > 1) {
                    Box(Modifier.heightIn(min = 22.dp).clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text(String.format(LocalConfiguration.current.locales[0], "%d", row.conversation.unreadCount),
                            color = colors.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
                }
            }
        }
    }
    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(time, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            signals()
        }
    } else {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(time, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            signals()
        }
    }
}

@Composable
private fun LoadingState() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            .padding(20.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(if (filter == InboxFilter.UNREAD) R.drawable.ic_check_double_vector else R.drawable.ic_message_bubble),
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
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        containerColor = OpenLine.colors.accent,
        contentColor = OpenLine.colors.onAccent,
        shape = MaterialTheme.shapes.medium,
        // Collapsed it shows only the icon, so the icon must carry the label for TalkBack.
        icon = { Icon(painterResource(R.drawable.ic_message_bubble), contentDescription = if (expanded) null else stringResource(R.string.new_message)) },
        text = { Text(stringResource(R.string.new_message), style = MaterialTheme.typography.labelLarge) },
    )
}

@Composable
private fun InboxNavigation(
    unreadSpam: Int,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    vertical: Boolean = false,
    librarySelected: Boolean = false,
    onInbox: () -> Unit = {},
) {
    @Composable
    fun Items(itemModifier: Modifier) {
        NavItem(R.drawable.ic_message_bubble, stringResource(R.string.inbox_nav_inbox), !librarySelected, onInbox, itemModifier)
        NavItem(R.drawable.ic_library_vector, stringResource(R.string.inbox_nav_library), librarySelected, onLibrary, itemModifier, unreadSpam)
        NavItem(
            org.fossify.commons.R.drawable.ic_settings_cog_vector,
            stringResource(org.fossify.commons.R.string.settings),
            false,
            onSettings,
            itemModifier,
        )
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        if (vertical) {
            Column(
                Modifier
                    .fillMaxHeight()
                    // Insets before the width: a landscape cutout must widen the rail, not squeeze its labels.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                    .width(96.dp)
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
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        BadgedBox(badge = {
            if (badge > 0) {
                CountBadge(badge)
            }
        }) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SelectionBar(state: InboxUiState, onAction: (InboxAction) -> Unit) {
    val (bar, overflow) = remember(state.selected, state.rows, state.archiveAvailable) {
        availableActions(state.selectedRows, state.archiveAvailable).splitForBar()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(8.dp),
        ) {
            bar.forEach { action ->
                ActionItem(action.icon, stringResource(action.label), { onAction(action) }, Modifier.weight(1f), destructive = action == InboxAction.DELETE)
            }
            if (overflow.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    ActionItem(
                        org.fossify.commons.R.drawable.ic_three_dots_vector,
                        stringResource(org.fossify.commons.R.string.more_options),
                        { open = true },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        overflow.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(action.label)) },
                                leadingIcon = { Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                modifier = Modifier.heightIn(min = TargetSize),
                                colors = androidx.compose.material3.MenuDefaults.itemColors(
                                    textColor = if (action == InboxAction.BLOCK || action == InboxAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = if (action == InboxAction.BLOCK || action == InboxAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                onClick = {
                                    open = false
                                    onAction(action)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionItem(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Badge text keeps its own compact line height; the Persian 1.7 leading would otherwise push the
 * badge past the nav item's clip. Digits are localized (۸۴ in Persian).
 */
@Composable
private fun CountBadge(count: Int) {
    Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
        Text((String.format(LocalConfiguration.current.locales[0], "%d", count.coerceAtMost(99)) + if (count > 99) "+" else ""), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPage(state: InboxUiState, onOpen: (LibraryDestination) -> Unit, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.inbox_nav_library), style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.library_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        LibraryDestination.entries.filter { it.isAvailable(state) }.forEach { destination ->
            val count = if (destination == LibraryDestination.SPAM) state.unreadSpam else 0
            val description = when (destination) {
                LibraryDestination.STARRED -> R.string.library_starred_description
                LibraryDestination.ARCHIVE -> R.string.library_archive_description
                LibraryDestination.SPAM -> R.string.library_spam_description
                LibraryDestination.RECYCLE_BIN -> R.string.library_recycle_description
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 75.dp).clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainer).clickable { onOpen(destination) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(destination.icon), null, tint = colors.primary, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(destination.label), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (count > 0) CountBadge(count)
                Icon(painterResource(org.fossify.commons.R.drawable.ic_chevron_right_vector), null,
                    tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp).graphicsLayer {
                        scaleX = if (rtl) -1f else 1f
                    })
            }
        }
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(colors.secondaryContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.library_principle_title), style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer)
            Text(stringResource(R.string.library_principle_body), style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
        }
    }
}

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
    OpenLineTheme(dark = dark) {
        InboxScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
            onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
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

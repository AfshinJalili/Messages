package com.afsh.messages.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.afsh.messages.R
import com.afsh.messages.helpers.THREAD_DATE_TIME
import com.afsh.messages.helpers.THREAD_UNREAD_SEPARATOR
import com.afsh.messages.helpers.generateStableId
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.AfshSnackbarHost
import com.afsh.messages.ui.components.FloatingButton
import com.afsh.messages.ui.components.MixedLocaleText
import com.afsh.messages.ui.components.UnreadDivider
import com.afsh.messages.ui.isolate
import kotlinx.coroutines.flow.distinctUntilChanged

// Same limits the View timeline used.
private const val JUMP_BUTTON_ITEM_LIMIT = 20
private const val LOAD_OLDER_THRESHOLD = 45

const val THREAD_LIST_TAG = "thread_list"
const val THREAD_JUMP_TAG = "thread_jump_to_latest"

/**
 * The list is laid out newest-first from the bottom (reverseLayout), so it opens at the latest
 * message, keeps its place while older pages load above, and stays pinned to the bottom when the
 * keyboard resizes the window.
 */
@Composable
fun ThreadTimeline(
    state: ThreadUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onDim: (Float) -> Unit = {},
) {
    val shown = remember(state.items) {
        state.items.filter { it !is ThreadItem.ThreadError && it !is ThreadItem.ThreadSending && it !is ThreadItem.ThreadSent }
    }
    val newestFirst = remember(shown) { shown.asReversed() }
    val event by rememberUpdatedState(onEvent)
    val stableOnEvent = remember { { e: ThreadEvent -> event(e) } }
    val strings = rememberBubbleStrings()
    TimelineEffects(state, newestFirst, listState, stableOnEvent)
    var lifted by remember { mutableStateOf<LiftedBubble?>(null) }
    // The sheet's own message: the lift can land and clear before the sheet's dismissal settles, and the sheet
    // must stay composed until then or a dismissThen action (Delete, Forward) is cancelled with its scope.
    var sheetFor by remember { mutableStateOf<Message?>(null) }
    // Set when the sheet starts to slide away, well before onDismiss, so the bubble leaves with it (#308).
    var sheetLeaving by remember { mutableStateOf(false) }
    var sheetTop by remember { mutableFloatStateOf(Float.NaN) }
    val openSheet = remember {
        { message: Message, groupedBelow: Boolean, coordinates: LayoutCoordinates ->
            lifted = LiftedBubble(message, groupedBelow, Rect(coordinates.positionOnScreen(), coordinates.size.toSize()))
            sheetTop = Float.NaN
            sheetLeaving = false
            sheetFor = message
        }
    }
    val liftedId = lifted?.message?.getStableId()

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.Center) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = Afsh.spacing.md),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .testTag(THREAD_LIST_TAG),
            ) {
                items(newestFirst.size, key = { newestFirst[it].key() }, contentType = { newestFirst[it]::class }) { index ->
                    // Chronological neighbours: index + 1 is older, index - 1 newer.
                    val item = newestFirst[index]
                    val older = newestFirst.getOrNull(index + 1)
                    val newer = newestFirst.getOrNull(index - 1)
                    when (item) {
                        is Message -> {
                            val stableId = item.getStableId()
                            MessageRow(
                                message = item,
                                older = older,
                                newer = newer,
                                selecting = state.selecting,
                                selected = stableId in state.selected,
                                starred = state.isStarred(item),
                                spamReason = state.spamReason(item),
                                simLabel = state.simLabels[item.subscriptionId],
                                sendSim = state.sendSim,
                                isGroup = state.isGroup,
                                isRecycleBin = state.isRecycleBin,
                                hidden = liftedId == stableId,
                                strings = strings,
                                onEvent = stableOnEvent,
                                onOpen = openSheet,
                            )
                        }
                        is ThreadItem.ThreadDateTime -> DatePill(item.date, Modifier.padding(vertical = Afsh.spacing.md))
                        is ThreadItem.ThreadUnreadSeparator -> UnreadDivider()
                        else -> Unit
                    }
                }
            }
            if (state.empty && shown.isEmpty()) EmptyConversation(state.firstName)
            StickyDate(newestFirst, listState, Modifier.align(Alignment.TopCenter))
            JumpToLatest(state, newestFirst, listState, stableOnEvent, Modifier.align(Alignment.BottomEnd))
            AfshSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            lifted?.let { lift ->
                val liftMsg = lift.message
                LiftOverlay(
                    lift = lift,
                    up = sheetFor != null && !sheetLeaving,
                    sheetTop = sheetTop,
                    selecting = state.selecting,
                    selected = liftMsg.getStableId() in state.selected,
                    starred = state.isStarred(liftMsg),
                    spamReason = state.spamReason(liftMsg),
                    simLabel = state.simLabels[liftMsg.subscriptionId],
                    sendSim = state.sendSim,
                    isRecycleBin = state.isRecycleBin,
                    strings = strings,
                    onDim = onDim,
                    onGone = { if (lifted === lift) lifted = null },
                )
            }
        }
    }
    sheetFor?.let { message ->
        MessageSheet(
            message, state,
            onRestingTop = { sheetTop = it },
            onLeavingChange = { sheetLeaving = it },
            onEvent = stableOnEvent,
            onDismiss = { sheetFor = null },
        )
    }
}

@Composable
private fun EmptyConversation(firstName: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .semantics(mergeDescendants = true) {}
            .padding(Afsh.spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
    ) {
        Icon(AfshIcon.Chat.painter(), contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.thread_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        if (firstName.isNotEmpty()) {
            MixedLocaleText(stringResource(R.string.thread_empty_text, isolate(firstName)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineEffects(state: ThreadUiState, newestFirst: List<ThreadItem>, listState: LazyListState, onEvent: (ThreadEvent) -> Unit) {
    // Only a scroll (by the user or by us) moves the anchor; new data never does. So a refresh that
    // lands while the reader is up in the history leaves them there.
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.viewport() }
            .distinctUntilChanged()
            .collect { (scrolling, atBottom, newestVisible) ->
                // Leaving the bottom unsticks at once, so a message arriving mid-fling does not yank
                // the reader back; only coming to rest at the bottom sticks again.
                if (!atBottom) stickToBottom = false else if (!scrolling) stickToBottom = true
                onEvent(ThreadEvent.Viewport(atBottom = atBottom, newestVisible = newestVisible, scrolling = scrolling))
            }
    }
    // Applied in this frame's measure pass, so a message that arrives while the reader is at the
    // bottom is shown at once instead of being kept just below the viewport by the key anchor.
    val newestKey = newestFirst.firstOrNull()?.key()
    val seenNewest = remember { longArrayOf(Long.MIN_VALUE) }
    SideEffect {
        if (newestKey != null && newestKey != seenNewest[0]) {
            seenNewest[0] = newestKey
            if (stickToBottom) listState.requestScrollToItem(0)
        }
    }
    // Read state is decided in the activity from the viewport; re-evaluate once new items are laid out.
    LaunchedEffect(state.items) {
        awaitFrame()
        val (scrolling, atBottom, newestVisible) = listState.viewport()
        onEvent(ThreadEvent.Viewport(atBottom = atBottom, newestVisible = newestVisible, scrolling = scrolling))
    }
    LaunchedEffect(listState, newestFirst.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { top -> if (newestFirst.isNotEmpty() && newestFirst.size - 1 - top <= LOAD_OLDER_THRESHOLD) onEvent(ThreadEvent.LoadOlder) }
    }
    val initial = state.initialScroll
    LaunchedEffect(initial) {
        initial ?: return@LaunchedEffect
        // Settles even when a touch or a new message cancels the scroll, or read state stays blocked.
        try {
            awaitFrame()
            when (initial) {
                InitialScroll.FirstUnread -> {
                    val index = newestFirst.indexOfFirst { it is ThreadItem.ThreadUnreadSeparator }
                    if (index >= 0 && !listState.isFullyVisible(index)) listState.scrollItemToTop(index)
                }

                is InitialScroll.Message -> {
                    val index = newestFirst.indexOfFirst { it is Message && it.id == initial.messageId && it.isMMS == initial.isMms }
                    if (index >= 0) listState.scrollItemToTop(index)
                }
            }
        } finally {
            onEvent(ThreadEvent.InitialScrollSettled)
        }
    }
    val request = state.scrollRequest
    LaunchedEffect(request) {
        when (request) {
            null -> Unit
            is ScrollRequest.Bottom -> if (request.smooth) listState.animateScrollToItem(0) else listState.scrollToItem(0)
            is ScrollRequest.ToMessage -> {
                // Also when a newer request cancels this one: the activity holds page loads and read
                // state until it hears back.
                try {
                    val index = newestFirst.indexOfFirst { it is Message && request.matches(it) }
                    if (index >= 0) {
                        listState.animateScrollToItem(index)
                        listState.scrollItemToTop(index, centre = true)
                    }
                } finally {
                    onEvent(ThreadEvent.JumpSettled(request.nonce))
                }
            }
        }
    }
}

private suspend fun awaitFrame() = withFrameNanos { }

/** Scrolling, at the very bottom, and the newest message (index 0 of the reversed list) on screen. */
private fun LazyListState.viewport(): Triple<Boolean, Boolean, Boolean> =
    Triple(isScrollInProgress, !canScrollBackward, layoutInfo.visibleItemsInfo.firstOrNull()?.index == 0)

// Offsets in a reversed list are measured from the bottom edge, so an item's top edge is at
// viewportEndOffset - (offset + size) from the top.
private fun LazyListState.isFullyVisible(index: Int): Boolean {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return info.offset >= layoutInfo.viewportStartOffset && info.offset + info.size <= layoutInfo.viewportEndOffset
}

private suspend fun LazyListState.scrollItemToTop(index: Int, centre: Boolean = false) {
    scrollToItem(index)
    awaitFrame()
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewport = layoutInfo.viewportEndOffset - info.offset
    val shift = if (centre) (viewport - info.size) / 2 else viewport - info.size
    if (shift > 0) scrollBy(-shift.toFloat())
}

private fun ThreadItem.key(): Long = when (this) {
    is Message -> getStableId()
    is ThreadItem.ThreadDateTime -> generateStableId(THREAD_DATE_TIME, date.toLong())
    is ThreadItem.ThreadUnreadSeparator -> generateStableId(THREAD_UNREAD_SEPARATOR, 0)
    // Filtered out before display.
    is ThreadItem.ThreadError, is ThreadItem.ThreadSending, is ThreadItem.ThreadSent -> 0L
}

private fun Message.isUnreadIncoming() = !read && isReceivedMessage() && !isScheduled

@Composable
private fun JumpToLatest(state: ThreadUiState, newestFirst: List<ThreadItem>, listState: LazyListState, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val unread = remember(newestFirst) { newestFirst.count { it is Message && it.isUnreadIncoming() && state.spamReason(it) == null } }
    val show by remember(newestFirst) {
        derivedStateOf {
            val bottom = listState.firstVisibleItemIndex
            listState.canScrollBackward && (bottom >= JUMP_BUTTON_ITEM_LIMIT ||
                (0 until bottom.coerceAtMost(newestFirst.size)).any { (newestFirst[it] as? Message)?.isUnreadIncoming() == true })
        }
    }
    val description = if (unread > 0) {
        stringResource(R.string.scroll_to_unread_messages, unread)
    } else {
        stringResource(R.string.scroll_to_latest_message)
    }
    AnimatedVisibility(show && !state.selecting, modifier.padding(Afsh.spacing.lg), enter = fadeIn(), exit = fadeOut()) {
        FloatingButton(
            AfshIcon.ArrowDown,
            description = description,
            onClick = { onEvent(ThreadEvent.JumpToLatest) },
            modifier = Modifier.testTag(THREAD_JUMP_TAG),
            text = if (unread > 0) pluralStringResource(R.plurals.thread_new_count, unread, unread) else null,
            secondary = true,
        )
    }
}

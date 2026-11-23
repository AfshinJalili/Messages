package com.afsh.messages.ui.thread

import com.afsh.messages.ui.components.FloatingButton
import com.afsh.messages.ui.components.UnreadDivider
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import com.afsh.messages.ui.components.AfshSnackbarHost
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import android.net.Uri
import android.provider.Telephony
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.util.LinkifyCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.afsh.commons.extensions.formatSize
import com.afsh.commons.extensions.formatTime
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.R
import com.afsh.messages.ui.components.DatePill
import com.afsh.messages.ui.components.BarEnter
import com.afsh.messages.ui.components.BarExit
import com.afsh.messages.ui.components.SELECTION_ENTER_MS
import com.afsh.messages.ui.components.SELECTION_EXIT_MS
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.SelectionBar
import com.afsh.messages.ui.components.selectionTitle
import com.afsh.messages.ui.components.SelectableBubbleRow
import com.afsh.messages.ui.components.longPressToSelect
import com.afsh.messages.ui.components.keptWhileLeaving
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.components.BubbleTone
import com.afsh.messages.ui.components.MessageBubble
import com.afsh.messages.ui.components.role
import com.afsh.messages.extensions.getFileSizeFromUri
import com.afsh.messages.extensions.isVCardMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.helpers.THREAD_DATE_TIME
import com.afsh.messages.helpers.THREAD_UNREAD_SEPARATOR
import com.afsh.messages.helpers.ThreadDates
import com.afsh.messages.helpers.generateStableId
import com.afsh.messages.helpers.attachmentIcon
import com.afsh.messages.helpers.parseNameFromVCard
import com.afsh.messages.helpers.parseVCardFromUri
import com.afsh.messages.ui.components.upcomingDayAndTime
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.models.spamReasonLabel
import com.afsh.messages.ui.Avatar
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.ContentDirection
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.MixedLocaleText
import com.afsh.messages.ui.rememberBitmap
import kotlin.coroutines.resume

// Same limits the View timeline used.
private const val JUMP_BUTTON_ITEM_LIMIT = 20
private const val LOAD_OLDER_THRESHOLD = 45
private const val GROUPING_WINDOW_SECS = 60
private const val MAX_MEDIA_HEIGHT_RATIO = 3
private const val STICKY_DATE_HIDE_DELAY_MS = 700L
private const val STICKY_DATE_FADE_MS = 180
// R6-48: the lift is a spring of its own, not tied to the sheet's slide, so the bubble glides while the sheet moves.
// Its stiffness is the sheet's (Material 3's default spatial spring, 700), so the bubble and the sheet arrive and
// leave together, in about the 250-300 ms the app's other panels and screens take. It was 400, which trailed the sheet.
private const val LIFT_STIFFNESS = 700f
private val LiftSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = LIFT_STIFFNESS, visibilityThreshold = 0.001f)
private val LiftElevation = 6.dp
private val LiftFade = 32.dp
private val TargetSize = 48.dp

const val THREAD_LIST_TAG = "thread_list"
const val THREAD_JUMP_TAG = "thread_jump_to_latest"
const val THREAD_STICKY_DATE_TAG = "thread_sticky_date"

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
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTop by remember { mutableFloatStateOf(Float.NaN) }
    val openSheet = remember {
        { message: Message, groupedBelow: Boolean, coordinates: LayoutCoordinates ->
            lifted = LiftedBubble(message, groupedBelow, Rect(coordinates.positionOnScreen(), coordinates.size.toSize()))
            sheetTop = Float.NaN
            sheetOpen = true
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
                    up = sheetOpen,
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
    val lift = lifted
    if (sheetOpen && lift != null) {
        MessageSheet(lift.message, state, onRestingTop = { sheetTop = it }, onEvent = stableOnEvent, onDismiss = { sheetOpen = false })
    }
}

/** R6-48: the tapped bubble, where it sat on screen, and whether it ends a run (so shows its tail and time). */
private class LiftedBubble(val message: Message, val groupedBelow: Boolean, val bounds: Rect)

/**
 * R6-48: dims the timeline and floats a copy of the tapped bubble, without scrolling, then returns it to its slot.
 * It moves only as far as it must to clear the sheet's resting top by 12 dp, and it glides there on its own spring:
 * it never follows the sheet's edge while the sheet slides in or out. The sheet's own scrim is off; onDim lets the
 * host dim the header views outside this one. A bubble taller than the free space shows its top, fading out at the bottom.
 */
/** Hoisted once in [ThreadTimeline] so scrolling bubbles do not resolve strings per row. */
@Immutable
private data class BubbleStrings(
    val selectLabel: String,
    val copyLabel: String,
    val forwardLabel: String,
    val starLabel: String,
    val unstarLabel: String,
    val deleteLabel: String,
    val notSentLabel: String,
    val spamLabel: String,
    val scheduledLabel: String,
    val deliveredLabel: String,
    val sentLabel: String,
    val starredLabel: String,
    val sendingLabel: String,
    val retryLabel: String,
)

@Composable
private fun rememberBubbleStrings(): BubbleStrings {
    val selectLabel = stringResource(R.string.inbox_select)
    val copyLabel = stringResource(MessageAction.COPY.label)
    val forwardLabel = stringResource(MessageAction.FORWARD.label)
    val starLabel = stringResource(MessageAction.STAR.label)
    val unstarLabel = stringResource(MessageAction.UNSTAR.label)
    val deleteLabel = stringResource(MessageAction.DELETE.label)
    val notSentLabel = stringResource(R.string.message_not_sent_short)
    val spamLabel = stringResource(R.string.inbox_spam)
    val scheduledLabel = stringResource(R.string.scheduled_message)
    val deliveredLabel = stringResource(R.string.message_delivered)
    val sentLabel = stringResource(R.string.message_sent)
    val starredLabel = stringResource(R.string.starred_messages)
    val sendingLabel = stringResource(R.string.sending)
    val retryLabel = stringResource(R.string.thread_retry)
    return remember(
        selectLabel, copyLabel, forwardLabel, starLabel, unstarLabel, deleteLabel,
        notSentLabel, spamLabel, scheduledLabel, deliveredLabel, sentLabel,
        starredLabel, sendingLabel, retryLabel,
    ) {
        BubbleStrings(
            selectLabel = selectLabel,
            copyLabel = copyLabel,
            forwardLabel = forwardLabel,
            starLabel = starLabel,
            unstarLabel = unstarLabel,
            deleteLabel = deleteLabel,
            notSentLabel = notSentLabel,
            spamLabel = spamLabel,
            scheduledLabel = scheduledLabel,
            deliveredLabel = deliveredLabel,
            sentLabel = sentLabel,
            starredLabel = starredLabel,
            sendingLabel = sendingLabel,
            retryLabel = retryLabel,
        )
    }
}

private val NoOpThreadEvent: (ThreadEvent) -> Unit = {}

@Composable
private fun LiftOverlay(
    lift: LiftedBubble,
    up: Boolean,
    sheetTop: Float,
    selecting: Boolean,
    selected: Boolean,
    starred: Boolean,
    spamReason: Int?,
    simLabel: String?,
    sendSim: Int,
    isRecycleBin: Boolean,
    strings: BubbleStrings,
    onDim: (Float) -> Unit,
    onGone: () -> Unit,
) {
    val progress = remember(lift) { Animatable(0f) }
    LaunchedEffect(progress) { snapshotFlow { progress.value }.collect(onDim) }
    LaunchedEffect(lift, up) {
        progress.animateTo(if (up) 1f else 0f, LiftSpring)
        if (!up) onGone()
    }
    val density = LocalDensity.current
    val liftGap = Afsh.spacing.md
    val scrim = Afsh.colors.liftScrim
    var origin by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .drawBehind { drawRect(scrim, alpha = progress.value) }
            .clearAndSetSemantics {},
        // Bounds are absolute screen positions; a start-aligned child would sit at the right edge in RTL.
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val at = origin ?: return@Box
        val slot = lift.bounds.top - at.y
        val full = lift.bounds.height
        val gap = with(density) { liftGap.toPx() }
        // sheetTop is where the sheet comes to rest, not its moving edge. Until it is known, the bubble stays in its slot.
        val free = if (sheetTop.isNaN()) slot + full else sheetTop - at.y - gap
        val shown = minOf(full, free.coerceAtLeast(0f))
        // Stays put when the sheet leaves it in view; a bubble cut off at the top comes down to the gap.
        val target = minOf(maxOf(slot, gap), free - shown)
        val height = full + (shown - full) * progress.value
        val top = slot + (target - slot) * progress.value
        val clipped = height < full - 1f
        val incoming = lift.message.isReceivedMessage()
        val fade = with(density) { LiftFade.toPx() }
        val mask = Afsh.colors.mask
        val clear = Afsh.colors.transparent
        Box(
            Modifier
                .absoluteOffset { IntOffset((lift.bounds.left - at.x).roundToInt(), top.roundToInt()) }
                .size(with(density) { lift.bounds.width.toDp() }, with(density) { height.toDp() })
                .then(
                    if (clipped) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(0f to mask, 1f to clear, startY = size.height - fade, endY = size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .shadow(LiftElevation * progress.value, messageBubbleShape(incoming, lift.groupedBelow), clip = false),
            ) {
                Bubble(
                    message = lift.message,
                    selecting = selecting,
                    selected = selected,
                    starred = starred,
                    spamReason = spamReason,
                    simLabel = simLabel,
                    sendSim = sendSim,
                    isRecycleBin = isRecycleBin,
                    strings = strings,
                    onEvent = NoOpThreadEvent,
                    groupedBelow = lift.groupedBelow,
                )
            }
        }
    }
}

/** R6-48 (screens 97, 98): date · time · SIM, then the actions, Delete in the error colour. */
@Composable
private fun MessageSheet(message: Message, state: ThreadUiState, onRestingTop: (Float) -> Unit, onEvent: (ThreadEvent) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sim = state.simLabels[message.subscriptionId]?.let { stringResource(R.string.message_sim_label, it) }
    val info = remember(message.date, message.isScheduled, sim) {
        val millis = message.date * 1000L
        if (message.isScheduled) {
            // 84: when it goes, rather than when it was written.
            context.getString(R.string.scheduled_for, listOfNotNull(upcomingDayAndTime(context, millis), sim).joinToString(" · "))
        } else {
            listOfNotNull(DateFormat.format("EEE d MMM yyyy", millis).toString(), millis.formatTime(context), sim).joinToString(" · ")
        }
    }
    AfshSheet(
        title = info,
        onDismiss = onDismiss,
        showTitle = false,
        // The lifted bubble dims the page itself.
        dimBehind = false,
        // The sheet rests against the bottom of its full-window dialog, so its resting top is known from the first
        // frame, while it is still sliding in. The lifted bubble aims there instead of chasing the moving edge.
        modifier = Modifier.onGloballyPositioned {
            val window = it.findRootCoordinates()
            onRestingTop(window.positionOnScreen().y + window.size.height - it.size.height)
        },
    ) {
        Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Afsh.spacing.xxl, vertical = Afsh.spacing.md))
        HorizontalDivider(Modifier.padding(horizontal = Afsh.spacing.xl), color = colors.outlineVariant)
        tapActions(message, state).forEach { action ->
            AfshSheetItem(
                label = stringResource(action.label),
                icon = action.icon,
                danger = action == MessageAction.DELETE,
                // Act once the sheet is gone, so a dialog or the forward screen does not open under it.
                onClick = { dismissThen { onEvent(ThreadEvent.Act(action, listOf(message))) } },
            )
        }
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
            MixedLocaleText(stringResource(R.string.thread_empty_text, firstName), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineEffects(state: ThreadUiState, newestFirst: List<ThreadItem>, listState: LazyListState, onEvent: (ThreadEvent) -> Unit) {
    // Only a scroll (by the user or by us) moves the anchor; new data never does. So a refresh that
    // lands while the reader is up in the history leaves them there.
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to !listState.canScrollBackward }
            .distinctUntilChanged()
            .collect { (scrolling, atBottom) ->
                // Leaving the bottom unsticks at once, so a message arriving mid-fling does not yank
                // the reader back; only coming to rest at the bottom sticks again.
                if (!atBottom) stickToBottom = false else if (!scrolling) stickToBottom = true
                onEvent(ThreadEvent.Viewport(atBottom = atBottom, scrolling = scrolling))
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
        onEvent(ThreadEvent.Viewport(atBottom = !listState.canScrollBackward, scrolling = listState.isScrollInProgress))
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

private fun groupedWith(message: Message, other: ThreadItem?) = other is Message &&
    other.isReceivedMessage() == message.isReceivedMessage() &&
    other.senderPhoneNumber == message.senderPhoneNumber &&
    kotlin.math.abs(other.date - message.date) <= GROUPING_WINDOW_SECS

@Composable
private fun MessageRow(
    message: Message,
    older: ThreadItem?,
    newer: ThreadItem?,
    selecting: Boolean,
    selected: Boolean,
    starred: Boolean,
    spamReason: Int?,
    simLabel: String?,
    sendSim: Int,
    isGroup: Boolean,
    isRecycleBin: Boolean,
    hidden: Boolean,
    strings: BubbleStrings,
    onEvent: (ThreadEvent) -> Unit,
    onOpen: (Message, Boolean, LayoutCoordinates) -> Unit,
) {
    val incoming = message.isReceivedMessage()
    val groupedAbove = groupedWith(message, older)
    val topGap = when {
        older is ThreadItem.ThreadDateTime -> 0.dp
        groupedAbove -> Afsh.spacing.xs
        else -> Afsh.spacing.md
    }
    val onToggle = remember(message, onEvent) { { onEvent(ThreadEvent.ToggleSelection(message)) } }
    SelectableBubbleRow(
        selecting = selecting,
        checked = selected,
        incoming = incoming,
        onToggle = onToggle,
        modifier = Modifier.padding(start = Afsh.spacing.lg, end = Afsh.spacing.lg, top = topGap),
        above = if (incoming && isGroup && !groupedAbove && message.senderName.isNotEmpty()) {
            { shift ->
                ContentText(
                    message.senderName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = shift.padding(start = Afsh.spacing.xs, bottom = Afsh.spacing.xs),
                )
            }
        } else {
            null
        },
    ) {
        val groupedBelow = groupedWith(message, newer)
        val openBubble = remember(message, groupedBelow, onOpen) {
            { coords: LayoutCoordinates -> onOpen(message, groupedBelow, coords) }
        }
        Bubble(
            message = message,
            selecting = selecting,
            selected = selected,
            starred = starred,
            spamReason = spamReason,
            simLabel = simLabel,
            sendSim = sendSim,
            isRecycleBin = isRecycleBin,
            strings = strings,
            onEvent = onEvent,
            groupedBelow = groupedBelow,
            hidden = hidden,
            onOpen = openBubble,
        )
    }
}

private fun isRtlParagraph(text: CharSequence): Boolean {
    for (i in text.indices) {
        when (Character.getDirectionality(text[i])) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
    }
    return false
}

@Composable
private fun Bubble(
    message: Message,
    selecting: Boolean,
    selected: Boolean,
    starred: Boolean,
    spamReason: Int?,
    simLabel: String?,
    sendSim: Int,
    isRecycleBin: Boolean,
    strings: BubbleStrings,
    onEvent: (ThreadEvent) -> Unit,
    groupedBelow: Boolean,
    hidden: Boolean = false,
    onOpen: ((LayoutCoordinates) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val incoming = message.isReceivedMessage()
    val failed = message.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val tone = BubbleTone.of(incoming, failed, selected)
    val content = tone.role().content
    val shape = messageBubbleShape(incoming, groupedBelow)
    val attachments = message.attachment?.attachments.orEmpty()
    val fullWidth = attachments.isNotEmpty() || failed
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hasBody = message.body.isNotEmpty()
    val accessibleActions = remember(message.id, message.isMMS, hasBody, starred, isRecycleBin, strings, onEvent) {
        buildList {
            if (hasBody) {
                add(CustomAccessibilityAction(strings.copyLabel) { onEvent(ThreadEvent.Act(MessageAction.COPY, listOf(message))); true })
            }
            add(CustomAccessibilityAction(strings.forwardLabel) { onEvent(ThreadEvent.Act(MessageAction.FORWARD, listOf(message))); true })
            if (!isRecycleBin) {
                val starText = if (starred) strings.unstarLabel else strings.starLabel
                val starAction = if (starred) MessageAction.UNSTAR else MessageAction.STAR
                add(CustomAccessibilityAction(starText) { onEvent(ThreadEvent.Act(starAction, listOf(message))); true })
            }
            add(CustomAccessibilityAction(strings.deleteLabel) { onEvent(ThreadEvent.Act(MessageAction.DELETE, listOf(message))); true })
        }
    }
    val onCopy = remember(onEvent) { { value: String -> onEvent(ThreadEvent.CopyText(value)) } }
    // Hidden while its copy sits lifted above the sheet.
    Box(Modifier.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
        MessageBubble(
            tone,
            shape,
            Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .onGloballyPositioned { coordinates[0] = it }
                .clip(shape)
                .longPressToSelect(strings.selectLabel) { onEvent(ThreadEvent.ToggleSelection(message)) }
                .clickable(enabled = onOpen != null) {
                    if (selecting) {
                        onEvent(ThreadEvent.ToggleSelection(message))
                    } else {
                        coordinates[0]?.takeIf { it.isAttached }?.let { onOpen?.invoke(it) }
                    }
                }
                .semantics {
                    if (selecting) this.selected = selected
                    customActions = accessibleActions
                },
        ) {
            attachments.forEach { attachment ->
                AttachmentView(attachment) {
                    if (selecting) onEvent(ThreadEvent.ToggleSelection(message)) else onEvent(ThreadEvent.OpenAttachment(message, attachment))
                }
            }
            if (hasBody) {
                val textModifier = if (fullWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.align(if (isRtlParagraph(message.body)) AbsoluteAlignment.Right else AbsoluteAlignment.Left)
                }
                MessageText(
                    body = message.body,
                    color = content,
                    linkColor = colors.primary,
                    links = !selecting,
                    modifier = textModifier,
                    onCopy = onCopy,
                )
            }
            // Never hide a failure, in-flight send, scheduled time, star, or spam explanation in a run.
            if (!groupedBelow || failed || message.isScheduled || message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || starred || spamReason != null) {
                Metadata(
                    message = message,
                    selected = selected,
                    starred = starred,
                    spamReason = spamReason,
                    simLabel = simLabel,
                    strings = strings,
                    content = content,
                    modifier = Modifier.align(if (failed) Alignment.Start else Alignment.End),
                )
            }
            if (failed) {
                // R6-46: Retry sends on the SIM shown. The switcher beside it is the composer's glyph
                // and only changes that SIM. The words "سیم ۱" wrap once the font is large.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
                ) {
                    Button(
                        onClick = { onEvent(ThreadEvent.Retry(message)) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceContainerLowest, contentColor = colors.primary),
                        contentPadding = PaddingValues(horizontal = Afsh.spacing.lg),
                        modifier = Modifier.heightIn(min = TargetSize),
                    ) {
                        Text(
                            strings.retryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (sendSim > 0) {
                        SimSwitcher(sendSim, carrier = "", onClick = { onEvent(ThreadEvent.NextSim) })
                    }
                }
            }
        }
    }
}

// The tail corner marks the last bubble of a run, on the sender's side.
/** Web links and email addresses open; standalone numbers copy, as in the View timeline. */
@Composable
internal fun MessageText(
    body: String,
    color: Color,
    linkColor: Color,
    links: Boolean,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit,
) {
    val currentOnCopy by rememberUpdatedState(onCopy)
    val detected = remember(body, links) { if (links) detectLinks(body) else emptyList() }
    // Each paragraph takes its direction from its first letter, whatever the app language, and one
    // with no letters (a number, emoji) reads left to right, as in Telegram and WhatsApp.
    if (detected.isEmpty()) {
        ContentText(body, style = MaterialTheme.typography.bodyLarge, color = color, modifier = modifier, direction = ContentDirection.Text)
    } else {
        val text = remember(body, detected, linkColor) {
            buildLinkifiedText(body, detected, linkColor) { currentOnCopy(it) }
        }
        ContentText(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = modifier, direction = ContentDirection.Text)
    }
}

private data class DetectedLink(val start: Int, val end: Int, val value: String, val isUrl: Boolean)

private val linkScanCache = object : android.util.LruCache<String, List<DetectedLink>>(512) {}

private fun detectLinks(body: String): List<DetectedLink> {
    if (body.isEmpty()) return emptyList()
    linkScanCache.get(body)?.let { return it }
    var hasUrlHint = false
    var hasDigit = false
    for (i in body.indices) {
        val c = body[i]
        if (c == '.' || c == '@' || c == ':') hasUrlHint = true
        else if (c.isDigit()) hasDigit = true
        if (hasUrlHint && hasDigit) break
    }
    if (!hasUrlHint && !hasDigit) {
        linkScanCache.put(body, emptyList())
        return emptyList()
    }
    val urls = if (hasUrlHint) {
        val spannable = SpannableString(body)
        LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        spannable.getSpans(0, body.length, URLSpan::class.java)
            .map { DetectedLink(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url, isUrl = true) }
    } else {
        emptyList()
    }
    val numbers = if (hasDigit) {
        NUMBER.findAll(body)
            .filter { match -> urls.none { match.range.first < it.end && match.range.last >= it.start } }
            .map { DetectedLink(it.range.first, it.range.last + 1, it.value, isUrl = false) }
            .toList()
    } else {
        emptyList()
    }
    val combined = when {
        urls.isEmpty() -> numbers
        numbers.isEmpty() -> urls
        else -> (urls + numbers).sortedBy { it.start }
    }
    linkScanCache.put(body, combined)
    return combined
}

// A match is the whole number or nothing. Isolating only part of it, as "000,000" out of
// "مانده70,000,000", leaves a separator outside the isolate and the groups read "000,000,70".
private const val NUMBER_SEPARATOR = "[ .,:/()\\-\u066B\u066C]"
private val NUMBER = Regex(
    "(?<![\\p{L}\\p{N}]|\\p{N}$NUMBER_SEPARATOR)\\+?\\p{Nd}+(?:$NUMBER_SEPARATOR\\p{Nd}+)*(?![\\p{L}\\p{N}]|$NUMBER_SEPARATOR\\p{N})"
)

private fun buildLinkifiedText(
    body: String,
    links: List<DetectedLink>,
    linkColor: Color,
    onCopy: (String) -> Unit,
): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var cursor = 0
        links.forEach { (start, end, value, isUrl) ->
            append(body, cursor, start)
            val link = if (isUrl) {
                LinkAnnotation.Url(value, style)
            } else {
                LinkAnnotation.Clickable(value, style) { onCopy(value) }
            }
            // Isolated left to right, so a spaced phone number or a URL keeps its order inside Persian text.
            append('\u2066')
            withLink(link) { append(body, start, end) }
            append('\u2069')
            cursor = end
        }
        append(body, cursor, body.length)
    }
}

internal fun linkify(body: String, linkColor: Color = Color.Unspecified, onCopy: (String) -> Unit): AnnotatedString {
    val links = detectLinks(body)
    return if (links.isEmpty()) AnnotatedString(body) else buildLinkifiedText(body, links, linkColor, onCopy)
}

@Composable
private fun Metadata(
    message: Message,
    selected: Boolean,
    starred: Boolean,
    spamReason: Int?,
    simLabel: String?,
    strings: BubbleStrings,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val incoming = message.isReceivedMessage()
    val metaColor = when {
        message.type == Telephony.Sms.MESSAGE_TYPE_FAILED -> content
        incoming || selected -> colors.onSurfaceVariant
        else -> Afsh.colors.onBandVariant
    }
    val time = remember(message.date) { (message.date * 1000L).formatTime(context) }
    val failed = message.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    // R6-47: the clock alone says sending, where the tick will go; TalkBack still hears the word.
    val sending = message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX && !message.isScheduled
    val spamKind = spamReason?.let { stringResource(spamReasonLabel(it)) }
    val simPart = simLabel?.let { stringResource(R.string.message_sim_label, it) }
    val parts = buildList {
        if (failed) add(strings.notSentLabel)
        if (spamKind != null) {
            add(strings.spamLabel)
            add(spamKind)
        }
        if (!sending) add(time)
        if (simPart != null) add(simPart)
        if (message.isScheduled) add(strings.scheduledLabel)
    }
    val delivered = message.status == Telephony.Sms.STATUS_COMPLETE
    val sent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT && !message.isScheduled
    val statusLabel = if (sent) (if (delivered) strings.deliveredLabel else strings.sentLabel) else null
    val sendingLabel = if (sending) strings.sendingLabel else null
    val description = (listOfNotNull(strings.starredLabel.takeIf { starred }) + parts + listOfNotNull(statusLabel, sendingLabel)).joinToString(", ")
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        // Failed status wraps on narrow / Persian / large text; top-align with the warning icon.
        verticalAlignment = if (failed) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
    ) {
        if (starred) Icon(AfshIcon.Star.painter(filled = true), null, tint = metaColor, modifier = Modifier.size(14.dp))
        when {
            message.isScheduled ->
                Icon(AfshIcon.Schedule.painter(), null, tint = metaColor, modifier = Modifier.size(14.dp))

            failed -> Icon(AfshIcon.Warning.painter(), null, tint = colors.error, modifier = Modifier.size(18.dp))
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (message.isScheduled) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (failed) FontWeight.Bold else null,
                color = metaColor,
            )
        }
        if (sending) {
            Icon(AfshIcon.Schedule.painter(), null, tint = metaColor, modifier = Modifier.size(14.dp))
        } else if (sent) {
            val icon = if (delivered) AfshIcon.CheckAll else AfshIcon.Check
            Icon(icon.painter(), null, tint = metaColor, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AttachmentView(attachment: Attachment, onClick: () -> Unit) {
    val mimetype = attachment.mimetype
    // Long press belongs to the bubble, which selects the whole message.
    val modifier = Modifier.clickable(onClickLabel = attachment.filename.ifEmpty { null }, onClick = onClick)
    when {
        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> ImageAttachment(attachment, modifier)
        mimetype.isVCardMimeType() -> VCardAttachment(attachment, modifier)
        else -> FileAttachment(attachment, modifier)
    }
}

@Composable
private fun ImageAttachment(attachment: Attachment, clickable: Modifier) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val bitmap = rememberBitmap(attachment.uriString, widthPx, widthPx * MAX_MEDIA_HEIGHT_RATIO)
        // Known dimensions reserve the right height before the bitmap arrives, so the list doesn't jump.
        val ratio = when {
            bitmap != null -> bitmap.width.toFloat() / bitmap.height
            attachment.width > 0 && attachment.height > 0 -> attachment.width.toFloat() / attachment.height
            else -> null
        }?.coerceAtLeast(1f / MAX_MEDIA_HEIGHT_RATIO)
        Surface(
            Modifier
                .fillMaxWidth()
                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.height(180.dp))
                .clip(MaterialTheme.shapes.small)
                .then(clickable),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = attachment.filename.ifEmpty { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                if (attachment.mimetype.isVideoMimeType()) {
                    Icon(
                        AfshIcon.Play.painter(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
    }
}

/** The first contact's name and a "Contact" / "and N others" line; empty until the card is parsed. */
@Composable
internal fun rememberVCardSummary(uri: Uri): Pair<String, String> {
    val context = LocalContext.current
    val unknown = stringResource(com.afsh.commons.R.string.unknown_error_occurred)
    val summary by produceState<Pair<String, Int>?>(null, uri) {
        // Cancellable: the parser calls back on its own thread, possibly after the tile has left.
        value = suspendCancellableCoroutine { continuation ->
            parseVCardFromUri(context, uri) { cards ->
                if (continuation.isActive) {
                    continuation.resume((cards.firstOrNull()?.parseNameFromVCard() ?: unknown) to (cards.size - 1).coerceAtLeast(0))
                }
            }
        }
    }
    val (name, others) = summary ?: return "" to ""
    return name to if (others > 0) pluralStringResource(R.plurals.and_other_contacts, others, others) else stringResource(R.string.contact)
}

@Composable
internal fun rememberFileSize(uri: Uri): String {
    val context = LocalContext.current
    val size by produceState("", uri) {
        value = withContext(Dispatchers.IO) { runCatching { context.getFileSizeFromUri(uri).formatSize() }.getOrDefault("") }
    }
    return size
}

@Composable
private fun VCardAttachment(attachment: Attachment, clickable: Modifier) {
    val (name, subtitle) = rememberVCardSummary(attachment.getUri())
    AttachmentTile(AfshIcon.Person, name, subtitle, clickable)
}

@Composable
private fun FileAttachment(attachment: Attachment, clickable: Modifier) {
    val size = rememberFileSize(attachment.getUri())
    AttachmentTile(attachmentIcon(attachment.mimetype), attachment.filename.ifEmpty { attachment.mimetype }, size, clickable)
}

@Composable
private fun AttachmentTile(icon: AfshIcon, title: String, subtitle: String, clickable: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .then(clickable),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(Afsh.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = Afsh.colors.badge.container, contentColor = Afsh.colors.badge.content) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon.painter(), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                ContentText(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DatePill(seconds: Int, modifier: Modifier = Modifier) {
    val label = remember(seconds) { ThreadDates.label(seconds) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DatePill(label)
    }
}

/**
 * Follows the top visible day while scrolling, hides while its inline pill is on screen, and is
 * pushed up by the next day's pill. The push is a translation, and the timeline is drawn after the
 * header, so the slot is clipped: otherwise the outgoing pill paints over the title. Fades 700 ms
 * after scrolling stops (design board, dates).
 */
@Immutable
private data class StickyDay(val date: Int, val dayIndex: Int, val inlineShowing: Boolean)

@Composable
private fun StickyDate(newestFirst: List<ThreadItem>, listState: LazyListState, modifier: Modifier = Modifier) {
    val pillHeight = remember { intArrayOf(0) }
    val margin = with(LocalDensity.current) { Afsh.spacing.sm.roundToPx() }
    val sticky by remember(newestFirst) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val top = visible.lastOrNull() ?: return@derivedStateOf null
            val dayIndex = (top.index until newestFirst.size).firstOrNull { newestFirst[it] is ThreadItem.ThreadDateTime }
                ?: return@derivedStateOf null
            val inlineShowing = visible.any { it.index == dayIndex }
            StickyDay((newestFirst[dayIndex] as ThreadItem.ThreadDateTime).date, dayIndex, inlineShowing)
        }
    }
    var scrolledRecently by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            scrolledRecently = true
        } else {
            delay(STICKY_DATE_HIDE_DELAY_MS)
            scrolledRecently = false
        }
    }
    // Keeps the last label while fading out, after the value itself has gone.
    val lastShown = remember { arrayOfNulls<StickyDay>(1) }
    val current = sticky
    if (current != null) lastShown[0] = current
    AnimatedVisibility(
        visible = current != null && !current.inlineShowing && scrolledRecently,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(STICKY_DATE_FADE_MS)),
        modifier = modifier.clipToBounds(),
    ) {
        val shown = lastShown[0] ?: return@AnimatedVisibility
        DatePill(
            remember(shown.date) { ThreadDates.label(shown.date) },
            Modifier
                .padding(top = Afsh.spacing.sm)
                .onSizeChanged { pillHeight[0] = it.height }
                .graphicsLayer {
                    val info = listState.layoutInfo
                    val next = info.visibleItemsInfo
                        .filter { it.index < shown.dayIndex && newestFirst.getOrNull(it.index) is ThreadItem.ThreadDateTime }
                        .maxByOrNull { it.index }
                    val push = next?.let {
                        val nextTop = info.viewportEndOffset - it.offset - it.size
                        minOf(0, nextTop - (pillHeight[0] + margin))
                    } ?: 0
                    translationY = push.toFloat()
                }
                .testTag(THREAD_STICKY_DATE_TAG),
        )
    }
}

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

/**
 * The selection bar, drawn in the composer's frame. Over the composer it fills the frame, so both are the same
 * height and the timeline above never moves: the bar rises over the composer while the composer sinks under it.
 * With no composer (Recycle Bin) it grows from the bottom edge like the other selection bars.
 */
@Composable
fun ThreadSelectionBar(state: ThreadUiState, overComposer: Boolean, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val enter = if (overComposer) slideInVertically(tween(SELECTION_ENTER_MS)) { it } + fadeIn(tween(SELECTION_ENTER_MS)) else BarEnter
    val exit = if (overComposer) slideOutVertically(tween(SELECTION_EXIT_MS)) { it } + fadeOut(tween(SELECTION_EXIT_MS)) else BarExit
    AnimatedVisibility(state.selecting, modifier, enter = enter, exit = exit) {
        MessageSelectionBar(state, onEvent)
    }
}

@Composable
private fun MessageSelectionBar(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit) {
    val actions = remember(state.selected, state.items, state.starred) { selectionActions(state).splitForBar() }
    val (bar, overflow) = keptWhileLeaving(actions, empty = state.selected.isEmpty())
    val selected = state.selectedMessages
    val act = { action: MessageAction -> onEvent(ThreadEvent.Act(action, selected)) }
    SelectionBar(bar.map { it.toBarAction(act) }, overflow = overflow.map { it.toBarAction(act) })
}

@Composable
private fun MessageAction.toBarAction(onAction: (MessageAction) -> Unit) =
    BarAction(stringResource(label), icon, destructive = this == MessageAction.DELETE) { onAction(this) }

private fun previewMessage(id: Long, body: String, incoming: Boolean, minutesAgo: Int, read: Boolean = true, type: Int? = null) = Message(
    id = id,
    body = body,
    type = type ?: if (incoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
    status = Telephony.Sms.STATUS_COMPLETE,
    participants = ArrayList<SimpleContact>(),
    date = ((System.currentTimeMillis() / 1000) - minutesAgo * 60).toInt(),
    read = read,
    threadId = 1,
    isMMS = false,
    attachment = null as MessageAttachment?,
    senderPhoneNumber = if (incoming) "+989120000000" else "",
    senderName = if (incoming) "Mina Farahani" else "",
    senderPhotoUri = "",
    subscriptionId = 1,
)

private val previewState = ThreadUiState(
    header = ThreadHeaderState("Mina Farahani", "+98 912 000 0000", canDial = true, actions = ThreadMenuAction.entries),
    items = listOf(
        ThreadItem.ThreadDateTime(((System.currentTimeMillis() / 1000) - 3 * 3600).toInt()),
        previewMessage(1, "Morning! I’m at the station.", incoming = true, minutesAgo = 180),
        previewMessage(2, "I’ll be there in ten minutes.", incoming = false, minutesAgo = 170),
        previewMessage(3, "Running late, sorry", incoming = false, minutesAgo = 100, type = Telephony.Sms.MESSAGE_TYPE_FAILED),
        ThreadItem.ThreadUnreadSeparator,
        previewMessage(4, "Perfect. The train leaves at six. Code 482913, see https://example.com", incoming = true, minutesAgo = 5, read = false),
    ),
)

@Composable
private fun ThreadPreviewContent(state: ThreadUiState, dark: Boolean) {
    AfshTheme(dark = dark) {
        Column(Modifier.fillMaxSize()) {
            ThreadTopBar(state, {})
            ThreadTimeline(state, remember { SnackbarHostState() }, {}, Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ThreadPreview() = ThreadPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadDarkPreview() = ThreadPreviewContent(previewState, dark = true)

@Preview(widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun ThreadRtlPreview() = ThreadPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ThreadSelectionPreview() = ThreadPreviewContent(previewState.copy(selected = setOf(previewMessage(2, "", false, 0).getStableId())), dark = false)

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun ThreadLandscapePreview() = ThreadPreviewContent(previewState, dark = false)

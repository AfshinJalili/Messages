package org.fossify.messages.ui.components

import android.content.res.Configuration
import android.net.Uri
import android.provider.Telephony
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.text.util.LinkifyCompat
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.formatTime
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.extensions.getFileSizeFromUri
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.helpers.THREAD_DATE_TIME
import org.fossify.messages.helpers.THREAD_SPAM_GROUP
import org.fossify.messages.helpers.THREAD_UNREAD_SEPARATOR
import org.fossify.messages.helpers.ThreadDates
import org.fossify.messages.helpers.generateStableId
import org.fossify.messages.helpers.getIconResourceForMimeType
import org.fossify.messages.helpers.parseNameFromVCard
import org.fossify.messages.helpers.parseVCardFromUri
import org.fossify.messages.helpers.searchRanges
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.spamReasonLabel
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.rememberBitmap
import org.fossify.messages.ui.thread.*
import org.fossify.messages.ui.withContentFonts

// Same limits the View timeline used.
private const val JUMP_BUTTON_ITEM_LIMIT = 20
private const val LOAD_OLDER_THRESHOLD = 45
private const val GROUPING_WINDOW_SECS = 60
private const val BUBBLE_WIDTH_FRACTION = 0.8f
private const val MAX_MEDIA_HEIGHT_RATIO = 3
private const val STICKY_DATE_HIDE_DELAY_MS = 700L
private const val STICKY_DATE_FADE_MS = 180
// R6-48: the lift shares the sheet's curve and length.
private const val LIFT_MS = 300
private val LiftGap = 12.dp
private val LiftElevation = 2.dp
private val LiftFade = 32.dp
private val TargetSize = 48.dp
// Keeps lines readable in landscape and on wide windows.
private val MaxBubbleWidth = 520.dp
private val BubbleCorner = 16.dp
private val BubbleTail = 4.dp

const val THREAD_LIST_TAG = "thread_list"
const val THREAD_JUMP_TAG = "thread_jump_to_latest"
const val THREAD_STICKY_DATE_TAG = "thread_sticky_date"

@Composable
fun ThreadHeader(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val header = state.header
    val compact = LocalConfiguration.current.screenHeightDp < 480
    Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .heightIn(min = if (compact) 56.dp else 72.dp)
                .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.selecting -> {
                    LabeledIconButton(stringResource(org.fossify.commons.R.string.close), onClick = { onEvent(ThreadEvent.ClearSelection) }) {
                        Icon(painterResource(R.drawable.ic_ol_x), contentDescription = null)
                    }
                    Identity(header, pluralStringResource(R.plurals.inbox_selected, state.selected.size, state.selected.size), Modifier.weight(1f))
                }

                state.searching -> {
                    LabeledIconButton(stringResource(org.fossify.commons.R.string.close), onClick = { onEvent(ThreadEvent.SearchClose) }) {
                        Icon(painterResource(R.drawable.ic_ol_arrow_left), contentDescription = null)
                    }
                    SearchField(state, onEvent, Modifier.weight(1f))
                }

                else -> {
                    LabeledIconButton(stringResource(org.fossify.commons.R.string.back), onClick = { onEvent(ThreadEvent.Back) }) {
                        Icon(painterResource(R.drawable.ic_ol_arrow_left), contentDescription = null)
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = TargetSize)
                            .clip(MaterialTheme.shapes.small)
                            .openLineFocus(MaterialTheme.shapes.small)
                            .clickable(enabled = ThreadMenuAction.DETAILS in header.actions) { onEvent(ThreadEvent.OpenDetails) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Identity(header, if (compact) "" else header.subtitle, Modifier.weight(1f))
                    }
                    if (header.canDial) {
                        LabeledIconButton(stringResource(org.fossify.commons.R.string.dial_number), onClick = { onEvent(ThreadEvent.Dial) }) {
                            Icon(painterResource(R.drawable.ic_ol_phone), contentDescription = null)
                        }
                    }
                    if (header.actions.isNotEmpty()) {
                        OverflowMenu(header.actions, onEvent)
                    }
                }
            }
        }
    }
}

/** The label sits on the 48 dp button, not the 24 dp icon, so accessibility tools see one labelled target. */
@Composable
private fun LabeledIconButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    OpenLineIconButton(label, onClick, content = icon)
}

@Composable
private fun Identity(header: ThreadHeaderState, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            remember(header.title) { AnnotatedString(header.title).withContentFonts() },
            style = MaterialTheme.typography.titleLarge.forContent(header.title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OpenLine.colors.onBandVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverflowMenu(actions: List<ThreadMenuAction>, onEvent: (ThreadEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        LabeledIconButton(stringResource(org.fossify.commons.R.string.more_options), onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_ol_ellipsis), contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                    colors = if (action == ThreadMenuAction.DELETE || action == ThreadMenuAction.BLOCK) {
                        androidx.compose.material3.MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        androidx.compose.material3.MenuDefaults.itemColors()
                    },
                    onClick = {
                        open = false
                        onEvent(ThreadEvent.Menu(action))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val query = state.searchQuery
    val searchLabel = stringResource(R.string.search_in_conversation)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(shape = MaterialTheme.shapes.medium, color = androidx.compose.ui.res.colorResource(R.color.open_line_pine_2),
        modifier = modifier.heightIn(min = 56.dp).openLineFocus(MaterialTheme.shapes.medium)) {
        BoxWithConstraints {
            val stackActions = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_ol_search), contentDescription = null, tint = OpenLine.colors.onBandVariant)
                    Box(Modifier.weight(1f).padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_in_conversation), style = MaterialTheme.typography.bodyMedium,
                                color = OpenLine.colors.onBandVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { onEvent(ThreadEvent.SearchChanged(it)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onPrimaryContainer),
                            cursorBrush = SolidColor(OpenLine.colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onEvent(ThreadEvent.SearchSubmit(query)) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .semantics { contentDescription = searchLabel }
                                .onPreviewKeyEvent {
                                    val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                                    if (enter && it.type == KeyEventType.KeyUp && query.isNotBlank()) onEvent(ThreadEvent.SearchSubmit(query))
                                    enter
                                },
                        )
                    }
                    if (query.isNotEmpty() && !stackActions) SearchMatchControls(state, onEvent)
                }
                if (query.isNotEmpty() && stackActions) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) { SearchMatchControls(state, onEvent) }
                }
            }
        }
    }
}

@Composable
private fun SearchMatchControls(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (state.searchMatchCount > 0) Text(
        stringResource(R.string.search_match_position, state.searchMatchPosition, state.searchMatchCount),
        style = MaterialTheme.typography.bodySmall, color = OpenLine.colors.onBandVariant, maxLines = 1,
    )
    LabeledIconButton(stringResource(R.string.search_previous_match), onClick = { onEvent(ThreadEvent.SearchSubmit(state.searchQuery, backwards = true)) }) {
        Icon(painterResource(R.drawable.ic_ol_chevron_down), null, tint = colors.onPrimaryContainer)
    }
    LabeledIconButton(stringResource(R.string.thread_search_next), onClick = { onEvent(ThreadEvent.SearchSubmit(state.searchQuery)) }) {
        Icon(painterResource(R.drawable.ic_ol_chevron_up), null, tint = colors.onPrimaryContainer)
    }
}

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
    TimelineEffects(state, newestFirst, listState) { event(it) }
    var lifted by remember { mutableStateOf<LiftedBubble?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTop by remember { mutableFloatStateOf(Float.NaN) }
    val openSheet = { message: Message, groupedBelow: Boolean, coordinates: LayoutCoordinates ->
        lifted = LiftedBubble(message, groupedBelow, Rect(coordinates.positionOnScreen(), coordinates.size.toSize()))
        sheetTop = Float.NaN
        sheetOpen = true
    }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.Center) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 12.dp),
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
                        is Message -> MessageRow(item, older, newer, state, onEvent, lifted?.message?.getStableId(), openSheet)
                        is ThreadItem.ThreadDateTime -> DatePill(item.date, Modifier.padding(vertical = 12.dp))
                        is ThreadItem.ThreadUnreadSeparator -> UnreadDivider()
                        is ThreadItem.ThreadSpamGroup -> SpamGroupRow(item) { onEvent(ThreadEvent.SpamGroup(item)) }
                        else -> Unit
                    }
                }
            }
            if (state.empty && shown.isEmpty()) EmptyConversation(state.firstName)
            StickyDate(newestFirst, listState, Modifier.align(Alignment.TopCenter))
            JumpToLatest(state, newestFirst, listState, onEvent, Modifier.align(Alignment.BottomEnd))
            OpenLineSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            lifted?.let { lift -> LiftOverlay(lift, sheetOpen, sheetTop, state, onDim, onGone = { if (lifted === lift) lifted = null }) }
        }
        if (state.selecting) {
            SelectionBar(state, onEvent)
        }
    }
    val lift = lifted
    if (sheetOpen && lift != null) {
        MessageSheet(lift.message, state, onTop = { sheetTop = it }, onEvent = onEvent, onDismiss = { sheetOpen = false })
    }
}

/** R6-48: the tapped bubble, where it sat on screen, and whether it ends a run (so shows its tail and time). */
private class LiftedBubble(val message: Message, val groupedBelow: Boolean, val bounds: Rect)

/**
 * R6-48: dims the timeline and lifts a copy of the tapped bubble to 12 dp above the sheet, without scrolling, then
 * returns it to its slot. The sheet's own scrim is off; onDim lets the host dim the header views outside this one. A bubble taller than
 * the free space shows its top, fading out at the bottom.
 */
@Composable
private fun LiftOverlay(lift: LiftedBubble, up: Boolean, sheetTop: Float, state: ThreadUiState, onDim: (Float) -> Unit, onGone: () -> Unit) {
    val progress = remember(lift) { Animatable(0f) }
    LaunchedEffect(progress) { snapshotFlow { progress.value }.collect(onDim) }
    LaunchedEffect(lift, up) {
        progress.animateTo(if (up) 1f else 0f, tween(LIFT_MS))
        if (!up) onGone()
    }
    val density = LocalDensity.current
    val scrim = MaterialTheme.colorScheme.scrim
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
        // Until the sheet reports where it is, the bubble stays in its slot.
        val free = if (sheetTop.isNaN()) slot + full else sheetTop - at.y - with(density) { LiftGap.toPx() }
        val shown = minOf(full, free.coerceAtLeast(0f))
        val height = full + (shown - full) * progress.value
        val top = slot + (free - shown - slot) * progress.value
        val clipped = height < full - 1f
        val incoming = lift.message.isReceivedMessage()
        val fade = with(density) { LiftFade.toPx() }
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
                                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
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
                    .shadow(LiftElevation * progress.value, bubbleShape(incoming, lift.groupedBelow), clip = false),
            ) {
                Bubble(lift.message, state, onEvent = {}, groupedBelow = lift.groupedBelow)
            }
        }
    }
}

/** R6-48 (screens 97, 98): date · time · SIM, then the actions, Delete in the error colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSheet(message: Message, state: ThreadUiState, onTop: (Float) -> Unit, onEvent: (ThreadEvent) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val sim = state.simLabels[message.subscriptionId]?.let { stringResource(R.string.message_sim_label, it) }
    val info = remember(message.date, sim) {
        val millis = message.date * 1000L
        listOfNotNull(DateFormat.format("EEE d MMM yyyy", millis).toString(), millis.formatTime(context), sim).joinToString(" · ")
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Transparent,
        dragHandle = {
            // The handle's slot starts at the sheet's top edge, which the lifted bubble follows.
            Box(Modifier.onGloballyPositioned { onTop(it.positionOnScreen().y) }.padding(top = 8.dp)) {
                Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(colors.outlineVariant))
            }
        },
    ) {
        // Scrolls when landscape, split-screen or a large font leave less room than the eight rows need.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            HorizontalDivider(color = colors.outlineVariant)
            tapActions(message, state).sortedBy { it == MessageAction.DELETE || it == MessageAction.BLOCK_SENDER }.forEach { action ->
                val danger = action == MessageAction.DELETE || action == MessageAction.BLOCK_SENDER
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TargetSize)
                        .clickable {
                            // Act once the sheet is gone, so a dialog or the forward screen does not open under it.
                            scope.launch {
                                sheetState.hide()
                                // Not reached when the hide is cancelled, so a dismissed sheet never acts.
                                onDismiss()
                                onEvent(ThreadEvent.Act(action, listOf(message)))
                            }
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painterResource(action.icon), contentDescription = null, tint = if (danger) colors.error else colors.primary, modifier = Modifier.size(24.dp))
                    Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(firstName: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .semantics(mergeDescendants = true) {}
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_ol_messages_square), contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.thread_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        if (firstName.isNotEmpty()) {
            Text(stringResource(R.string.thread_empty_text, firstName), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
    is ThreadItem.ThreadSpamGroup -> generateStableId(THREAD_SPAM_GROUP, key)
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
    state: ThreadUiState,
    onEvent: (ThreadEvent) -> Unit,
    lifted: Long?,
    onOpen: (Message, Boolean, LayoutCoordinates) -> Unit,
) {
    val incoming = message.isReceivedMessage()
    val groupedAbove = groupedWith(message, older)
    val topGap = when {
        older is ThreadItem.ThreadDateTime -> 0.dp
        groupedAbove -> 4.dp
        else -> 12.dp
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topGap),
        horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
    ) {
        if (incoming && state.isGroup && !groupedAbove && message.senderName.isNotEmpty()) {
            Text(
                message.senderName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd) {
            Column(Modifier.width(minOf(maxWidth * BUBBLE_WIDTH_FRACTION, MaxBubbleWidth)), horizontalAlignment = if (incoming) Alignment.Start else Alignment.End) {
                val groupedBelow = groupedWith(message, newer)
                Bubble(message, state, onEvent, groupedBelow, hidden = lifted == message.getStableId()) { onOpen(message, groupedBelow, it) }
            }
        }
    }
}

@Composable
private fun Bubble(
    message: Message,
    state: ThreadUiState,
    onEvent: (ThreadEvent) -> Unit,
    groupedBelow: Boolean,
    hidden: Boolean = false,
    onOpen: ((LayoutCoordinates) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val incoming = message.isReceivedMessage()
    val failed = message.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val selected = message.getStableId() in state.selected
    val (container, content) = when {
        selected -> colors.secondaryContainer to colors.onSecondaryContainer
        failed -> colors.errorContainer to colors.onErrorContainer
        incoming && state.spamReason(message) != null -> colors.secondaryContainer to colors.onSecondaryContainer
        incoming -> colors.surfaceContainer to colors.onSurface
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    val statusLine = when {
        message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX && !message.isScheduled -> OpenLine.colors.lineSending
        state.spamReason(message) != null -> OpenLine.colors.lineBlocked
        else -> null
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = bubbleShape(incoming, groupedBelow)
    val attachments = message.attachment?.attachments.orEmpty()
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val selectLabel = stringResource(R.string.inbox_select)
    val accessibleActions = listOfNotNull(
        MessageAction.COPY.takeIf { message.body.isNotEmpty() },
        MessageAction.FORWARD,
        (if (state.isStarred(message)) MessageAction.UNSTAR else MessageAction.STAR).takeIf { !state.isRecycleBin },
        MessageAction.DELETE,
    ).map { action ->
        val label = stringResource(action.label)
        CustomAccessibilityAction(label) { onEvent(ThreadEvent.Act(action, listOf(message))); true }
    }
    // Hidden while its copy sits lifted above the sheet.
    Box(Modifier.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
        Column(
            Modifier
                .then(if (attachments.isNotEmpty()) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
                .onGloballyPositioned { coordinates[0] = it }
                .clip(shape)
                .background(container)
                .drawBehind {
                    statusLine?.let {
                        val width = 4.dp.toPx()
                        drawRect(it, Offset(if (rtl) size.width - width else 0f, 0f), Size(width, size.height))
                    }
                }
                .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else if (state.spamReason(message) != null) Modifier.border(1.dp, colors.primary, shape) else Modifier)
                .longPressAction(if (state.selecting) selectLabel else stringResource(org.fossify.commons.R.string.more_options)) {
                    if (state.selecting) onEvent(ThreadEvent.ToggleSelection(message))
                    else coordinates[0]?.takeIf { it.isAttached }?.let { onOpen?.invoke(it) }
                }
                .clickable(enabled = onOpen != null) {
                    if (state.selecting) {
                        onEvent(ThreadEvent.ToggleSelection(message))
                    } else {
                        coordinates[0]?.takeIf { it.isAttached }?.let { onOpen?.invoke(it) }
                    }
                }
                .semantics {
                    if (state.selecting) this.selected = selected
                    customActions = accessibleActions
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            attachments.forEach { attachment ->
                AttachmentView(attachment) {
                    if (state.selecting) onEvent(ThreadEvent.ToggleSelection(message)) else onEvent(ThreadEvent.OpenAttachment(message, attachment))
                }
            }
            if (message.body.isNotEmpty()) {
                MessageText(message.body, content, linkColor = if (incoming || selected || failed) colors.primary else OpenLine.colors.accent, links = !state.selecting, onCopy = { onEvent(ThreadEvent.CopyText(it)) }, query = if (state.searching) state.searchQuery else "")
            }
            if (selected) Icon(painterResource(R.drawable.ic_ol_square_check), null, tint = content, modifier = Modifier.size(20.dp))
            // Never hide a failure, in-flight send, scheduled time, star, or spam explanation in a run.
            if (!groupedBelow || failed || message.isScheduled || message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || state.isStarred(message) || state.spamReason(message) != null) {
                Metadata(message, state, content, Modifier.align(if (failed) Alignment.Start else Alignment.End))
            }
            if (failed) {
                // R6-46: Retry sends on the SIM shown; the switcher beside it only changes that SIM.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OpenLineButton(
                        stringResource(R.string.thread_retry),
                        { onEvent(ThreadEvent.Retry(message)) },
                        Modifier.weight(1f),
                        kind = OpenLineButtonKind.SECONDARY,
                    )
                    if (state.sendSim > 0) {
                        SimSwitcher(state.sendSim, carrier = "", expanded = true, onClick = { onEvent(ThreadEvent.NextSim) })
                    }
                }
            }
        }
    }
}

// The tail corner marks the last bubble of a run, on the sender's side.
private fun bubbleShape(incoming: Boolean, groupedBelow: Boolean): RoundedCornerShape {
    val tail = if (groupedBelow) BubbleCorner else BubbleTail
    return if (incoming) {
        RoundedCornerShape(BubbleCorner, BubbleCorner, BubbleCorner, tail)
    } else {
        RoundedCornerShape(BubbleCorner, BubbleCorner, tail, BubbleCorner)
    }
}

/**
 * Long press anywhere on a bubble opens its actions, including over a link, whose own handler
 * would otherwise take the gesture. While selecting, it toggles selection. Watches the Initial pass so it sees the press before the text does,
 * and swallows the release so the link or bubble tap does not fire as well.
 */
@Composable
private fun Modifier.longPressAction(label: String, onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    return semantics {
        onLongClick(label) {
            action()
            true
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val endedEarly = withTimeoutOrNull(450L) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            }
            if (endedEarly == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

/** Web links and email addresses open; standalone numbers copy, as in the View timeline. */
@Composable
internal fun MessageText(body: String, color: Color, linkColor: Color, links: Boolean, query: String = "", onCopy: (String) -> Unit) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val highlightInk = MaterialTheme.colorScheme.onSecondaryContainer
    val text = remember(body, links, linkColor, query, highlight, highlightInk) {
        val source = if (links) linkify(body, linkColor, onCopy) else AnnotatedString(body)
        buildAnnotatedString {
            append(source)
            searchRanges(source.text, query).forEach { range ->
                addStyle(SpanStyle(background = highlight, color = highlightInk, fontWeight = FontWeight.Bold), range.first, range.last + 1)
            }
        }.withContentFonts()
    }
    // Each paragraph takes its direction from its first letter, whatever the app language, and one
    // with no letters (a number, emoji) reads left to right, as in Telegram and WhatsApp.
    Text(text, style = MaterialTheme.typography.bodyLarge.forContent(body).copy(textDirection = TextDirection.ContentOrLtr), color = color, modifier = Modifier.fillMaxWidth())
}

// A match is the whole number or nothing. Isolating only part of it, as "000,000" out of
// "مانده70,000,000", leaves a separator outside the isolate and the groups read "000,000,70".
private const val NUMBER_SEPARATOR = "[ .,:/()\\-\u066B\u066C]"
private val NUMBER = Regex(
    "(?<![\\p{L}\\p{N}]|\\p{N}$NUMBER_SEPARATOR)\\+?\\p{Nd}+(?:$NUMBER_SEPARATOR\\p{Nd}+)*(?![\\p{L}\\p{N}]|$NUMBER_SEPARATOR\\p{N})"
)

internal fun linkify(body: String, linkColor: Color = Color.Unspecified, onCopy: (String) -> Unit): AnnotatedString {
    val spannable = SpannableString(body)
    LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
    val urls = spannable.getSpans(0, body.length, URLSpan::class.java)
        .map { Triple(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url) }
    val numbers = NUMBER.findAll(body)
        .filter { match -> urls.none { match.range.first < it.second && match.range.last >= it.first } }
        .map { Triple(it.range.first, it.range.last + 1, it.value) }
    val style = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    val links = (urls.map { it to true } + numbers.map { it to false }).sortedBy { it.first.first }
    return buildAnnotatedString {
        var cursor = 0
        links.forEach { (range, isUrl) ->
            val (start, end, value) = range
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

@Composable
private fun Metadata(message: Message, state: ThreadUiState, content: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val incoming = message.isReceivedMessage()
    val metaColor = when {
        message.type == Telephony.Sms.MESSAGE_TYPE_FAILED -> content
        message.getStableId() in state.selected || state.spamReason(message) != null -> OpenLine.colors.onSecondaryVariant
        incoming -> colors.onSurfaceVariant
        else -> OpenLine.colors.onBandVariant
    }
    val time = remember(message.date) { (message.date * 1000L).formatTime(context) }
    val failed = message.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    // Final v2 keeps the clock and a visible sending label; TalkBack announces the same state.
    val sending = message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX && !message.isScheduled
    val parts = buildList {
        if (failed) add(stringResource(R.string.message_not_sent_short))
        state.spamReason(message)?.let {
            add(stringResource(R.string.inbox_spam))
            add(stringResource(spamReasonLabel(it)))
        }
        if (!sending) add(time)
        state.simLabels[message.subscriptionId]?.let { add(stringResource(R.string.message_sim_label, it)) }
        if (message.isScheduled) add(stringResource(R.string.scheduled_message))
    }
    val delivered = message.status == Telephony.Sms.STATUS_COMPLETE
    val sent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT && !message.isScheduled
    val statusLabel = if (sent) stringResource(if (delivered) R.string.message_delivered else R.string.message_sent) else null
    val starredLabel = stringResource(R.string.starred_messages)
    val starred = state.isStarred(message)
    val sendingLabel = stringResource(R.string.sending).takeIf { sending }
    val description = (listOfNotNull(starredLabel.takeIf { starred }) + parts + listOfNotNull(statusLabel, sendingLabel)).joinToString(", ")
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
        },
        // Top, so the warning stays beside the first line when the failure line wraps.
        verticalAlignment = if (failed) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (starred) Icon(painterResource(R.drawable.ic_lucide_star), null, tint = metaColor, modifier = Modifier.size(14.dp))
        when {
            message.isScheduled ->
                Icon(painterResource(R.drawable.ic_ol_clock), null, tint = metaColor, modifier = Modifier.size(14.dp))

            failed -> Icon(painterResource(R.drawable.ic_ol_triangle_alert), null, tint = colors.error, modifier = Modifier.size(16.dp))
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
            Text(stringResource(R.string.sending), style = MaterialTheme.typography.bodySmall, color = metaColor)
            Icon(painterResource(R.drawable.ic_ol_clock), null, tint = metaColor, modifier = Modifier.size(14.dp))
        } else if (sent) {
            val icon = if (delivered) R.drawable.ic_ol_check_check else R.drawable.ic_ol_check
            Icon(painterResource(icon), null, tint = metaColor, modifier = Modifier.size(16.dp))
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(clickable),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = attachment.filename.ifEmpty { null }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (attachment.mimetype.isVideoMimeType()) {
                Icon(
                    painterResource(org.fossify.commons.R.drawable.ic_play_outline_vector),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

/** The first contact's name and a "Contact" / "and N others" line; empty until the card is parsed. */
@Composable
internal fun rememberVCardSummary(uri: Uri): Pair<String, String> {
    val context = LocalContext.current
    val unknown = stringResource(org.fossify.commons.R.string.unknown_error_occurred)
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
    AttachmentTile(R.drawable.ic_ol_user, name, subtitle, clickable)
}

@Composable
private fun FileAttachment(attachment: Attachment, clickable: Modifier) {
    val size = rememberFileSize(attachment.getUri())
    AttachmentTile(getIconResourceForMimeType(attachment.mimetype), attachment.filename.ifEmpty { attachment.mimetype }, size, clickable)
}

@Composable
private fun AttachmentTile(icon: Int, title: String, subtitle: String, clickable: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh)
            .then(clickable)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun DatePill(seconds: Int, modifier: Modifier = Modifier) {
    val label = remember(seconds) { ThreadDates.label(seconds) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DatePillText(label)
    }
}

@Composable
private fun DatePillText(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun UnreadDivider() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outlineVariant)
        )
        Text(stringResource(R.string.new_messages), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outlineVariant)
        )
    }
}

@Composable
private fun SpamGroupRow(group: ThreadItem.ThreadSpamGroup, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (group.expanded) 180f else 0f, label = "spam chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(min = TargetSize)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(R.drawable.ic_ol_shield_alert), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            pluralStringResource(if (group.expanded) R.plurals.spam_group_count else R.plurals.spam_group_hidden, group.messageIds.size, group.messageIds.size),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_ol_chevron_down),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * Follows the top visible day while scrolling, hides while its inline pill is on screen, and is
 * pushed up by the next day's pill. Fades 700 ms after scrolling stops (design board, dates).
 */
@Composable
private fun StickyDate(newestFirst: List<ThreadItem>, listState: LazyListState, modifier: Modifier = Modifier) {
    var pillHeight by remember { mutableIntStateOf(0) }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val sticky by remember(newestFirst) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val top = visible.lastOrNull() ?: return@derivedStateOf null
            val dayIndex = (top.index until newestFirst.size).firstOrNull { newestFirst[it] is ThreadItem.ThreadDateTime }
                ?: return@derivedStateOf null
            val inlineShowing = visible.any { it.index == dayIndex }
            // The next (newer) day's pill, if on screen, pushes this one up as it arrives.
            val next = visible.filter { it.index < dayIndex && newestFirst[it.index] is ThreadItem.ThreadDateTime }.maxByOrNull { it.index }
            val nextTop = next?.let { info.viewportEndOffset - it.offset - it.size }
            val push = nextTop?.let { minOf(0, it - (pillHeight + margin)) } ?: 0
            Triple((newestFirst[dayIndex] as ThreadItem.ThreadDateTime).date, inlineShowing, push)
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
    val lastShown = remember { arrayOfNulls<Triple<Int, Boolean, Int>>(1) }
    val current = sticky
    if (current != null) lastShown[0] = current
    AnimatedVisibility(
        visible = current != null && !current.second && scrolledRecently,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(STICKY_DATE_FADE_MS)),
        modifier = modifier,
    ) {
        val (date, _, push) = lastShown[0] ?: return@AnimatedVisibility
        DatePillText(
            remember(date) { ThreadDates.label(date) },
            Modifier
                .padding(top = 8.dp)
                .onSizeChanged { pillHeight = it.height }
                .graphicsLayer { translationY = push.toFloat() }
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
    AnimatedVisibility(show && !state.selecting, modifier.padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
        Surface(
            onClick = { onEvent(ThreadEvent.JumpToLatest) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .heightIn(min = TargetSize)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag(THREAD_JUMP_TAG),
        ) {
            Row(
                Modifier
                    .heightIn(min = TargetSize)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_ol_arrow_down), contentDescription = null, modifier = Modifier.size(20.dp))
                if (unread > 0) {
                    Text(pluralStringResource(R.plurals.thread_new_count, unread, unread), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit) {
    val (bar, overflow) = remember(state.selected, state.items, state.starred) { selectionActions(state).splitForBar() }
    val selected = state.selectedMessages
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .padding(8.dp),
        ) {
            bar.forEach { action ->
                BarItem(action.icon, stringResource(action.label), Modifier.weight(1f), danger = action == MessageAction.DELETE) {
                    onEvent(ThreadEvent.Act(action, selected))
                }
            }
            if (overflow.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    BarItem(R.drawable.ic_ol_ellipsis, stringResource(org.fossify.commons.R.string.more_options)) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        overflow.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(action.label), style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                                onClick = {
                                    open = false
                                    onEvent(ThreadEvent.Act(action, selected))
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
private fun BarItem(icon: Int, label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (danger) colors.error else colors.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

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
    OpenLineTheme(dark = dark) {
        Column(Modifier.fillMaxSize()) {
            ThreadHeader(state, {})
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

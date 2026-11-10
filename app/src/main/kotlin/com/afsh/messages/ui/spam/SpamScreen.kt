package com.afsh.messages.ui.spam

import com.afsh.messages.ui.components.UnreadDivider
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.getTimeFormat
import com.afsh.messages.R
import com.afsh.messages.models.SpamReviewMessage
import com.afsh.messages.models.SpamWhy
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.ConfirmationDialog
import com.afsh.messages.ui.components.DatePill
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.dayLabel
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.withContentFonts
import java.util.Calendar
import kotlin.math.roundToInt

private val BubbleMaxWidth = 280.dp
private val MenuWidth = 260.dp
private val HeldGap = 20.dp
private val ScreenMargin = 16.dp

/** Everything 20 · Spam can ask its activity to do. */
sealed interface SpamEvent {
    data object Back : SpamEvent
    data object OpenRules : SpamEvent
    data class Open(val message: SpamReviewMessage) : SpamEvent
    data class NotSpam(val message: SpamReviewMessage) : SpamEvent
    data class BlockSender(val message: SpamReviewMessage) : SpamEvent
    data object DeleteAll : SpamEvent
    /** These unread messages are on screen now; the activity marks them read. */
    data class Seen(val ids: Set<Long>) : SpamEvent
}

/**
 * [newIds] were unread when the page opened. They keep the "New messages" divider in place while
 * they are read, as a conversation does.
 */
@Immutable
data class SpamUiState(
    val loading: Boolean = true,
    val messages: List<SpamReviewMessage> = emptyList(),
    val newIds: Set<Long> = emptySet(),
)

/** One line of the review timeline. */
private sealed interface SpamLine {
    val key: String

    data class Day(val label: String, override val key: String) : SpamLine
    data object NewDivider : SpamLine {
        override val key get() = "new"
    }
    data class Item(val message: SpamReviewMessage, val showSender: Boolean) : SpamLine {
        override val key get() = "m${message.id}"
    }
}

/**
 * Oldest at the top and newest at the bottom, as in a conversation: one day pill where the day
 * changes, the sender named once per run. The "New messages" divider goes above the first message
 * of [newIds].
 */
private fun timeline(messages: List<SpamReviewMessage>, newIds: Set<Long>, dayOf: (Long) -> String): List<SpamLine> {
    val calendar = Calendar.getInstance()
    fun dayKey(millis: Long) = calendar.run {
        timeInMillis = millis
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }
    val lines = ArrayList<SpamLine>(messages.size + 8)
    var lastDay = -1
    var lastThread = -1L
    // The id breaks ties, so messages stored in the same millisecond keep their arrival order.
    messages.sortedWith(compareBy({ it.date }, { it.id })).forEach { message ->
        val day = dayKey(message.date)
        val newDay = day != lastDay
        if (newDay) lines += SpamLine.Day(dayOf(message.date), "d$day")
        lines += SpamLine.Item(message, showSender = newDay || message.threadId != lastThread)
        lastDay = day
        lastThread = message.threadId
    }
    val first = lines.indexOfFirst { it is SpamLine.Item && it.message.id in newIds }
    if (first < 0) return lines
    // A divider straight after a day pill reads better above it.
    val at = if (first > 0 && lines[first - 1] is SpamLine.Day) first - 1 else first
    return lines.apply { add(at, SpamLine.NewDivider) }
}

/**
 * 20 · Spam / review, the preferred "20 draft" direction: spam reads like a timeline. Tap opens the
 * conversation; long-press holds the message over a scrim with Not spam and Block sender.
 */
@Composable
fun SpamScreen(
    state: SpamUiState,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onEvent: (SpamEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lines = remember(state.loading, state.messages, state.newIds, configuration) {
        if (state.loading) emptyList() else timeline(state.messages, state.newIds) { dayLabel(context, it) }
    }
    val listState = rememberLazyListState()
    SpamReadTracking(lines, state.newIds, listState, onEvent)
    var held by remember { mutableStateOf<Held?>(null) }
    var confirmBlock by remember { mutableStateOf<SpamReviewMessage?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Box(modifier) {
        PageScaffold(
            title = stringResource(R.string.inbox_spam),
            subtitle = stringResource(R.string.spam_subtitle),
            onBack = { onEvent(SpamEvent.Back) },
            navigation = navigation,
            snackbarHostState = snackbarHostState,
            actions = {
                PageAppBarAction(R.drawable.ic_lucide_shield_ban, stringResource(R.string.block_rules), onClick = {
                    onEvent(SpamEvent.OpenRules)
                })
                if (state.messages.isNotEmpty()) {
                    PageAppBarAction(R.drawable.ic_lucide_trash_2, stringResource(R.string.clear_spam), onClick = {
                        confirmDeleteAll = true
                    })
                }
            },
        ) {
            LazyColumn(
                // The whole page scrolls; sized to its content, a drag beside the bubbles missed it.
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.messages.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_lucide_shield_ban,
                            title = stringResource(R.string.no_blocked_messages),
                            text = stringResource(R.string.spam_empty_text),
                        )
                    }

                    else -> items(lines, key = { it.key }) { line ->
                        when (line) {
                            is SpamLine.Day -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                DatePill(line.label)
                            }

                            SpamLine.NewDivider -> UnreadDivider()

                            is SpamLine.Item -> SpamMessage(
                                line,
                                hidden = held?.message?.id == line.message.id,
                                onOpen = { onEvent(SpamEvent.Open(line.message)) },
                                onHold = { bounds -> held = Held(line.message, line.showSender, bounds) },
                            )
                        }
                    }
                }
            }
        }
        held?.let { current ->
            HeldMessage(
                current,
                onDismiss = { held = null },
                onNotSpam = {
                    held = null
                    onEvent(SpamEvent.NotSpam(current.message))
                },
                onBlock = {
                    held = null
                    confirmBlock = current.message
                },
            )
        }
    }
    confirmBlock?.let { message ->
        ConfirmationDialog(
            title = stringResource(R.string.block_sender_title, message.sender),
            explanation = stringResource(R.string.block_sender_text),
            confirmLabel = stringResource(R.string.block_sender),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmBlock = null
                onEvent(SpamEvent.BlockSender(message))
            },
            onDismiss = { confirmBlock = null },
            destructive = true,
        )
    }
    if (confirmDeleteAll) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_spam_title),
            explanation = stringResource(R.string.clear_spam_confirmation),
            confirmLabel = stringResource(R.string.delete_forever),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmDeleteAll = false
                onEvent(SpamEvent.DeleteAll)
            },
            onDismiss = { confirmDeleteAll = false },
            destructive = true,
        )
    }
}

/**
 * As in a conversation: the page opens at the newest message, or at the "New messages" divider
 * when there is unread spam. Unread spam counts as read once it has been on screen, which also
 * clears the Spam badge. Nothing is counted before that first jump, so the rows the list starts
 * on for a frame are not marked read.
 */
@Composable
private fun SpamReadTracking(lines: List<SpamLine>, newIds: Set<Long>, listState: LazyListState, onEvent: (SpamEvent) -> Unit) {
    val currentLines by rememberUpdatedState(lines)
    val divider = lines.indexOf(SpamLine.NewDivider)
    // Once per visit: each visit brings its own set of new messages.
    var scrolledFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var positioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lines.isEmpty(), divider, newIds) {
        if (lines.isEmpty()) return@LaunchedEffect
        val visit = newIds.hashCode()
        when {
            divider >= 0 && newIds.isNotEmpty() && scrolledFor != visit -> {
                listState.scrollToItem(divider)
                scrolledFor = visit
            }
            // The first time there is something to show, start at the newest.
            !positioned -> listState.scrollToItem(lines.lastIndex)
        }
        positioned = true
    }
    LaunchedEffect(listState) {
        snapshotFlow<Set<Long>> {
            if (!positioned) return@snapshotFlow emptySet()
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
            currentLines.withIndex()
                .filter { (index, line) -> index in visible && line is SpamLine.Item && !line.message.read }
                .mapTo(HashSet()) { (it.value as SpamLine.Item).message.id }
        }
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .collect { onEvent(SpamEvent.Seen(it)) }
    }
}

/** The message under the finger and where it sits on screen. */
private class Held(val message: SpamReviewMessage, val showSender: Boolean, val bounds: Rect)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpamMessage(line: SpamLine.Item, hidden: Boolean, onOpen: () -> Unit, onHold: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (line.showSender) SenderLabel(line.message.sender, MaterialTheme.colorScheme.primary)
        // While held, the copy above the scrim stands in for this bubble.
        Bubble(
            line.message,
            held = false,
            modifier = Modifier
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .alpha(if (hidden) 0f else 1f)
                .clip(messageBubbleShape(incoming = true))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { onHold(bounds) },
                    onLongClickLabel = stringResource(R.string.spam_hold_label),
                ),
        )
    }
}

@Composable
private fun SenderLabel(sender: String, color: Color) {
    Text(senderText(sender), style = senderStyle(sender), color = color)
}

@Composable
private fun senderText(sender: String) = remember(sender) { AnnotatedString(sender).withContentFonts() }

@Composable
private fun senderStyle(sender: String) =
    MaterialTheme.typography.bodySmall.forContent(sender).copy(fontWeight = FontWeight.ExtraBold)

@Composable
private fun Bubble(message: SpamReviewMessage, held: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(message.date) {
        android.text.format.DateFormat.format(context.getTimeFormat(), message.date).toString()
    }
    val shape = messageBubbleShape(incoming = true)
    Column(
        modifier
            .widthIn(max = BubbleMaxWidth)
            .background(if (held) colors.secondaryContainer else colors.surfaceContainer, shape)
            .let { if (held) it.border(2.dp, colors.primary, shape) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ranges = message.why?.ranges.orEmpty()
        val highlight = SpanStyle(background = colors.errorContainer, color = colors.onErrorContainer)
        Text(
            remember(message.body, ranges, highlight) { highlighted(message.body, ranges, highlight).withContentFonts() },
            style = MaterialTheme.typography.bodyLarge.forContent(message.body),
            color = colors.onSurface,
        )
        val meta = if (held) Afsh.colors.onSecondaryContainerVariant else colors.onSurfaceVariant
        message.why?.let { WhyLine(message.body, it, meta) }
        Text(time, style = MaterialTheme.typography.bodySmall, color = meta)
    }
}

/** [body] with each matched range marked, so the words that sent it here stand out. */
private fun highlighted(body: String, ranges: List<IntRange>, style: SpanStyle) = buildAnnotatedString {
    append(body)
    ranges.filter { it.first >= 0 && it.last < body.length }.forEach { addStyle(style, it.first, it.last + 1) }
}

/** Why it is here: the rule, then the words it matched, or that today's rules no longer match. */
@Composable
private fun WhyLine(body: String, why: SpamWhy, color: Color) {
    val rule = stringResource(why.label)
    val words = remember(body, why) { why.matchedWords(body) }
    val text = when {
        why.stale -> stringResource(R.string.spam_why_stale, rule)
        words.isEmpty() -> stringResource(R.string.spam_why, rule)
        else -> stringResource(R.string.spam_why_words, rule, words.joinToString(stringResource(R.string.spam_why_separator)) { "«$it»" })
    }
    Text(
        remember(text) { AnnotatedString(text).withContentFonts() },
        style = MaterialTheme.typography.bodySmall.forContent(text).copy(fontWeight = FontWeight.Bold),
        color = color,
    )
}

/**
 * The held message (20 draft): the bubble stays where it was, outlined, over a scrim, with the
 * sender above it and the menu below. Near the bottom the group moves up so the menu fits.
 */
@Composable
private fun HeldMessage(held: Held, onDismiss: () -> Unit, onNotSpam: () -> Unit, onBlock: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    var groupHeight by remember { mutableIntStateOf(0) }
    var root by remember { mutableStateOf(Rect.Zero) }
    val title = stringResource(R.string.spam_hold_label)
    // The label sits above the bubble, so the group starts that much higher. Its line height is
    // in em, so it is measured rather than converted.
    val textMeasurer = rememberTextMeasurer()
    val senderText = senderText(held.message.sender)
    val senderStyle = senderStyle(held.message.sender)
    val labelHeight = remember(senderText, senderStyle) { textMeasurer.measure(senderText, senderStyle).size.height }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { root = it.boundsInRoot() }
            .background(Afsh.colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics { paneTitle = title },
    ) {
        val margin = with(density) { ScreenMargin.roundToPx() }
        val labelSpace = with(density) { HeldGap.roundToPx() } + labelHeight
        val wanted = held.bounds.top.roundToInt() - root.top.roundToInt() - if (held.showSender) labelSpace else 0
        val maxTop = with(density) { maxHeight.roundToPx() } - groupHeight - margin
        val top = wanted.coerceAtMost(maxTop).coerceAtLeast(margin)
        val start = held.bounds.left.roundToInt() - root.left.roundToInt()
        Column(
            Modifier
                .offset { IntOffset(start, top) }
                .onGloballyPositioned { groupHeight = it.size.height }
                .width(with(density) { held.bounds.width.toDp() }.coerceAtLeast(MenuWidth))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalArrangement = Arrangement.spacedBy(HeldGap),
        ) {
            if (held.showSender) SenderLabel(held.message.sender, colorResource(R.color.design_white))
            Bubble(held.message, held = true)
            HeldMenu(onNotSpam, onBlock)
        }
    }
}

@Composable
private fun HeldMenu(onNotSpam: () -> Unit, onBlock: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(MenuWidth)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val notSpam = stringResource(R.string.not_spam_short)
        MenuItem(R.drawable.ic_lucide_inbox, notSpam, colors.primary, colors.onSurface, onClick = onNotSpam)
        val block = stringResource(R.string.block_sender)
        MenuItem(R.drawable.ic_lucide_ban, block, iconTint = colors.error, textColor = colors.error, onClick = onBlock)
    }
}

/** Component / menu item. */
@Composable
private fun MenuItem(icon: Int, label: String, iconTint: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
    }
}

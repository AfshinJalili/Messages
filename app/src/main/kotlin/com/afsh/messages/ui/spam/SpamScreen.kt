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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.getTimeFormat
import com.afsh.messages.R
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.selected
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.SelectableBubbleRow
import com.afsh.messages.ui.components.PageSelection
import com.afsh.messages.ui.components.longPressToSelect
import com.afsh.messages.models.SpamReviewMessage
import com.afsh.messages.models.SpamWhy
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.ConfirmationDialog
import com.afsh.messages.ui.components.DatePill
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.dayLabel
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.withContentFonts
import java.util.Calendar

/** Everything 20 · Spam can ask its activity to do. */
sealed interface SpamEvent {
    data object Back : SpamEvent
    data object OpenRules : SpamEvent
    data class Open(val message: SpamReviewMessage) : SpamEvent
    data class NotSpam(val messages: List<SpamReviewMessage>) : SpamEvent
    data class BlockSender(val messages: List<SpamReviewMessage>) : SpamEvent
    data class Delete(val messages: List<SpamReviewMessage>) : SpamEvent
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
 * 20 · Spam / review: spam reads like a timeline. Tap opens the conversation. Long-press selects, the
 * same way a conversation does (SelectableBubbleRow): checks fade in, bubbles slide over, and the bar
 * offers Not spam, Block sender and Delete for everything selected.
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
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }
    // Messages that left the page (Not spam, Delete, Undo) leave the selection too.
    LaunchedEffect(state.messages) {
        val ids = state.messages.mapTo(HashSet()) { it.id }
        if (!ids.containsAll(selected)) selected = selected.filterTo(HashSet()) { it in ids }
    }
    val chosen = remember(state.messages, selected) { state.messages.filter { it.id in selected } }
    val selecting = chosen.isNotEmpty()
    val clear = { selected = emptySet() }
    var confirmBlock by remember { mutableStateOf<List<SpamReviewMessage>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<SpamReviewMessage>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    PageScaffold(
        title = stringResource(R.string.inbox_spam),
        modifier = modifier,
        subtitle = stringResource(R.string.spam_subtitle),
        onBack = { onEvent(SpamEvent.Back) },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
        selection = if (selecting) {
            PageSelection(
                count = chosen.size,
                onClear = clear,
                onSelectAll = { selected = state.messages.mapTo(HashSet()) { it.id } },
                actions = listOf(
                    BarAction(stringResource(R.string.not_spam_short), R.drawable.ic_lucide_inbox) {
                        clear()
                        onEvent(SpamEvent.NotSpam(chosen))
                    },
                    BarAction(stringResource(R.string.block_sender), R.drawable.ic_lucide_ban, destructive = true) { confirmBlock = chosen },
                    BarAction(stringResource(org.fossify.commons.R.string.delete), R.drawable.ic_lucide_trash_2, destructive = true) { confirmDelete = chosen },
                ),
            )
        } else {
            null
        },
        actions = {
            PageAppBarAction(AfshIcon.SpamShield, stringResource(R.string.block_rules), onClick = {
                onEvent(SpamEvent.OpenRules)
            })
            if (state.messages.isNotEmpty()) {
                PageAppBarAction(AfshIcon.Delete, stringResource(R.string.clear_spam), onClick = {
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
                            selecting = selecting,
                            checked = line.message.id in selected,
                            onOpen = { onEvent(SpamEvent.Open(line.message)) },
                            onToggle = {
                                val id = line.message.id
                                selected = if (id in selected) selected - id else selected + id
                            },
                        )
                    }
                }
            }
        }
    }
    confirmBlock?.let { messages ->
        val senders = remember(messages) { messages.map { it.sender }.distinct().joinToString(", ") }
        ConfirmationDialog(
            title = stringResource(R.string.block_sender_title, senders),
            explanation = stringResource(R.string.block_sender_text),
            confirmLabel = stringResource(R.string.block_sender),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmBlock = null
                clear()
                onEvent(SpamEvent.BlockSender(messages))
            },
            onDismiss = { confirmBlock = null },
            destructive = true,
        )
    }
    confirmDelete?.let { messages ->
        ConfirmationDialog(
            title = pluralStringResource(R.plurals.delete_messages_title, messages.size, messages.size),
            explanation = pluralStringResource(R.plurals.delete_messages_forever_text, messages.size),
            confirmLabel = stringResource(R.string.delete_forever),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmDelete = null
                clear()
                onEvent(SpamEvent.Delete(messages))
            },
            onDismiss = { confirmDelete = null },
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

private val SelectionSaver = Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toSet() },
)

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

/** A spam bubble in the shared selectable row, so it selects exactly like a conversation's. */
@Composable
private fun SpamMessage(line: SpamLine.Item, selecting: Boolean, checked: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val selectLabel = stringResource(R.string.inbox_select)
    SelectableBubbleRow(
        selecting = selecting,
        checked = checked,
        incoming = true,
        onToggle = onToggle,
        above = if (line.showSender) {
            { shift -> SenderLabel(line.message.sender, MaterialTheme.colorScheme.primary, shift.padding(bottom = 12.dp)) }
        } else {
            null
        },
    ) {
        Bubble(
            line.message,
            selected = checked,
            modifier = Modifier
                .clip(messageBubbleShape(incoming = true))
                .longPressToSelect(selectLabel, onToggle)
                .clickable { if (selecting) onToggle() else onOpen() }
                .semantics { if (selecting) this.selected = checked },
        )
    }
}

@Composable
private fun SenderLabel(sender: String, color: Color, modifier: Modifier = Modifier) {
    Text(senderText(sender), style = senderStyle(sender), color = color, modifier = modifier)
}

@Composable
private fun senderText(sender: String) = remember(sender) { AnnotatedString(sender).withContentFonts() }

@Composable
private fun senderStyle(sender: String) =
    MaterialTheme.typography.bodySmall.forContent(sender).copy(fontWeight = FontWeight.ExtraBold)

@Composable
private fun Bubble(message: SpamReviewMessage, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(message.date) {
        android.text.format.DateFormat.format(context.getTimeFormat(), message.date).toString()
    }
    val shape = messageBubbleShape(incoming = true)
    Column(
        modifier
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainer, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ranges = message.why?.ranges.orEmpty()
        val highlight = SpanStyle(background = colors.errorContainer, color = colors.onErrorContainer)
        Text(
            remember(message.body, ranges, highlight) { highlighted(message.body, ranges, highlight).withContentFonts() },
            style = MaterialTheme.typography.bodyLarge.forContent(message.body),
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
        )
        val meta = if (selected) Afsh.colors.onSecondaryContainerVariant else colors.onSurfaceVariant
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

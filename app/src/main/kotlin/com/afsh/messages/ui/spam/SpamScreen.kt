package com.afsh.messages.ui.spam

import com.afsh.messages.ui.components.UnreadDivider
import com.afsh.messages.ui.isolate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.afsh.commons.extensions.getTimeFormat
import com.afsh.messages.R
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.selected
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.SelectableBubbleRow
import com.afsh.messages.ui.components.PageSelection
import com.afsh.messages.ui.components.longPressToSelect
import com.afsh.messages.models.SpamReviewMessage
import com.afsh.messages.models.SpamWhy
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import com.afsh.messages.ui.components.ConfirmationDialog
import com.afsh.messages.ui.components.DatePill
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.dayLabel
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.components.BubbleTone
import com.afsh.messages.ui.components.ContentDirection
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.MessageBubble
import java.util.Calendar

/** Everything 20 · Spam can ask its activity to do. */
sealed interface SpamEvent {
    data object Back : SpamEvent
    data object OpenRules : SpamEvent
    data class Open(val message: SpamReviewMessage) : SpamEvent
    data class NotSpam(val messages: List<SpamReviewMessage>, val neverSpamSender: Boolean) : SpamEvent
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
    var notSpamSheet by remember { mutableStateOf<List<SpamReviewMessage>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<SpamReviewMessage>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val spamNumbers = context.config.spamNumbers
    val canAlwaysSpam = chosen.any { it.address.isNotEmpty() && !spamNumbers.containsNumber(it.address) }
    PageScaffold(
        title = stringResource(R.string.inbox_spam),
        modifier = modifier,
        onBack = { onEvent(SpamEvent.Back) },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
        selection = if (selecting) {
            PageSelection(
                count = chosen.size,
                onClear = clear,
                onSelectAll = { selected = state.messages.mapTo(HashSet()) { it.id } },
                actions = buildList {
                    add(
                        BarAction(stringResource(R.string.not_spam_short), AfshIcon.Inbox) {
                            notSpamSheet = chosen
                        },
                    )
                    if (canAlwaysSpam) {
                        add(
                            BarAction(
                                pluralStringResource(
                                    R.plurals.always_spam,
                                    chosen.map { it.address }.filter { it.isNotEmpty() }.distinct().size.coerceAtLeast(1),
                                ),
                                AfshIcon.SpamShield,
                            ) { confirmBlock = chosen },
                        )
                    }
                    add(
                        BarAction(stringResource(com.afsh.commons.R.string.delete), AfshIcon.Delete, destructive = true) {
                            confirmDelete = chosen
                        },
                    )
                },
            )
        } else {
            null
        },
        actions = {
            PageAppBarAction(AfshIcon.SpamShield, stringResource(R.string.filter_rules), onClick = {
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
            contentPadding = PaddingValues(start = Afsh.spacing.lg, top = Afsh.spacing.lg, end = Afsh.spacing.lg, bottom = Afsh.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.messages.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = AfshIcon.SpamShield,
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
    notSpamSheet?.let { messages ->
        AfshSheet(title = stringResource(R.string.not_spam_sheet_title), onDismiss = { notSpamSheet = null }) {
            AfshSheetItem(
                label = stringResource(R.string.not_spam_only),
                icon = AfshIcon.Inbox,
                supporting = stringResource(R.string.not_spam_only_text),
                onClick = {
                    dismissThen {
                        notSpamSheet = null
                        clear()
                        onEvent(SpamEvent.NotSpam(messages, neverSpamSender = false))
                    }
                },
            )
            AfshSheetItem(
                label = pluralStringResource(R.plurals.never_spam, 1),
                icon = AfshIcon.Verified,
                supporting = pluralStringResource(R.plurals.never_spam_text, 1),
                onClick = {
                    dismissThen {
                        notSpamSheet = null
                        clear()
                        onEvent(SpamEvent.NotSpam(messages, neverSpamSender = true))
                    }
                },
            )
        }
    }
    confirmBlock?.let { messages ->
        val senderCount = remember(messages) { messages.map { it.sender }.distinct().size.coerceAtLeast(1) }
        val senders = remember(messages) { messages.map { it.sender }.distinct().joinToString(", ") }
        ConfirmationDialog(
            title = if (senderCount == 1) {
                pluralStringResource(R.plurals.always_spam_title, 1, isolate(senders))
            } else {
                pluralStringResource(R.plurals.always_spam_title, senderCount, senderCount)
            },
            explanation = pluralStringResource(R.plurals.always_spam_text, senderCount),
            confirmLabel = pluralStringResource(R.plurals.always_spam, senderCount),
            dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
            onConfirm = {
                confirmBlock = null
                clear()
                onEvent(SpamEvent.BlockSender(messages))
            },
            onDismiss = { confirmBlock = null },
        )
    }
    confirmDelete?.let { messages ->
        ConfirmationDialog(
            title = pluralStringResource(R.plurals.delete_messages_title, messages.size, messages.size),
            explanation = pluralStringResource(R.plurals.delete_messages_forever_text, messages.size),
            confirmLabel = stringResource(R.string.delete_forever),
            dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
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
            dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
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
            { shift -> SenderLabel(line.message.sender, MaterialTheme.colorScheme.primary, shift.padding(bottom = Afsh.spacing.md)) }
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
    ContentText(
        sender,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun Bubble(message: SpamReviewMessage, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(message.date) {
        android.text.format.DateFormat.format(context.getTimeFormat(), message.date).toString()
    }
    MessageBubble(BubbleTone.of(incoming = true, selected = selected), messageBubbleShape(incoming = true), modifier) {
        val ranges = message.why?.ranges.orEmpty()
        val highlight = SpanStyle(background = colors.errorContainer, color = colors.onErrorContainer)
        ContentText(
            remember(message.body, ranges, highlight) { highlighted(message.body, ranges, highlight) },
            style = MaterialTheme.typography.bodyLarge,
            direction = ContentDirection.Text,
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
        else -> stringResource(R.string.spam_why_words, rule, words.joinToString(stringResource(R.string.spam_why_separator)) { "«${isolate(it)}»" })
    }
    ContentText(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        direction = ContentDirection.Inherit,
    )
}

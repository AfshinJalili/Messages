package org.fossify.messages.ui.spam

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.messages.R
import org.fossify.messages.models.SpamReviewMessage
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.ConfirmationDialog
import org.fossify.messages.ui.components.DatePill
import org.fossify.messages.ui.components.EmptyState
import org.fossify.messages.ui.components.PageAppBarAction
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.dayLabel
import org.fossify.messages.ui.components.messageBubbleShape
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.withContentFonts
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
}

@Immutable
data class SpamUiState(val loading: Boolean = true, val messages: List<SpamReviewMessage> = emptyList())

/** One line of the review timeline. */
private sealed interface SpamLine {
    val key: String

    data class Day(val label: String, override val key: String) : SpamLine
    data class Item(val message: SpamReviewMessage, val showSender: Boolean) : SpamLine {
        override val key get() = "m${message.id}"
    }
}

/** Days newest first; inside a day the messages read in order, the sender named once per run. */
private fun timeline(messages: List<SpamReviewMessage>, dayOf: (Long) -> String): List<SpamLine> {
    val calendar = Calendar.getInstance()
    fun dayKey(millis: Long) = calendar.run {
        timeInMillis = millis
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }
    return messages.groupBy { dayKey(it.date) }.toSortedMap(compareByDescending { it }).flatMap { (key, day) ->
        val ordered = day.sortedBy { it.date }
        listOf<SpamLine>(SpamLine.Day(dayOf(ordered.first().date), "d$key")) +
            ordered.mapIndexed { index, message ->
                SpamLine.Item(message, showSender = index == 0 || ordered[index - 1].threadId != message.threadId)
            }
    }
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
    val lines = remember(state.messages, configuration) { timeline(state.messages) { dayLabel(context, it) } }
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
        Text(
            remember(message.body) { AnnotatedString(message.body).withContentFonts() },
            style = MaterialTheme.typography.bodyLarge.forContent(message.body),
            color = colors.onSurface,
        )
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = if (held) OpenLine.colors.onSecondaryContainerVariant else colors.onSurfaceVariant,
        )
    }
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
            .background(OpenLine.colors.scrim)
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

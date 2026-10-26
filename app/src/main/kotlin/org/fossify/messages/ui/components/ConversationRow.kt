package org.fossify.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.messages.R
import org.fossify.messages.ui.Avatar
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.withContentFonts
import kotlin.math.abs

// A deliberate half-row drag commits. Flicks do not: the default fling velocity made a short flick
// delete or archive a conversation.
private const val SWIPE_COMMIT_FRACTION = 0.5f
private const val ARMED_ICON_SCALE = 1.25f
private const val TWO_LINE_TITLE_SCALE = 1.3f
private const val INLINE_METADATA_SCALE = 1.5f
private val AvatarSize = 50.dp
val ConversationRowMaxWidth = 640.dp

/** How a swipe panel is tinted: coral for destructive, quiet grey, or pine. */
enum class SwipeTone { PRIMARY, QUIET, DESTRUCTIVE }

/** What a swipe in one direction does, as the row's panel shows it. */
@Immutable
data class SwipeGesture(@StringRes val label: Int, @DrawableRes val icon: Int, val tone: SwipeTone)

/**
 * SwipeToDismissBox works in layout direction, but swipe settings are physical, so the mapping
 * flips under RTL. [onSwipe] reports whether the finger moved towards the right.
 */
@Composable
fun SwipeableRow(
    left: SwipeGesture?,
    right: SwipeGesture?,
    enabled: Boolean,
    onSwipe: (towardsRight: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val startToEnd = if (rtl) left else right
    val endToStart = if (rtl) right else left
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
        enableDismissFromStartToEnd = enabled && startToEnd != null,
        enableDismissFromEndToStart = enabled && endToStart != null,
        onDismiss = { value ->
            val startedToEnd = value == SwipeToDismissBoxValue.StartToEnd
            onSwipe(startedToEnd != rtl)
            // Some actions keep the row; the ones that remove it must bring it back settled on Undo.
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            direction.gesture(startToEnd, endToStart)?.let { gesture ->
                SwipeBackground(gesture, armed, alignStart = direction == SwipeToDismissBoxValue.StartToEnd)
            }
        },
        content = content,
    )
}

private fun SwipeToDismissBoxValue.gesture(startToEnd: SwipeGesture?, endToStart: SwipeGesture?) = when (this) {
    SwipeToDismissBoxValue.StartToEnd -> startToEnd
    SwipeToDismissBoxValue.EndToStart -> endToStart
    SwipeToDismissBoxValue.Settled -> null
}

/** The tonal panel reveals its label only once releasing would commit the action. */
@Composable
private fun SwipeBackground(gesture: SwipeGesture, armed: Boolean, alignStart: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (panel, onPanel) = when (gesture.tone) {
        SwipeTone.DESTRUCTIVE -> colors.errorContainer to colors.error
        SwipeTone.QUIET -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        SwipeTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
    }
    val background by animateColorAsState(panel, label = "swipe background")
    val tint by animateColorAsState(onPanel, label = "swipe icon")
    val scale by animateFloatAsState(
        if (armed) ARMED_ICON_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe icon scale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 24.dp),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(gesture.icon),
                contentDescription = stringResource(gesture.label),
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
            if (armed) Text(stringResource(gesture.label), color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Component / conversation row. While selecting, the avatar turns into a check. [actions] are the
 * TalkBack equivalents of the row's swipe gestures.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    row: InboxRow,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val conversation = row.conversation
    val unread = !conversation.read
    val fontScale = LocalDensity.current.fontScale
    val unreadCount = conversation.unreadCount.coerceAtLeast(1)
    val unreadLabel = if (unread) pluralStringResource(R.plurals.inbox_row_unread, unreadCount, unreadCount) else null
    val mutedLabel = stringResource(R.string.muted)
    val pinnedLabel = stringResource(R.string.inbox_section_pinned)
    val time = rememberRowTime(conversation.date)
    val description = listOfNotNull(
        conversation.title, unreadLabel, row.draft ?: conversation.snippet, time,
        mutedLabel.takeIf { row.muted }, pinnedLabel.takeIf { row.pinned },
    ).joinToString(", ")
    Row(
        modifier
            .widthIn(max = ConversationRowMaxWidth)
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
                if (!selecting) customActions = actions
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(AvatarSize), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_lucide_check), null, tint = colors.onPrimary)
                }
            } else {
                Avatar(conversation.title, conversation.photoUri, conversation.isGroupConversation, AvatarSize)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                remember(conversation.title) { AnnotatedString(conversation.title).withContentFonts() },
                style = MaterialTheme.typography.titleMedium.forContent(conversation.title),
                color = colors.onSurface,
                maxLines = if (fontScale >= TWO_LINE_TITLE_SCALE) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fontScale >= INLINE_METADATA_SCALE) RowMetadata(row, time, inline = true)
            SnippetText(row, unread)
        }
        if (fontScale < INLINE_METADATA_SCALE) {
            Box(Modifier.clearAndSetSemantics { }) { RowMetadata(row, time) }
        }
    }
}

/** The time today, Yesterday, then a short date ("21 Sep"): decision R9. */
@Composable
private fun rememberRowTime(seconds: Int): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(seconds, configuration) {
        val millis = seconds * 1000L
        when (relativeDay(millis)) {
            RelativeDay.TODAY -> android.text.format.DateFormat.format(context.getTimeFormat(), millis).toString()
            RelativeDay.YESTERDAY -> context.getString(R.string.inbox_section_yesterday)
            RelativeDay.EARLIER -> shortDate(millis)
        }
    }
}

@Composable
internal fun SnippetText(row: InboxRow, unread: Boolean) {
    val colors = MaterialTheme.colorScheme
    val draft = row.draft
    val conversation = row.conversation
    when {
        !draft.isNullOrEmpty() -> {
            val label = stringResource(R.string.inbox_draft_label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.error, fontWeight = FontWeight.Bold)) { append(label) }
                    append(' ')
                    append(draft)
                }.withContentFonts(),
                style = MaterialTheme.typography.bodyMedium.forContent(draft),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        conversation.isScheduled -> {
            val time = android.text.format.DateFormat.format("EEE HH:mm", conversation.date * 1000L).toString()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painterResource(R.drawable.ic_lucide_clock),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.inbox_scheduled_at, time),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        else -> Text(
            remember(conversation.snippet) { AnnotatedString(conversation.snippet).withContentFonts() },
            style = MaterialTheme.typography.bodyMedium.forContent(conversation.snippet),
            color = if (unread) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowMetadata(row: InboxRow, time: String, inline: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val unread = !row.conversation.read
    val signals: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.pinned) SignalIcon(R.drawable.ic_lucide_pin)
            if (row.muted) SignalIcon(R.drawable.ic_lucide_bell_off)
            if (unread) UnreadSignal(row.conversation.unreadCount)
        }
    }
    val timeText: @Composable () -> Unit = {
        Text(time, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
    }
    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            timeText()
            signals()
        }
    } else {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            timeText()
            signals()
        }
    }
}

@Composable
private fun SignalIcon(icon: Int) = Icon(
    painterResource(icon),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(16.dp),
)

/** A pine dot for one unread message, a count pill for more. */
@Composable
private fun UnreadSignal(count: Int) {
    val colors = MaterialTheme.colorScheme
    if (count > 1) {
        Box(
            Modifier
                .heightIn(min = 22.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                String.format(LocalConfiguration.current.locales[0], "%d", count),
                color = colors.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.primary),
        )
    }
}

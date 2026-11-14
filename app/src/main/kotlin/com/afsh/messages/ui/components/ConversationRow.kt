package com.afsh.messages.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.Avatar
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.inbox.InboxRow
import com.afsh.messages.ui.withContentFonts
import org.fossify.commons.extensions.getTimeFormat

private const val TWO_LINE_TITLE_SCALE = 1.3f
private const val INLINE_METADATA_SCALE = 1.5f
private val AvatarSize = 50.dp
private val UnreadDotSize = 10.dp
val ConversationRowMaxWidth = 640.dp

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
    val inlineMetadata = fontScale >= INLINE_METADATA_SCALE
    ListItem(
        headlineContent = {
            Text(
                remember(conversation.title) { AnnotatedString(conversation.title).withContentFonts() },
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.titleMedium.forContent(conversation.title),
                color = colors.onSurface,
                maxLines = if (fontScale >= TWO_LINE_TITLE_SCALE) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs)) {
                if (inlineMetadata) RowMetadata(row, time, inline = true)
                SnippetText(row, unread)
            }
        },
        leadingContent = { RowAvatar(row, selected) },
        trailingContent = if (inlineMetadata) null else ({ Box(Modifier.clearAndSetSemantics { }) { RowMetadata(row, time) } }),
        modifier = modifier
            .widthIn(max = ConversationRowMaxWidth)
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.inbox_select),
            )
            .semantics(mergeDescendants = true) {
                if (selecting) this.selected = selected
                contentDescription = description
                if (!selecting) customActions = actions
            },
        // Square on purpose: the selection fill and the swiped row are full rectangles.
        colors = ListItemDefaults.colors(containerColor = if (selected) colors.secondaryContainer else colors.surface),
    )
}

/** While selecting, a selected row's avatar turns into a check. */
@Composable
private fun RowAvatar(row: InboxRow, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (selected) {
        Surface(Modifier.size(AvatarSize), shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_lucide_check), null) }
        }
    } else {
        val conversation = row.conversation
        Avatar(conversation.title, conversation.photoUri, conversation.isGroupConversation, AvatarSize)
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
    // What people wrote keeps its own direction (an English full stop stays at the end in the
    // Persian interface), while the line still starts at the row's start edge.
    val rowStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
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
                style = MaterialTheme.typography.bodyMedium.forContent(draft).copy(textDirection = TextDirection.ContentOrLtr),
                color = colors.onSurfaceVariant,
                textAlign = rowStart,
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
            style = MaterialTheme.typography.bodyMedium.forContent(conversation.snippet).copy(textDirection = TextDirection.ContentOrLtr),
            color = if (unread) colors.onSurface else colors.onSurfaceVariant,
            textAlign = rowStart,
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

/** A dot for one unread message, a count badge for more. */
@Composable
private fun UnreadSignal(count: Int) {
    if (count > 1) {
        Badge(containerColor = Afsh.colors.badge.container, contentColor = Afsh.colors.badge.content) {
            Text(String.format(LocalConfiguration.current.locales[0], "%d", count))
        }
    } else {
        Badge(Modifier.size(UnreadDotSize), containerColor = MaterialTheme.colorScheme.primary)
    }
}

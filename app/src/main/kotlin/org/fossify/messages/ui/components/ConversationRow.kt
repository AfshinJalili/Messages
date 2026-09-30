package org.fossify.messages.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.messages.R
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.withContentFonts

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
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
    val line = OpenLine.colors.lineDraft
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
    val description = listOfNotNull(unreadLabel, conversation.title, row.draft ?: conversation.snippet, time,
        mutedLabel.takeIf { row.muted }, pinnedLabel.takeIf { row.pinned }).joinToString(", ")
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(MaterialTheme.shapes.medium)
            .openLineFocus(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .drawBehind {
                if (!row.draft.isNullOrEmpty()) {
                    val width = 4.dp.toPx()
                    drawRect(line, Offset(if (rtl) size.width - width else 0f, 0f), Size(width, size.height))
                }
            }
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
            .padding(horizontal = 4.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(OpenLine.colors.onAvatar), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_ol_check), null, tint = androidx.compose.ui.res.colorResource(R.color.design_white))
                }
            } else {
                Avatar(conversation.title, conversation.photoUri, conversation.isGroupConversation, 50.dp)
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                remember(conversation.title) { AnnotatedString(conversation.title).withContentFonts() },
                style = MaterialTheme.typography.titleMedium.forContent(conversation.title),
                color = colors.onSurface,
                maxLines = if (fontScale >= 1.3f) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fontScale >= 1.5f) Metadata(row, unread, inline = true)
            SnippetText(row, unread)
        }
        if (fontScale < 1.5f) Box(Modifier.width(70.dp).clearAndSetSemantics { }) { Metadata(row, unread) }
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
            }.withContentFonts(),
            style = MaterialTheme.typography.bodyMedium.forContent(draft),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else if (row.conversation.isScheduled) {
        val time = android.text.format.DateFormat.format("EEE HH:mm", row.conversation.date * 1000L).toString()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_ol_clock), null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.inbox_scheduled_at, time), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text(
            remember(row.conversation.snippet) { AnnotatedString(row.conversation.snippet).withContentFonts() },
            style = MaterialTheme.typography.bodyMedium.forContent(row.conversation.snippet),
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
            if (row.pinned) Icon(painterResource(R.drawable.ic_ol_pin), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            if (row.muted) Icon(painterResource(R.drawable.ic_ol_bell_off), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
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

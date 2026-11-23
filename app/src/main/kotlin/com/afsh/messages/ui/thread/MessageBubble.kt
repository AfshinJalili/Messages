package com.afsh.messages.ui.thread

import android.provider.Telephony
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afsh.commons.extensions.formatTime
import com.afsh.messages.R
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.models.spamReasonLabel
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.BubbleTone
import com.afsh.messages.ui.components.ContentDirection
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.MessageBubble
import com.afsh.messages.ui.components.SelectableBubbleRow
import com.afsh.messages.ui.components.longPressToSelect
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.components.role

private const val GROUPING_WINDOW_SECS = 60
private val TargetSize = 48.dp

/** Hoisted once in [ThreadTimeline] so scrolling bubbles do not resolve strings per row. */
@Immutable
internal data class BubbleStrings(
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
internal fun rememberBubbleStrings(): BubbleStrings {
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

internal fun groupedWith(message: Message, other: ThreadItem?) = other is Message &&
    other.isReceivedMessage() == message.isReceivedMessage() &&
    other.senderPhoneNumber == message.senderPhoneNumber &&
    kotlin.math.abs(other.date - message.date) <= GROUPING_WINDOW_SECS

@Composable
internal fun MessageRow(
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

internal fun isRtlParagraph(text: CharSequence): Boolean {
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
internal fun Bubble(
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

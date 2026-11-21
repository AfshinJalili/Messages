package com.afsh.messages.ui.thread

import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshCorners
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.afshFieldColors
import com.afsh.messages.ui.components.fieldMinHeight
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.semantics.onClick
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afsh.messages.R
import com.afsh.messages.ui.components.DisabledComposer
import com.afsh.messages.ui.components.DisabledComposerText
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.extensions.isVCardMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.helpers.attachmentIcon
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.ui.components.SendButton
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.lineHeightSp
import com.afsh.messages.ui.rememberBitmap
import com.afsh.messages.ui.withContentFonts
import java.util.Locale

private val TargetSize = 48.dp
private val SendWidth = 64.dp
private val TileSize = 72.dp
// The tray and the schedule chip line up with the field, past the 48 dp attach button and its gap.
private val FieldInset = 56.dp
// How far the 48 dp remove target reaches past a tile's end and top edges, keeping the dot 4 dp inside the corner.
private val BadgeOverhang = 9.dp
private const val MAX_FIELD_LINES = 5
private const val SWITCHER_MS = 220
private const val THUMBNAIL_OVERSAMPLE = 3

const val COMPOSER_FIELD_TAG = "composer_field"
const val COMPOSER_SEND_TAG = "composer_send"

/**
 * Design 81 and Round 6: attachment tray, then one row of 56 dp controls: + · SIM switcher (dual SIM, empty field only) ·
 * field · send, with the length pill above send. Design 78 when the sender cannot receive replies, 67 when
 * the sender is blocked.
 */
@Composable
fun ThreadComposer(
    composer: ComposerState,
    firstName: String,
    text: TextFieldValue,
    sendOnEnter: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onEvent: (ComposerEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var attaching by rememberSaveable { mutableStateOf(false) }
    // 67: a blocked sender keeps the composer's shape, switched off, until they are unblocked.
    if (composer.blocked) {
        ComposerPanel {
            DisabledComposer(stringResource(R.string.unblock_to_reply), Modifier.widthIn(max = 720.dp))
        }
        return
    }
    ComposerPanel {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            // A sender that takes no replies gets only the notice, full width: no +, SIM or send.
            if (!composer.canReply) {
                ReplyDisabledField(onEvent, Modifier.fillMaxWidth())
                return@Column
            }
            if (composer.attachments.isNotEmpty()) {
                AttachmentTray(composer.attachments, onEvent)
            }
            // Schedule is picked from the + sheet (R6-44); once picked, the time stays visible and cancellable here.
            if (composer.scheduledAt != null) {
                ScheduledChip(composer.scheduledAt, onEvent, Modifier.padding(start = FieldInset))
            }
            // No spacedBy: the collapsed switcher would still leave its gap.
            Row(verticalAlignment = Alignment.Bottom) {
                val attachLabel = stringResource(R.string.attachment)
                IconButton(onClick = { attaching = true }, modifier = Modifier.size(TargetSize, fieldMinHeight())) {
                    Icon(AfshIcon.Add.painter(), contentDescription = attachLabel, tint = colors.primary)
                }
                Spacer(Modifier.width(Afsh.spacing.sm))
                // R6-42: collapses on the first character and the field grows into its place.
                AnimatedVisibility(
                    visible = composer.sims.size > 1 && text.text.isEmpty(),
                    enter = expandHorizontally(tween(SWITCHER_MS)) + fadeIn(tween(SWITCHER_MS)),
                    exit = shrinkHorizontally(tween(SWITCHER_MS)) + fadeOut(tween(SWITCHER_MS)),
                ) {
                    val sim = composer.sims.getOrNull(composer.simIndex)
                    if (sim != null) {
                        SimSwitcher(sim.slot, sim.label, onClick = { onEvent(ComposerEvent.NextSim) }, modifier = Modifier.padding(end = Afsh.spacing.sm))
                    }
                }
                EntryField(composer, firstName, text, sendOnEnter, onTextChange, onEvent, Modifier.weight(1f))
                Spacer(Modifier.width(Afsh.spacing.sm))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm)) {
                    LengthPill(composer)
                    ComposerSendButton(composer, text.text, onEvent)
                }
            }
        }
    }
    if (attaching) {
        AttachSheet(onDismiss = { attaching = false }) { onEvent(ComposerEvent.Attach(it)) }
    }
}

/** The composer's band: a surface-container [Surface] across the window, content centred and clear of side insets. */
@Composable
private fun ComposerPanel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun EntryField(
    composer: ComposerState,
    firstName: String,
    text: TextFieldValue,
    sendOnEnter: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onEvent: (ComposerEvent) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(composer.focusRequest) {
        if (composer.focusRequest > 0) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val hint = if (firstName.isNotEmpty()) stringResource(R.string.composer_hint_name, firstName) else stringResource(R.string.composer_hint)
    val send = { if (composer.hasContent(text.text)) onEvent(ComposerEvent.Send) }
    val bodyLarge = MaterialTheme.typography.bodyLarge
    // Empty: keep the UI locale's bodyLarge so the cursor and placeholder share one line box.
    // Typed: script fonts via forContent, but never shrink leading below bodyLarge (English in a Persian UI
    // would otherwise drop 1.7.em → 1.45.em and reopen bottom slack against fieldMinHeight).
    val fieldStyle = if (text.text.isEmpty()) {
        bodyLarge
    } else {
        val content = bodyLarge.forContent(text.text)
        if (content.lineHeightSp().value < bodyLarge.lineHeightSp().value) {
            content.copy(lineHeight = bodyLarge.lineHeight)
        } else {
            content
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .heightIn(min = fieldMinHeight())
            .focusRequester(focus)
            .onPreviewKeyEvent {
                // Hardware keyboards send Enter rather than the IME's send action; Shift+Enter still breaks the line.
                val enter = sendOnEnter && !it.isShiftPressed && (it.key == Key.Enter || it.key == Key.NumPadEnter)
                if (enter && it.type == KeyEventType.KeyUp) send()
                enter
            }
            .testTag(COMPOSER_FIELD_TAG),
        // Empty, the cursor starts on the app language's side; with text, each paragraph follows its first letter and
        // digits alone read left to right, as the sent bubble will.
        textStyle = fieldStyle.copy(
            textDirection = if (text.text.isEmpty()) TextDirection.Content else TextDirection.ContentOrLtr,
        ),
        // The placeholder belongs to the edit box's own node, and TalkBack reads it only while empty.
        placeholder = { Text(hint, style = bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        visualTransformation = remember {
            VisualTransformation { TransformedText(it.withContentFonts(), OffsetMapping.Identity) }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onSend = { send() }),
        maxLines = MAX_FIELD_LINES,
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(colors.surface),
    )
}

@Composable
private fun ReplyDisabledField(onEvent: (ComposerEvent) -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier
            .heightIn(min = fieldMinHeight())
            .clip(shape)
            // Explains why, as the old notice's info button did.
            .clickable(onClickLabel = stringResource(com.afsh.commons.R.string.more_info)) { onEvent(ComposerEvent.ReplyInfo) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(Modifier.padding(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md), contentAlignment = Alignment.CenterStart) {
            DisabledComposerText(stringResource(R.string.composer_cant_reply))
        }
    }
}

@Composable
private fun ComposerSendButton(composer: ComposerState, text: String, onEvent: (ComposerEvent) -> Unit) {
    val canSend = composer.hasContent(text)
    // Mic while the field is empty and nothing is ready to send; dictation inserts, it never sends. A picked send time keeps
    // the schedule-send icon, disabled until there is content.
    val dictate = text.isEmpty() && !canSend && composer.scheduledAt == null
    val label = when {
        dictate -> stringResource(R.string.speak_message)
        composer.scheduledAt != null -> stringResource(R.string.schedule_message)
        composer.isMms -> stringResource(R.string.composer_send_mms)
        else -> stringResource(R.string.send_message)
    }
    val icon = when {
        dictate -> AfshIcon.Microphone
        composer.scheduledAt != null -> AfshIcon.ScheduleSend
        else -> AfshIcon.SendArrow
    }
    SendButton(
        icon = icon,
        description = label,
        onClick = { onEvent(if (dictate) ComposerEvent.Dictate else ComposerEvent.Send) },
        enabled = dictate || canSend,
        modifier = Modifier
            .size(SendWidth, fieldMinHeight())
            .testTag(COMPOSER_SEND_TAG),
    )
}

/** R6-45: "2 · 18" (SMS parts · characters left) near the limit, or "MMS". Always Latin digits, never wider than send. */
@Composable
private fun LengthPill(composer: ComposerState) {
    val text = when {
        composer.isMms -> stringResource(R.string.mms)
        composer.smsParts > 0 -> String.format(Locale.US, "%d · %d", composer.smsParts, composer.smsLeft)
        else -> return
    }
    val description = if (composer.isMms) text else stringResource(R.string.composer_sms_counter, composer.smsLeft, composer.smsParts)
    Surface(
        Modifier
            .width(SendWidth)
            .heightIn(min = 28.dp)
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(Modifier.padding(horizontal = Afsh.spacing.xs), contentAlignment = Alignment.Center) {
            // Latin digits keep Latin order: RTL would turn "1 · 150" into "150 · 1".
            Text(text, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * R6-43: a SIM card with its slot number. SIM 1 solid, SIM 2 outlined, so the two tell apart without reading. The cut corner
 * stays top right in RTL, as on a physical card; only the digit is localised.
 */
@Composable
internal fun SimGlyph(slot: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = AfshCorners.SimTile
    val solid = slot == 1
    // Decorative: the switcher around it says which SIM.
    Box(modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Surface(
            Modifier.size(14.dp, 20.dp),
            shape = shape,
            color = if (solid) colors.primary else colors.secondaryContainer,
            contentColor = if (solid) colors.onPrimary else colors.primary,
            border = if (solid) null else BorderStroke(1.5.dp, colors.primary),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    String.format(LocalConfiguration.current.locales[0], "%d", slot),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** R6-42 and R6-46: the same 48×56 glyph as the composer. A labelled "SIM n" wraps once Persian is large. A tap moves to the next SIM; it never sends. */
@Composable
internal fun SimSwitcher(slot: Int, carrier: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val simName = stringResource(R.string.sim_chip, slot)
    val description = listOf(stringResource(R.string.composer_change_sim), simName, carrier).filter { it.isNotEmpty() }.joinToString(", ")
    // One node for TalkBack: what it does, which SIM, and the carrier, read as a button that cycles SIMs.
    val cycle = onClick
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = description
        role = Role.Button
        this.onClick(label = null) {
            cycle()
            true
        }
    }
    // Same pill ripple as the attach button beside it.
    IconButton(onClick = onClick, modifier = modifier.size(TargetSize, fieldMinHeight()).then(semantics)) {
        SimGlyph(slot)
    }
}

@Composable
private fun ScheduledChip(scheduledAt: String, onEvent: (ComposerEvent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val cancel = stringResource(R.string.cancel_schedule_send)
    val sendLater = stringResource(R.string.composer_send_later)
    Surface(modifier.heightIn(min = TargetSize), shape = CircleShape, color = colors.surface, contentColor = colors.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .heightIn(min = TargetSize)
                    .clickable(onClickLabel = sendLater) { onEvent(ComposerEvent.Schedule) }
                    .padding(start = Afsh.spacing.md, end = Afsh.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
            ) {
                Icon(AfshIcon.Schedule.painter(), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(scheduledAt, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            IconButton(onClick = { onEvent(ComposerEvent.CancelSchedule) }) {
                Icon(AfshIcon.Close.painter(), contentDescription = cancel, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AttachmentTray(attachments: List<AttachmentSelection>, onEvent: (ComposerEvent) -> Unit) {
    LazyRow(
        // Room for the remove targets that reach past the tiles' top end.
        contentPadding = PaddingValues(start = FieldInset, top = BadgeOverhang, end = BadgeOverhang),
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                val open = Modifier.clickable(onClickLabel = attachment.filename.ifEmpty { null }) { onEvent(ComposerEvent.OpenAttachment(attachment)) }
                when {
                    attachment.mimetype.isImageMimeType() || attachment.mimetype.isVideoMimeType() -> MediaTile(attachment, open)
                    attachment.mimetype.isVCardMimeType() -> {
                        val (name, subtitle) = rememberVCardSummary(attachment.uri)
                        FileTile(AfshIcon.Person, name, subtitle, open)
                    }

                    else -> FileTile(
                        attachmentIcon(attachment.mimetype),
                        attachment.filename.ifEmpty { attachment.mimetype },
                        rememberFileSize(attachment.uri),
                        open,
                    )
                }
                RemoveBadge(Modifier.align(Alignment.TopEnd).offset(x = BadgeOverhang, y = -BadgeOverhang)) {
                    onEvent(ComposerEvent.RemoveAttachment(attachment))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(attachment: AttachmentSelection, open: Modifier) {
    val px = with(LocalDensity.current) { TileSize.roundToPx() } * THUMBNAIL_OVERSAMPLE
    // Compressed images get a new uri, so the key follows it.
    val bitmap = if (attachment.isPending) null else rememberBitmap(attachment.uri.toString(), px, px)
    val shape = MaterialTheme.shapes.small
    Surface(Modifier.size(TileSize).clip(shape).then(open), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            when {
                attachment.isPending -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                bitmap != null -> Image(bitmap, contentDescription = attachment.filename.ifEmpty { null }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (bitmap != null && attachment.mimetype.isVideoMimeType()) {
                Icon(AfshIcon.Play.painter(), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun FileTile(icon: AfshIcon, title: String, subtitle: String, open: Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Surface(Modifier.width(160.dp).heightIn(min = TileSize).clip(shape).then(open), shape = shape, color = colors.surface) {
        Row(
            // Room at the end for the remove badge.
            Modifier.padding(start = Afsh.spacing.md, end = Afsh.spacing.xxl, top = Afsh.spacing.sm, bottom = Afsh.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            Icon(icon.painter(), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** A 22 dp dot, 4 dp inside the tile's corner as drawn, centred in a 48 dp touch target. */
@Composable
private fun RemoveBadge(modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.composer_remove_attachment)
    IconButton(onClick = onClick, modifier = modifier.size(TargetSize)) {
        Surface(Modifier.size(22.dp), shape = CircleShape, color = colors.inverseSurface, contentColor = colors.inverseOnSurface) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AfshIcon.Close.painter(), contentDescription = label, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Design 11, as a sheet over the conversation rather than a page. */
@Composable
private fun AttachSheet(onDismiss: () -> Unit, onPick: (AttachOption) -> Unit) {
    AfshSheet(title = stringResource(R.string.attach_title), onDismiss = onDismiss) {
        AttachOption.entries.forEach { option ->
            AfshSheetItem(
                label = stringResource(option.title),
                icon = option.icon,
                supporting = stringResource(option.subtitle),
                tile = true,
                // The picker, camera or contact list opens once the sheet is gone.
                onClick = { dismissThen { onPick(option) } },
            )
        }
    }
}

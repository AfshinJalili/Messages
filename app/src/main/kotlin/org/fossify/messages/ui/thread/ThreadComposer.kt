package org.fossify.messages.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.messages.R
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.helpers.getIconResourceForMimeType
import org.fossify.messages.models.AttachmentSelection
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.rememberBitmap
import java.util.Locale

private val TargetSize = 48.dp
// R6-41: every control in the composer row.
private val RowHeight = 56.dp
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
 * field · send, with the length pill above send. Design 78 when the sender cannot receive replies.
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
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                IconButton(
                    onClick = { attaching = true },
                    modifier = Modifier
                        .size(TargetSize, RowHeight)
                        .semantics { contentDescription = attachLabel },
                ) {
                    Icon(painterResource(org.fossify.commons.R.drawable.ic_plus_vector), contentDescription = null, tint = colors.primary)
                }
                Spacer(Modifier.width(8.dp))
                // R6-42: collapses on the first character and the field grows into its place.
                AnimatedVisibility(
                    visible = composer.sims.size > 1 && text.text.isEmpty(),
                    enter = expandHorizontally(tween(SWITCHER_MS)) + fadeIn(tween(SWITCHER_MS)),
                    exit = shrinkHorizontally(tween(SWITCHER_MS)) + fadeOut(tween(SWITCHER_MS)),
                ) {
                    val sim = composer.sims.getOrNull(composer.simIndex)
                    if (sim != null) {
                        SimSwitcher(sim.slot, sim.label, expanded = false, onClick = { onEvent(ComposerEvent.NextSim) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
                EntryField(composer, firstName, text, sendOnEnter, onTextChange, onEvent, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LengthPill(composer)
                    SendButton(composer, text.text, onEvent)
                }
            }
        }
    }
    if (attaching) {
        AttachSheet(onDismiss = { attaching = false }) {
            attaching = false
            onEvent(ComposerEvent.Attach(it))
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
    // Persian glyphs sit taller, so RTL takes 2 dp less to keep one line at 56 dp (R6-41).
    val vertical = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 13.dp else 15.dp
    Box(
        modifier
            .heightIn(min = RowHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = vertical),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            maxLines = MAX_FIELD_LINES,
            // Empty, the cursor starts on the app language's side; with text, each paragraph follows its first letter and
            // digits alone read left to right, as the sent bubble will.
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = colors.onSurface,
                textDirection = if (text.text.isEmpty()) TextDirection.Content else TextDirection.ContentOrLtr,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
            // Inside the decoration box the hint belongs to the edit box's own node, and TalkBack reads it only while empty.
            decorationBox = { field ->
                // Full width for the inner field too, or it wraps its text and sits mid-field in RTL.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart, propagateMinConstraints = true) {
                    if (text.text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    // Hardware keyboards send Enter rather than the IME's send action; Shift+Enter still breaks the line.
                    val enter = sendOnEnter && !it.isShiftPressed && (it.key == Key.Enter || it.key == Key.NumPadEnter)
                    if (enter && it.type == KeyEventType.KeyUp) send()
                    enter
                }
                .testTag(COMPOSER_FIELD_TAG),
        )
    }
}

@Composable
private fun ReplyDisabledField(onEvent: (ComposerEvent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = RowHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            // Explains why, as the old notice's info button did.
            .clickable(onClickLabel = stringResource(org.fossify.commons.R.string.more_info)) { onEvent(ComposerEvent.ReplyInfo) }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(R.string.composer_cant_reply), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun SendButton(composer: ComposerState, text: String, onEvent: (ComposerEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val canSend = composer.hasContent(text)
    // Mic while the field is empty and nothing is ready to send; dictation inserts, it never sends. A picked send time keeps
    // the schedule-send icon, disabled until there is content.
    val dictate = text.isEmpty() && !canSend && composer.scheduledAt == null
    val enabled = dictate || canSend
    val label = when {
        dictate -> stringResource(R.string.speak_message)
        composer.scheduledAt != null -> stringResource(R.string.schedule_message)
        composer.isMms -> stringResource(R.string.composer_send_mms)
        else -> stringResource(R.string.send_message)
    }
    val icon = when {
        dictate -> org.fossify.commons.R.drawable.ic_microphone_vector
        composer.scheduledAt != null -> R.drawable.ic_schedule_send_vector
        else -> R.drawable.ic_arrow_up_right_vector
    }
    Box(
        Modifier
            .size(64.dp, RowHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) OpenLine.colors.accent else colors.surfaceContainerHigh)
            .clickable(enabled = enabled) { onEvent(if (dictate) ComposerEvent.Dictate else ComposerEvent.Send) }
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .testTag(COMPOSER_SEND_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (enabled) OpenLine.colors.onAccent else colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
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
    Box(
        Modifier
            .width(64.dp)
            .heightIn(min = 28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Latin digits keep Latin order: RTL would turn "1 · 150" into "150 · 1".
        Text(text, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/**
 * R6-43: a SIM card with its slot number. SIM 1 solid, SIM 2 outlined, so the two tell apart without reading. The cut corner
 * stays top right in RTL, as on a physical card; only the digit is localised.
 */
@Composable
internal fun SimGlyph(slot: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = AbsoluteRoundedCornerShape(topLeft = 2.dp, topRight = 5.dp, bottomRight = 2.dp, bottomLeft = 2.dp)
    val solid = slot == 1
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp, 20.dp)
                .clip(shape)
                .background(if (solid) colors.primary else colors.secondaryContainer)
                .then(if (solid) Modifier else Modifier.border(1.5.dp, colors.primary, shape)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                String.format(LocalConfiguration.current.locales[0], "%d", slot),
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (solid) colors.onPrimary else colors.primary,
            )
        }
    }
}

/** R6-42 compact (48×56, glyph only) and R6-46 expanded (glyph and "SIM n"). A tap moves to the next SIM; it never sends. */
@Composable
internal fun SimSwitcher(slot: Int, carrier: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val simName = stringResource(R.string.sim_chip, slot)
    val description = listOf(stringResource(R.string.composer_change_sim), simName, carrier).filter { it.isNotEmpty() }.joinToString(", ")
    Row(
        modifier
            .then(
                if (expanded) {
                    Modifier.heightIn(min = TargetSize).clip(MaterialTheme.shapes.medium).background(colors.surfaceContainer)
                } else {
                    // Same pill ripple as the attach button beside it.
                    Modifier.size(TargetSize, RowHeight).clip(CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .then(if (expanded) Modifier.padding(horizontal = 16.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        SimGlyph(slot)
        if (expanded) Text(simName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = colors.primary, maxLines = 1)
    }
}

@Composable
private fun ScheduledChip(scheduledAt: String, onEvent: (ComposerEvent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val cancel = stringResource(R.string.cancel_schedule_send)
    Row(
        modifier
            .heightIn(min = TargetSize)
            .clip(CircleShape)
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = TargetSize)
                .clickable(onClickLabel = stringResource(R.string.composer_send_later)) { onEvent(ComposerEvent.Schedule) }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(org.fossify.commons.R.drawable.ic_clock_vector), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Text(scheduledAt, style = MaterialTheme.typography.labelMedium, color = colors.primary, maxLines = 1)
        }
        IconButton(onClick = { onEvent(ComposerEvent.CancelSchedule) }, modifier = Modifier.semantics { contentDescription = cancel }) {
            Icon(painterResource(org.fossify.commons.R.drawable.ic_cross_vector), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AttachmentTray(attachments: List<AttachmentSelection>, onEvent: (ComposerEvent) -> Unit) {
    LazyRow(
        // Room for the remove targets that reach past the tiles' top end.
        contentPadding = PaddingValues(start = FieldInset, top = BadgeOverhang, end = BadgeOverhang),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                val open = Modifier.clickable(onClickLabel = attachment.filename.ifEmpty { null }) { onEvent(ComposerEvent.OpenAttachment(attachment)) }
                when {
                    attachment.mimetype.isImageMimeType() || attachment.mimetype.isVideoMimeType() -> MediaTile(attachment, open)
                    attachment.mimetype.isVCardMimeType() -> {
                        val (name, subtitle) = rememberVCardSummary(attachment.uri)
                        FileTile(org.fossify.commons.R.drawable.ic_person_vector, name, subtitle, open)
                    }

                    else -> FileTile(
                        getIconResourceForMimeType(attachment.mimetype),
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
    Box(
        Modifier
            .size(TileSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(open),
        contentAlignment = Alignment.Center,
    ) {
        when {
            attachment.isPending -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            bitmap != null -> Image(bitmap, contentDescription = attachment.filename.ifEmpty { null }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (bitmap != null && attachment.mimetype.isVideoMimeType()) {
            Icon(painterResource(org.fossify.commons.R.drawable.ic_play_outline_vector), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun FileTile(icon: Int, title: String, subtitle: String, open: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .width(160.dp)
            .heightIn(min = TileSize)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface)
            .then(open)
            // Room at the end for the remove badge.
            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A 22 dp dot, 4 dp inside the tile's corner as drawn, centred in a 48 dp touch target. */
@Composable
private fun RemoveBadge(modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.composer_remove_attachment)
    Box(
        modifier
            .size(TargetSize)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.inverseSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(org.fossify.commons.R.drawable.ic_cross_vector), contentDescription = null, tint = colors.inverseOnSurface, modifier = Modifier.size(14.dp))
        }
    }
}

/** Design 11, as a sheet over the conversation rather than a page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(onDismiss: () -> Unit, onPick: (AttachOption) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.attach_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            AttachOption.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(TargetSize)
                            .clip(MaterialTheme.shapes.small)
                            .background(colors.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(option.icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(option.title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(stringResource(option.subtitle), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

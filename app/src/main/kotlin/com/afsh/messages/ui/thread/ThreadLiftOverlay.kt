package com.afsh.messages.ui.thread

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.afsh.commons.extensions.formatTime
import com.afsh.messages.R
import com.afsh.messages.models.Message
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import com.afsh.messages.ui.components.messageBubbleShape
import com.afsh.messages.ui.components.upcomingDayAndTime
import kotlin.math.roundToInt

// R6-48: the lift is a spring of its own, not tied to the sheet's slide, so the bubble glides while the sheet moves.
// Its stiffness is the sheet's (Material 3's default spatial spring, 700), so the bubble and the sheet arrive and
// leave together, in about the 250-300 ms the app's other panels and screens take. It was 400, which trailed the sheet.
private const val LIFT_STIFFNESS = 700f
private val LiftSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = LIFT_STIFFNESS, visibilityThreshold = 0.001f)
private val LiftElevation = 6.dp
private val LiftFade = 32.dp

/** R6-48: the tapped bubble, where it sat on screen, and whether it ends a run (so shows its tail and time). */
internal class LiftedBubble(val message: Message, val groupedBelow: Boolean, val bounds: Rect)

private val NoOpThreadEvent: (ThreadEvent) -> Unit = {}

/**
 * R6-48: dims the timeline and floats a copy of the tapped bubble, without scrolling, then returns it to its slot.
 * It moves only as far as it must to clear the sheet's resting top by 12 dp, and it glides there on its own spring:
 * it never follows the sheet's edge while the sheet slides in or out. The sheet's own scrim is off; onDim lets the
 * host dim the header views outside this one. A bubble taller than the free space shows its top, fading out at the bottom.
 * [up] turns false as soon as the sheet heads for hidden, not when its dismissal settles, so the bubble and its
 * shadow and fade leave with the sheet instead of holding at its resting top after it has gone (#308).
 */
@Composable
internal fun LiftOverlay(
    lift: LiftedBubble,
    up: Boolean,
    sheetTop: Float,
    selecting: Boolean,
    selected: Boolean,
    starred: Boolean,
    spamReason: Int?,
    simLabel: String?,
    sendSim: Int,
    isRecycleBin: Boolean,
    strings: BubbleStrings,
    onDim: (Float) -> Unit,
    onGone: () -> Unit,
) {
    val progress = remember(lift) { Animatable(0f) }
    LaunchedEffect(progress) { snapshotFlow { progress.value }.collect(onDim) }
    LaunchedEffect(lift, up) {
        progress.animateTo(if (up) 1f else 0f, LiftSpring)
        if (!up) onGone()
    }
    val density = LocalDensity.current
    val liftGap = Afsh.spacing.md
    val scrim = Afsh.colors.liftScrim
    var origin by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .drawBehind { drawRect(scrim, alpha = progress.value) }
            .clearAndSetSemantics {},
        // Bounds are absolute screen positions; a start-aligned child would sit at the right edge in RTL.
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val at = origin ?: return@Box
        val slot = lift.bounds.top - at.y
        val full = lift.bounds.height
        val gap = with(density) { liftGap.toPx() }
        // sheetTop is where the sheet comes to rest, not its moving edge. Until it is known, the bubble stays in its slot.
        val free = if (sheetTop.isNaN()) slot + full else sheetTop - at.y - gap
        val shown = minOf(full, free.coerceAtLeast(0f))
        // Stays put when the sheet leaves it in view; a bubble cut off at the top comes down to the gap.
        val target = minOf(maxOf(slot, gap), free - shown)
        val height = full + (shown - full) * progress.value
        val top = slot + (target - slot) * progress.value
        val clipped = height < full - 1f
        val incoming = lift.message.isReceivedMessage()
        // The fade shrinks with the hidden part, so it is gone by the time the bubble is whole instead of
        // switching off in one frame when the clip ends (#308).
        val fade = minOf(with(density) { LiftFade.toPx() }, full - height)
        val mask = Afsh.colors.mask
        val clear = Afsh.colors.transparent
        Box(
            Modifier
                .absoluteOffset { IntOffset((lift.bounds.left - at.x).roundToInt(), top.roundToInt()) }
                // A bubble taller than the overlay must keep its own height: clamped to the overlay, the shown part's
                // bottom edge rode up off screen as the bubble went home, then the whole bubble popped back (#308).
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .size(with(density) { lift.bounds.width.toDp() }, with(density) { height.toDp() })
                .then(
                    if (clipped) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(0f to mask, 1f to clear, startY = size.height - fade, endY = size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .shadow(LiftElevation * progress.value, messageBubbleShape(incoming, lift.groupedBelow), clip = false),
            ) {
                Bubble(
                    message = lift.message,
                    selecting = selecting,
                    selected = selected,
                    starred = starred,
                    spamReason = spamReason,
                    simLabel = simLabel,
                    sendSim = sendSim,
                    isRecycleBin = isRecycleBin,
                    strings = strings,
                    onEvent = NoOpThreadEvent,
                    groupedBelow = lift.groupedBelow,
                )
            }
        }
    }
}

/** R6-48 (screens 97, 98): date · time · SIM, then the actions, Delete in the error colour. */
@Composable
internal fun MessageSheet(
    message: Message,
    state: ThreadUiState,
    onRestingTop: (Float) -> Unit,
    onLeavingChange: (Boolean) -> Unit,
    onEvent: (ThreadEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sim = state.simLabels[message.subscriptionId]?.let { stringResource(R.string.message_sim_label, it) }
    val info = remember(message.date, message.isScheduled, sim) {
        val millis = message.date * 1000L
        if (message.isScheduled) {
            // 84: when it goes, rather than when it was written.
            context.getString(R.string.scheduled_for, listOfNotNull(upcomingDayAndTime(context, millis), sim).joinToString(" · "))
        } else {
            listOfNotNull(DateFormat.format("EEE d MMM yyyy", millis).toString(), millis.formatTime(context), sim).joinToString(" · ")
        }
    }
    AfshSheet(
        title = info,
        onDismiss = onDismiss,
        showTitle = false,
        // The lifted bubble dims the page itself.
        dimBehind = false,
        onLeavingChange = onLeavingChange,
        // The sheet rests against the bottom of its full-window dialog, so its resting top is known from the first
        // frame, while it is still sliding in. The lifted bubble aims there instead of chasing the moving edge.
        modifier = Modifier.onGloballyPositioned {
            val window = it.findRootCoordinates()
            onRestingTop(window.positionOnScreen().y + window.size.height - it.size.height)
        },
    ) {
        Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Afsh.spacing.xxl, vertical = Afsh.spacing.md))
        HorizontalDivider(Modifier.padding(horizontal = Afsh.spacing.xl), color = colors.outlineVariant)
        tapActions(message, state).forEach { action ->
            AfshSheetItem(
                label = stringResource(action.label),
                icon = action.icon,
                danger = action == MessageAction.DELETE,
                // Act once the sheet is gone, so a dialog or the forward screen does not open under it.
                onClick = { dismissThen { onEvent(ThreadEvent.Act(action, listOf(message))) } },
            )
        }
    }
}

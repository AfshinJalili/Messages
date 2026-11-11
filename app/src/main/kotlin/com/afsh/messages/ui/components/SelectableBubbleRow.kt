package com.afsh.messages.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * Bubble selection, one source for every bubble list: the conversation (ThreadScreen) and Spam (SpamScreen).
 * Both lay out their rows with SelectableBubbleRow and select with longPressToSelect, so selecting a
 * message looks and behaves the same everywhere. Change it here, never in a screen.
 */

/** How wide a bubble may grow: a share of the row, capped so lines stay readable on wide windows. */
const val BUBBLE_WIDTH_FRACTION = 0.8f
val MaxBubbleWidth = 520.dp

// Room the selection check takes beside an incoming bubble: the 24 dp check and a 12 dp gap.
private val CheckSlot = 36.dp
private const val CHECK_START_SCALE = 0.6f

/**
 * One bubble row of a selectable list. While [selecting], a check fades in at the start of the row and
 * incoming bubbles slide over to make room. Bubbles keep the width they had (it comes from the full row),
 * so their text never reflows: they only move. Outgoing bubbles sit at the end, at most 80% wide, so the
 * check fits beside them without moving them. A tap anywhere on the row calls [onToggle], so the target
 * is not just the bubble. [above] (a sender name) moves together with the bubble.
 */
@Composable
fun SelectableBubbleRow(
    selecting: Boolean,
    checked: Boolean,
    incoming: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    above: (@Composable (Modifier) -> Unit)? = null,
    bubble: @Composable ColumnScope.() -> Unit,
) {
    // A layout offset, not a narrower slot: the bubble moves as one piece.
    val shift by animateDpAsState(
        if (selecting && incoming) CheckSlot else 0.dp,
        tween(if (selecting) SELECTION_ENTER_MS else SELECTION_EXIT_MS, easing = if (selecting) FastOutSlowInEasing else FastOutLinearInEasing),
        label = "selection shift",
    )
    val toggle by rememberUpdatedState(onToggle)
    val align = if (incoming) Alignment.Start else Alignment.End
    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        above?.invoke(Modifier.offset(x = shift))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .then(if (selecting) Modifier.pointerInput(Unit) { detectTapGestures { toggle() } } else Modifier),
            contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    .offset(x = shift)
                    .width(minOf(maxWidth * BUBBLE_WIDTH_FRACTION, MaxBubbleWidth)),
                horizontalAlignment = align,
                content = bubble,
            )
            RowCheck(selecting, checked, Modifier.align(Alignment.CenterStart))
        }
    }
}

/** The selection check at the start of a row; outside any Column or Row scope so it gets the plain AnimatedVisibility. */
@Composable
private fun RowCheck(visible: Boolean, checked: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(tween(SELECTION_ENTER_MS, easing = FastOutSlowInEasing)) +
            scaleIn(tween(SELECTION_ENTER_MS, easing = FastOutSlowInEasing), initialScale = CHECK_START_SCALE),
        exit = fadeOut(tween(SELECTION_EXIT_MS, easing = FastOutLinearInEasing)) +
            scaleOut(tween(SELECTION_EXIT_MS, easing = FastOutLinearInEasing), targetScale = CHECK_START_SCALE),
    ) {
        SelectionCheck(checked)
    }
}

/**
 * Long press anywhere on a bubble selects it, including over a link, whose own handler would
 * otherwise take the gesture. Watches the Initial pass so it sees the press before the text does,
 * and swallows the release so the link or bubble tap does not fire as well.
 */
@Composable
fun Modifier.longPressToSelect(label: String, onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    return semantics {
        onLongClick(label) {
            action()
            true
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            }
            if (endedEarly == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                action()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

package com.afsh.messages.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.BarEnter
import com.afsh.messages.ui.components.BarExit
import com.afsh.messages.ui.components.SELECTION_ENTER_MS
import com.afsh.messages.ui.components.SELECTION_EXIT_MS
import com.afsh.messages.ui.components.SelectionBar
import com.afsh.messages.ui.components.keptWhileLeaving

/**
 * The selection bar, drawn in the composer's frame. Over the composer it fills the frame, so both are the same
 * height and the timeline above never moves: the bar rises over the composer while the composer sinks under it.
 * With no composer (Recycle Bin) it grows from the bottom edge like the other selection bars.
 */
@Composable
fun ThreadSelectionBar(state: ThreadUiState, overComposer: Boolean, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val enter = if (overComposer) slideInVertically(tween(SELECTION_ENTER_MS)) { it } + fadeIn(tween(SELECTION_ENTER_MS)) else BarEnter
    val exit = if (overComposer) slideOutVertically(tween(SELECTION_EXIT_MS)) { it } + fadeOut(tween(SELECTION_EXIT_MS)) else BarExit
    AnimatedVisibility(state.selecting, modifier, enter = enter, exit = exit) {
        MessageSelectionBar(state, onEvent)
    }
}

@Composable
private fun MessageSelectionBar(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit) {
    val actions = remember(state.selected, state.items, state.starred) { selectionActions(state).splitForBar() }
    val (bar, overflow) = keptWhileLeaving(actions, empty = state.selected.isEmpty())
    val selected = state.selectedMessages
    val act = { action: MessageAction -> onEvent(ThreadEvent.Act(action, selected)) }
    SelectionBar(bar.map { it.toBarAction(act) }, overflow = overflow.map { it.toBarAction(act) })
}

@Composable
private fun MessageAction.toBarAction(onAction: (MessageAction) -> Unit) =
    BarAction(stringResource(label), icon, destructive = this == MessageAction.DELETE) { onAction(this) }

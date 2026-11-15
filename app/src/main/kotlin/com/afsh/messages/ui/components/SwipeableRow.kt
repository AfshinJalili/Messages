package com.afsh.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.afsh.messages.ui.Afsh
import kotlinx.coroutines.launch
import kotlin.math.abs

// A deliberate half-row drag commits. Flicks do not: the default fling velocity made a short flick
// delete or archive a conversation.
private const val SWIPE_COMMIT_FRACTION = 0.5f
private const val ARMED_ICON_SCALE = 1.25f

/** How a swipe panel is tinted: coral for destructive, quiet grey, or the swipe-action role. */
enum class SwipeTone { PRIMARY, QUIET, DESTRUCTIVE }

/** What a swipe in one direction does, as the row's panel shows it. */
@Immutable
data class SwipeGesture(@StringRes val label: Int, @DrawableRes val icon: Int, val tone: SwipeTone)

/** Whether releasing a drag at [offset] commits the action on a row [width] wide. */
internal fun swipeCommits(offset: Float, width: Float): Boolean = width > 0f && abs(offset) >= width * SWIPE_COMMIT_FRACTION

/**
 * The box's directions follow the layout, but swipe settings are physical: true when the finger
 * moved towards the right, null when settled.
 */
internal fun SwipeToDismissBoxValue.towardsRight(rtl: Boolean): Boolean? = when (this) {
    SwipeToDismissBoxValue.StartToEnd -> !rtl
    SwipeToDismissBoxValue.EndToStart -> rtl
    SwipeToDismissBoxValue.Settled -> null
}

/**
 * The Material 3 swipe-to-dismiss box with physical [left] and [right] gestures. [onSwipe] reports
 * whether the finger moved towards the right; the row then settles back, so an action that removes
 * it (and is undone) brings it back in place.
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
    var width by remember { mutableFloatStateOf(0f) }
    val state = rememberHalfRowSwipeState { width }
    val armed by remember { derivedStateOf { swipeCommits(state.offsetOrZero(), width) } }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val scope = rememberCoroutineScope()
    fun gesture(value: SwipeToDismissBoxValue) = when (value.towardsRight(rtl)) {
        true -> right
        false -> left
        null -> null
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { width = it.width.toFloat() },
        enableDismissFromStartToEnd = enabled && gesture(SwipeToDismissBoxValue.StartToEnd) != null,
        enableDismissFromEndToStart = enabled && gesture(SwipeToDismissBoxValue.EndToStart) != null,
        onDismiss = { value ->
            value.towardsRight(rtl)?.let(onSwipe)
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            gesture(direction)?.let { SwipeBackground(it, armed, alignStart = direction == SwipeToDismissBoxValue.StartToEnd) }
        },
        content = content,
    )
}

/**
 * Stock state with one veto: a fling released short of half the row settles back instead of
 * committing. The deprecated overload is the only one that can veto a dismiss.
 */
@Composable
private fun rememberHalfRowSwipeState(width: () -> Float): SwipeToDismissBoxState {
    val self = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    @Suppress("DEPRECATION")
    return rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.Settled || swipeCommits(self[0]?.offsetOrZero() ?: 0f, width())
        },
        positionalThreshold = { it * SWIPE_COMMIT_FRACTION },
    ).also { self[0] = it }
}

private fun SwipeToDismissBoxState.offsetOrZero() = runCatching { requireOffset() }.getOrDefault(0f)

/** The tonal panel reveals its label only once releasing would commit the action. */
@Composable
private fun SwipeBackground(gesture: SwipeGesture, armed: Boolean, alignStart: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (panel, onPanel) = when (gesture.tone) {
        SwipeTone.DESTRUCTIVE -> colors.errorContainer to colors.error
        SwipeTone.QUIET -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        SwipeTone.PRIMARY -> Afsh.colors.swipeAction.container to Afsh.colors.swipeAction.content
    }
    val background by animateColorAsState(panel, label = "swipe background")
    val tint by animateColorAsState(onPanel, label = "swipe icon")
    val scale by animateFloatAsState(
        if (armed) ARMED_ICON_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe icon scale",
    )
    Surface(Modifier.fillMaxSize(), color = background, contentColor = tint) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Afsh.spacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md, if (alignStart) Alignment.Start else Alignment.End),
        ) {
            Icon(
                painterResource(gesture.icon),
                contentDescription = stringResource(gesture.label),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
            if (armed) Text(stringResource(gesture.label), style = MaterialTheme.typography.labelMedium)
        }
    }
}

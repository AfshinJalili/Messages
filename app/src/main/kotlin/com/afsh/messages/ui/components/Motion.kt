package com.afsh.messages.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.integerResource
import com.afsh.messages.R

/** How far a pushed screen rises, in hundredths of its height; res/anim/screen_push_enter says 8% too. */
private const val SCREEN_RISE = 8
private const val PERCENT = 100

@Immutable
private data class StackEntry<T>(val depth: Int, val screen: T)

/**
 * A stack of screens inside one activity, moving like activities do (#176): a deeper [screen]
 * rises from the bottom over the one it covers, and a shallower one is revealed as the top sinks.
 */
@Composable
fun <T> ScreenStack(
    screen: T,
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val pushMs = integerResource(R.integer.motion_push_ms)
    val popMs = integerResource(R.integer.motion_pop_ms)
    AnimatedContent(
        targetState = StackEntry(depth, screen),
        modifier = modifier,
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                val push = tween<Float>(pushMs, easing = FastOutSlowInEasing)
                val rise = slideInVertically(tween(pushMs, easing = FastOutSlowInEasing)) { it * SCREEN_RISE / PERCENT }
                (rise + fadeIn(push)) togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                val pop = tween<Float>(popMs, easing = FastOutLinearInEasing)
                val sink = slideOutVertically(tween(popMs, easing = FastOutLinearInEasing)) { it * SCREEN_RISE / PERCENT }
                (EnterTransition.None togetherWith sink + fadeOut(pop)).apply { targetContentZIndex = -1f }
            }
        },
        label = "screen stack",
    ) { entry -> content(entry.screen) }
}

/** Switches between top-level destinations with the cross-fade the activities use (#176). */
@Composable
fun <T> TabSwitch(
    tab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val tabMs = integerResource(R.integer.motion_tab_ms)
    AnimatedContent(
        targetState = tab,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(tabMs, easing = LinearOutSlowInEasing)) togetherWith
                fadeOut(tween(tabMs, easing = FastOutLinearInEasing))
        },
        label = "tab switch",
    ) { content(it) }
}

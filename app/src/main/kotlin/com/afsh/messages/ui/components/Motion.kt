package com.afsh.messages.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.afsh.messages.R
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/**
 * Material 3 emphasized decelerate. Keep in sync with
 * `res/interpolator/emphasized_decelerate.xml`.
 */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Immutable
private data class StackEntry<T>(val depth: Int, val screen: T)

@Immutable
private data class TabEntry<T>(val index: Int, val tab: T)

/** The values of res/values/motion.xml, which the activity animations in res/anim use too. */
@Immutable
private class NavigationMotionTokens(
    val openMs: Int,
    val closeMs: Int,
    val tabMs: Int,
    val parallax: Float,
    val tabShift: Float,
)

@Composable
private fun rememberNavigationMotionTokens(): NavigationMotionTokens {
    val resources = LocalContext.current.resources
    return remember(resources) {
        NavigationMotionTokens(
            openMs = resources.getInteger(R.integer.motion_open_ms),
            closeMs = resources.getInteger(R.integer.motion_close_ms),
            tabMs = resources.getInteger(R.integer.motion_tab_ms),
            parallax = ResourcesCompat.getFloat(resources, R.dimen.motion_parallax),
            tabShift = ResourcesCompat.getFloat(resources, R.dimen.motion_tab_shift),
        )
    }
}

private fun <T> openSpec(ms: Int) = tween<T>(ms, easing = EmphasizedDecelerate)

/**
 * Opening: the new screen slides in from the end over the old one, which shifts toward the start
 * by [NavigationMotionTokens.parallax]. No alpha.
 */
private fun AnimatedContentTransitionScope<*>.screenOpen(tokens: NavigationMotionTokens): ContentTransform =
    (
        slideIntoContainer(SlideDirection.Start, openSpec(tokens.openMs)) togetherWith
            slideOutOfContainer(
                SlideDirection.Start,
                openSpec(tokens.openMs),
                targetOffset = { fullWidth -> (fullWidth * tokens.parallax).roundToInt() },
            )
        ) using SizeTransform(clip = false) { _, _ -> snap() }

/**
 * Back: the top screen slides out toward the end while the one underneath settles from its
 * parallax offset. No alpha.
 */
private fun AnimatedContentTransitionScope<*>.screenClose(tokens: NavigationMotionTokens): ContentTransform {
    val transform = (
        slideIntoContainer(
            SlideDirection.End,
            openSpec(tokens.closeMs),
            initialOffset = { fullWidth -> (fullWidth * tokens.parallax).roundToInt() },
        ) togetherWith slideOutOfContainer(SlideDirection.End, openSpec(tokens.closeMs))
        ) using SizeTransform(clip = false) { _, _ -> snap() }
    transform.targetContentZIndex = -1f
    return transform
}

/**
 * Tab change: the incoming pane slides in a short distance over the outgoing one, which stays put
 * until the enter finishes and is then dropped while fully covered. No alpha.
 *
 * A mutual [NavigationMotionTokens.tabShift] slide left ~70% of the old pane in the viewport for
 * the whole tween (and until AnimatedContent disposed it), so with non-opaque tab roots the
 * previous tab showed through and flashed away at the end.
 */
private fun AnimatedContentTransitionScope<*>.tabSwitch(
    tokens: NavigationMotionTokens,
    forward: Boolean,
): ContentTransform {
    val direction = if (forward) SlideDirection.Start else SlideDirection.End
    val transform = (
        slideIntoContainer(
            direction,
            openSpec(tokens.tabMs),
            initialOffset = { fullWidth -> (fullWidth * tokens.tabShift).roundToInt() },
        ) togetherWith slideOutOfContainer(
            direction,
            openSpec(tokens.tabMs),
            // Stay put under the opaque incoming cover; do not shift by tabShift.
            targetOffset = { 0 },
        )
        ) using SizeTransform(clip = true) { _, _ -> snap() }
    transform.targetContentZIndex = 1f
    return transform
}

/**
 * Screens that change inside one activity, moving like activities do: a deeper [screen] slides in
 * from the end over the one it covers (small parallax), and Back reverses that. When [onPop] is
 * set and [previous] is non-null, the system back gesture drives the close motion (predictive back).
 */
@Composable
fun <T : Any> ScreenStack(
    screen: T,
    depth: Int,
    modifier: Modifier = Modifier,
    previous: T? = null,
    onPop: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val tokens = rememberNavigationMotionTokens()
    val target = StackEntry(depth, screen)
    val transitionState = remember { SeekableTransitionState(target) }
    val underneath = previous?.let { StackEntry(depth - 1, it) }

    LaunchedEffect(target) {
        if (transitionState.currentState != target || transitionState.targetState != target) {
            transitionState.animateTo(target)
        }
    }

    PredictiveBackHandler(enabled = onPop != null && underneath != null) { events ->
        val current = transitionState.currentState
        val behind = underneath ?: return@PredictiveBackHandler
        try {
            events.collect { event ->
                transitionState.seekTo(fraction = event.progress, targetState = behind)
            }
            transitionState.snapTo(behind)
            onPop?.invoke()
        } catch (e: CancellationException) {
            transitionState.animateTo(current)
            throw e
        }
    }

    val transition = rememberTransition(transitionState, label = "screen stack")
    // clipToBounds: a finished exit translated off-host must not paint for an extra frame.
    transition.AnimatedContent(
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            if (targetState.depth >= initialState.depth) screenOpen(tokens) else screenClose(tokens)
        },
    ) { entry -> content(entry.screen) }
}

/**
 * Bottom-nav (or rail) tab content: a short cover slide in the direction of [tabIndex], with no
 * crossfade. Each pane paints an opaque surface and keeps saveable state across switches. Use this
 * for peer destinations such as Inbox and Library; pushed screens use [ScreenStack] instead.
 */
@Composable
fun <T> TabSwitch(
    tab: T,
    tabIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val tokens = rememberNavigationMotionTokens()
    val holder = rememberSaveableStateHolder()
    val surface = MaterialTheme.colorScheme.surface
    AnimatedContent(
        targetState = TabEntry(tabIndex, tab),
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            tabSwitch(tokens, forward = targetState.index >= initialState.index)
        },
        label = "tab switch",
    ) { entry ->
        holder.SaveableStateProvider(entry.index) {
            // Opaque root: without it the outgoing sibling shows through for the whole cover.
            Surface(Modifier.fillMaxSize(), color = surface) {
                content(entry.tab)
            }
        }
    }
}

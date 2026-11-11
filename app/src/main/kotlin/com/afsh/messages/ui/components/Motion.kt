package com.afsh.messages.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.afsh.messages.R

@Immutable
private data class StackEntry<T>(val depth: Int, val screen: T)

/** The values of res/values/motion.xml, which the activity animations in res/anim use too. */
@Immutable
private class ZoomMotion(val openMs: Int, val closeMs: Int, val small: Float, val large: Float) {
    /** Opening: the new screen grows in and fades in over the old one growing slightly. */
    fun open(): ContentTransform =
        (scaleIn(tween(openMs, easing = FastOutSlowInEasing), small) + fadeIn(tween(openMs, easing = FastOutSlowInEasing)))
            .togetherWith(scaleOut(tween(openMs, easing = FastOutSlowInEasing), large) + fadeOut(tween(0, delayMillis = openMs)))

    /** Back: the top screen shrinks away and fades out over the one underneath settling. */
    fun close(): ContentTransform =
        scaleIn(tween(closeMs, easing = FastOutSlowInEasing), large)
            .togetherWith(scaleOut(tween(closeMs, easing = FastOutLinearInEasing), small) + fadeOut(tween(closeMs, easing = FastOutLinearInEasing)))
            .apply { targetContentZIndex = -1f }
}

/**
 * Screens that change inside one activity, moving like activities do (#176): a deeper [screen]
 * zooms in over the one it covers, and a shallower one is revealed as the top shrinks away.
 * Settings pages use it, and so do Inbox (depth 0) and Library (depth 1).
 */
@Composable
fun <T> ScreenStack(
    screen: T,
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val resources = LocalContext.current.resources
    val motion = remember(resources) {
        ZoomMotion(
            openMs = resources.getInteger(R.integer.motion_open_ms),
            closeMs = resources.getInteger(R.integer.motion_close_ms),
            small = ResourcesCompat.getFloat(resources, R.dimen.motion_zoom_small),
            large = ResourcesCompat.getFloat(resources, R.dimen.motion_zoom_large),
        )
    }
    AnimatedContent(
        targetState = StackEntry(depth, screen),
        modifier = modifier,
        transitionSpec = { if (targetState.depth >= initialState.depth) motion.open() else motion.close() },
        label = "screen stack",
    ) { entry -> content(entry.screen) }
}

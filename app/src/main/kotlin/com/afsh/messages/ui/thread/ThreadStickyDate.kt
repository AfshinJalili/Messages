package com.afsh.messages.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.afsh.messages.helpers.ThreadDates
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.DatePill
import kotlinx.coroutines.delay

private const val STICKY_DATE_HIDE_DELAY_MS = 700L
private const val STICKY_DATE_FADE_MS = 180

const val THREAD_STICKY_DATE_TAG = "thread_sticky_date"

@Composable
internal fun DatePill(seconds: Int, modifier: Modifier = Modifier) {
    val label = remember(seconds) { ThreadDates.label(seconds) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DatePill(label)
    }
}

@Immutable
internal data class StickyDay(val date: Int, val dayIndex: Int, val inlineShowing: Boolean)

/**
 * The day of the topmost visible row in [newestFirst], whose [visible] indices run bottom to top: the first date
 * item at or above that row. Null when nothing is visible or no date lies above.
 */
internal fun stickyDay(newestFirst: List<ThreadItem>, visible: List<Int>): StickyDay? {
    val top = visible.lastOrNull() ?: return null
    val dayIndex = (top until newestFirst.size).firstOrNull { newestFirst[it] is ThreadItem.ThreadDateTime } ?: return null
    return StickyDay((newestFirst[dayIndex] as ThreadItem.ThreadDateTime).date, dayIndex, inlineShowing = dayIndex in visible)
}

/**
 * Follows the top visible day while scrolling, hides while its inline pill is on screen, and is
 * pushed up by the next day's pill. The push is a translation, and the timeline is drawn after the
 * header, so the slot is clipped: otherwise the outgoing pill paints over the title. Fades 700 ms
 * after scrolling stops (design board, dates).
 */
@Composable
internal fun StickyDate(newestFirst: List<ThreadItem>, listState: LazyListState, modifier: Modifier = Modifier) {
    val pillHeight = remember { intArrayOf(0) }
    val margin = with(LocalDensity.current) { Afsh.spacing.sm.roundToPx() }
    val sticky by remember(newestFirst) {
        derivedStateOf { stickyDay(newestFirst, listState.layoutInfo.visibleItemsInfo.map { it.index }) }
    }
    var scrolledRecently by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            scrolledRecently = true
        } else {
            delay(STICKY_DATE_HIDE_DELAY_MS)
            scrolledRecently = false
        }
    }
    // Keeps the last label while fading out, after the value itself has gone.
    val lastShown = remember { arrayOfNulls<StickyDay>(1) }
    val current = sticky
    if (current != null) lastShown[0] = current
    AnimatedVisibility(
        visible = current != null && !current.inlineShowing && scrolledRecently,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(STICKY_DATE_FADE_MS)),
        modifier = modifier.clipToBounds(),
    ) {
        val shown = lastShown[0] ?: return@AnimatedVisibility
        DatePill(
            remember(shown.date) { ThreadDates.label(shown.date) },
            Modifier
                .padding(top = Afsh.spacing.sm)
                .onSizeChanged { pillHeight[0] = it.height }
                .graphicsLayer {
                    val info = listState.layoutInfo
                    val next = info.visibleItemsInfo
                        .filter { it.index < shown.dayIndex && newestFirst.getOrNull(it.index) is ThreadItem.ThreadDateTime }
                        .maxByOrNull { it.index }
                    val push = next?.let {
                        val nextTop = info.viewportEndOffset - it.offset - it.size
                        minOf(0, nextTop - (pillHeight[0] + margin))
                    } ?: 0
                    translationY = push.toFloat()
                }
                .testTag(THREAD_STICKY_DATE_TAG),
        )
    }
}

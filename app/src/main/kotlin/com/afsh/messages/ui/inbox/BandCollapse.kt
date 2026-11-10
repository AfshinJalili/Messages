package com.afsh.messages.ui.inbox

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * How far the inbox band has collapsed (D01, design 80), driven by the list's scroll like an
 * enter-always Material app bar: every pixel the finger moves first moves the band, then the list.
 * [limitPx] is the collapsible part's height, measured by the band itself.
 */
@Stable
class BandCollapse(initialFraction: Float = 0f) {
    var limitPx by mutableFloatStateOf(0f)
        internal set
    var offsetPx by mutableFloatStateOf(0f)
        private set

    // Kept until the band is first measured, so a restored collapse lands at the right offset.
    private var pendingFraction = initialFraction

    /** 0 open, 1 collapsed. */
    val fraction: Float get() = if (limitPx <= 0f) pendingFraction else (-offsetPx / limitPx).coerceIn(0f, 1f)

    internal fun updateLimit(limit: Float) {
        if (limit == limitPx) return
        val keep = fraction
        limitPx = limit
        offsetPx = -keep * limit
        pendingFraction = keep
    }

    /** Moves the band by [delta] px and returns how much of it the band took. */
    fun consume(delta: Float): Float {
        val old = offsetPx
        offsetPx = (old + delta).coerceIn(-limitPx, 0f)
        return offsetPx - old
    }

    /** Ends a half-way band at whichever state is nearer, as Material app bars settle. */
    suspend fun settle(open: Boolean = fraction < HALF) {
        val target = if (open) 0f else -limitPx
        if (offsetPx == target) return
        AnimationState(offsetPx).animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) {
            offsetPx = value
        }
    }

    companion object {
        const val HALF = 0.5f
        val Saver: Saver<BandCollapse, Float> = Saver(save = { it.fraction }, restore = { BandCollapse(it) })
    }
}

@Composable
fun rememberBandCollapse(): BandCollapse = rememberSaveable(saver = BandCollapse.Saver) { BandCollapse() }

/**
 * Feeds the list's scroll to [band]. [enabled] is false while searching or selecting; [canScroll]
 * keeps a list that fits on screen from collapsing the band over nothing.
 */
@Composable
fun rememberBandScrollConnection(band: BandCollapse, enabled: () -> Boolean, canScroll: () -> Boolean): NestedScrollConnection =
    remember(band) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled() || !canScroll() || available.y == 0f) return Offset.Zero
                return Offset(0f, band.consume(available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (enabled()) band.settle()
                return Velocity.Zero
            }
        }
    }

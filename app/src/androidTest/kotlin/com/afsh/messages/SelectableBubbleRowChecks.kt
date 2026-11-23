package com.afsh.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.components.SELECTION_ENTER_MS
import com.afsh.messages.ui.components.SelectableBubbleRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #305: the first selection in a freshly opened thread must slide, not snap. */
@RunWith(AndroidJUnit4::class)
class SelectableBubbleRowChecks {
    @get:Rule
    val compose = createComposeRule()

    private var selecting by mutableStateOf(false)

    private fun show() {
        compose.setContent {
            TestTheme {
                SelectableBubbleRow(selecting = selecting, checked = false, incoming = true, onToggle = {}) {
                    Box(Modifier.testTag(BUBBLE).fillMaxWidth().height(40.dp))
                }
            }
        }
    }

    private fun bubbleLeft(): Dp = compose.onNodeWithTag(BUBBLE).getUnclippedBoundsInRoot().left

    @Test fun firstSelectionSlidesBubbleOverTheEnterDuration() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        assertEquals(0.dp, bubbleLeft())

        selecting = true
        compose.mainClock.advanceTimeByFrame() // recompose and start the animation
        compose.mainClock.advanceTimeBy(SELECTION_ENTER_MS / 2L)
        val mid = bubbleLeft()
        compose.mainClock.advanceTimeBy(SELECTION_ENTER_MS.toLong())
        val end = bubbleLeft()
        assertTrue("Bubble is mid-slide after half the enter duration, was $mid of $end", mid > 0.dp && mid < end)
    }

    @Test fun laterSelectionAndDeselectionStillSlide() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        selecting = true
        compose.mainClock.advanceTimeBy(SELECTION_ENTER_MS * 2L)
        val end = bubbleLeft()
        assertTrue("Bubble moved over to make room for the check", end > 0.dp)

        selecting = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(40)
        val leaving = bubbleLeft()
        assertTrue("Bubble is mid-slide back, was $leaving of $end", leaving > 0.dp && leaving < end)
        compose.mainClock.advanceTimeBy(SELECTION_ENTER_MS * 2L)
        assertEquals(0.dp, bubbleLeft())
    }

    private companion object { const val BUBBLE = "bubble" }
}

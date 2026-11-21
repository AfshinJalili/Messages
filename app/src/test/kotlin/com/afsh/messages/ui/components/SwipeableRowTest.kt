package com.afsh.messages.ui.components

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeableRowTest {
    @Test
    fun `a release past half the row commits in either direction`() {
        assertTrue(swipeCommits(offset = 200f, width = 400f))
        assertTrue(swipeCommits(offset = -260f, width = 400f))
    }

    @Test
    fun `a release short of half the row does not commit, however fast it was`() {
        assertFalse(swipeCommits(offset = 199f, width = 400f))
        assertFalse(swipeCommits(offset = -80f, width = 400f))
    }

    @Test
    fun `nothing commits before the row is measured`() {
        assertFalse(swipeCommits(offset = 0f, width = 0f))
    }

    @Test
    fun `swipe settings stay physical under RTL`() {
        assertEquals(true, SwipeToDismissBoxValue.StartToEnd.towardsRight(rtl = false))
        assertEquals(false, SwipeToDismissBoxValue.EndToStart.towardsRight(rtl = false))
        assertEquals(false, SwipeToDismissBoxValue.StartToEnd.towardsRight(rtl = true))
        assertEquals(true, SwipeToDismissBoxValue.EndToStart.towardsRight(rtl = true))
        assertNull(SwipeToDismissBoxValue.Settled.towardsRight(rtl = true))
    }
}

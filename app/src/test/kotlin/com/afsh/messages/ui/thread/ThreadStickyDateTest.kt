package com.afsh.messages.ui.thread

import com.afsh.messages.models.ThreadItem.ThreadDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which day the floating pill names, given the newest-first items and the visible indices (bottom to top). */
class ThreadStickyDateTest {
    // Newest first: index 0 is the bottom of the list, and each day's pill sits above (after) its messages.
    private val items = listOf(message(5), message(4), ThreadDateTime(200), message(3), message(2), ThreadDateTime(100), message(1))

    @Test
    fun `names the day of the topmost visible row`() {
        assertEquals(StickyDay(date = 200, dayIndex = 2, inlineShowing = false), stickyDay(items, listOf(0, 1)))
        assertEquals(StickyDay(date = 100, dayIndex = 5, inlineShowing = false), stickyDay(items, listOf(1, 2, 3, 4)))
    }

    @Test
    fun `knows when that day's own pill is on screen`() {
        assertEquals(StickyDay(date = 100, dayIndex = 5, inlineShowing = true), stickyDay(items, listOf(3, 4, 5)))
    }

    @Test
    fun `nothing when no row is visible or no day lies above`() {
        assertNull(stickyDay(items, emptyList()))
        assertNull(stickyDay(items, listOf(6)))
    }
}

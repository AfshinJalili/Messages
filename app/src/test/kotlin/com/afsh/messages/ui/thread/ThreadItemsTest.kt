package com.afsh.messages.ui.thread

import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem.ThreadDateTime
import com.afsh.messages.models.ThreadItem.ThreadUnreadSeparator
import com.afsh.messages.models.buildThreadItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a thread's messages become timeline items: spam inline, hidden left out, one unread divider. */
class ThreadItemsTest {
    private val spam = setOf(2L, 3L, 5L)

    private fun build(messages: List<Message>, hidden: Set<Long> = emptySet(), isNew: (Message) -> Boolean = { !it.read }) =
        buildThreadItems(messages, isSpam = { it.id in spam }, isHidden = { it.id in hidden }, isNew = isNew)
            .filterNot { it is ThreadDateTime }

    @Test
    fun `spam shows inline without an unread divider`() {
        val messages = listOf(message(1), message(2, read = false), message(3, read = false), message(4), message(5, read = false))
        assertEquals(messages, build(messages))
    }

    @Test
    fun `the divider skips spam and starts at the first real unread`() {
        val messages = listOf(message(1), message(2, read = false), message(4, read = false), message(5, read = false))
        assertEquals(listOf(messages[0], messages[1], ThreadUnreadSeparator, messages[2], messages[3]), build(messages))
    }

    @Test
    fun `hidden messages are left out`() {
        val messages = listOf(message(1), message(2, read = false), message(3, read = false), message(4))
        assertEquals(listOf(messages[0], messages[1], messages[3]), build(messages, hidden = setOf(3L)))
    }

    @Test
    fun `a divider pinned for this visit stays after the messages are read`() {
        val read = listOf(message(1), message(6), message(7))
        val pinned = setOf(read[1].getStableId(), read[2].getStableId())
        assertEquals(listOf(read[0], ThreadUnreadSeparator, read[1], read[2]), build(read) { it.getStableId() in pinned })
    }

    @Test
    fun `a read thread has no divider`() {
        assertTrue(build(listOf(message(1), message(6))).none { it == ThreadUnreadSeparator })
    }
}

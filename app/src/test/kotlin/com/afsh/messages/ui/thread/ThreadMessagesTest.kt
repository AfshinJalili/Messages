package com.afsh.messages.ui.thread

import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadMessagesTest {
    private val hidden = mutableSetOf<Long>()
    private val manual = mutableSetOf<Long>()
    private val data = ThreadMessages(isManuallyUnread = { it.id in manual }, isHidden = { it.id in hidden })

    private fun load(vararg messages: Message) {
        data.list = ArrayList(messages.toList())
    }

    private fun dividerBefore(): Long? {
        val items = data.items()
        val at = items.indexOf(ThreadItem.ThreadUnreadSeparator)
        return if (at < 0) null else (items.drop(at).first { it is Message } as Message).id
    }

    @Test
    fun `the divider sits above the first unread message`() {
        load(message(1), message(2, read = false), message(3, read = false))
        data.providerReady = true
        assertEquals(2L, dividerBefore())
    }

    @Test
    fun `the divider stays where it was after the thread is read`() {
        load(message(1), message(2, read = false))
        data.providerReady = true
        data.items()
        data.markReadLocally()
        assertFalse(data.hasUnread())
        assertEquals(2L, dividerBefore())
    }

    @Test
    fun `cached rows before the provider do not pin the divider`() {
        load(message(1, read = false))
        data.items()
        data.list = ArrayList(listOf(message(1)))
        data.providerReady = true
        assertEquals(null, dividerBefore())
    }

    @Test
    fun `a message marked unread by hand counts as unread but gets no divider`() {
        manual += 2
        load(message(1), message(2, read = false))
        data.providerReady = true
        assertTrue(data.hasUnread())
        assertEquals(null, dividerBefore())
    }

    @Test
    fun `spam is left out of read state`() {
        load(message(1, read = false))
        data.spamReasons = mapOf(1L to 0)
        assertFalse(data.hasUnread())
        data.markReadLocally()
        assertFalse(data.list.first().read)

        assertEquals(setOf(1L), data.markSpamReadLocally(listOf(1L)))
        assertTrue(data.list.first().read)
        assertTrue(data.markSpamReadLocally(listOf(1L)).isEmpty())
    }

    @Test
    fun `hidden messages leave the timeline`() {
        load(message(1), message(2))
        hidden += 1
        assertEquals(listOf(2L), data.items().filterIsInstance<Message>().map { it.id })
    }

    @Test
    fun `items come out oldest first`() {
        load(message(2), message(1))
        assertEquals(listOf(1L, 2L), data.items().filterIsInstance<Message>().map { it.id })
    }

    @Test
    fun `upsert replaces by id or appends`() {
        load(message(1))
        data.upsert(message(1, body = "edited"))
        data.upsert(message(2))
        assertEquals(listOf("edited", "m2"), data.list.map { it.body })
    }

    @Test
    fun `an empty older page means everything is loaded`() {
        load(message(5))
        data.prependOlder(listOf(message(3), message(4)))
        assertEquals(listOf(3L, 4L, 5L), data.list.map { it.id })
        assertEquals(3 * 60, data.oldestDate())
        assertFalse(data.allFetched)
        data.prependOlder(emptyList())
        assertTrue(data.allFetched)
    }

    @Test
    fun `contains matches the provider when it is known`() {
        load(message(1, isMms = true))
        assertTrue(data.contains(1, null))
        assertTrue(data.contains(1, true))
        assertFalse(data.contains(1, false))
    }
}

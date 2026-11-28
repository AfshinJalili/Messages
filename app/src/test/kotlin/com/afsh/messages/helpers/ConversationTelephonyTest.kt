package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTelephonyTest {

    private val sortOrders = ArrayList<String>()

    private fun rows(count: Int) = (1..count).toList()

    @Test
    fun `without a limit every thread is read newest first`() {
        val result = newestThreadsFirst(limit = null) { sortOrders += it; rows(30) }

        assertEquals(listOf("date DESC"), sortOrders)
        assertEquals(30, result.size)
    }

    @Test
    fun `a limit rides in the sort order`() {
        val result = newestThreadsFirst(limit = 20) { sortOrders += it; rows(20) }

        assertEquals(listOf("date DESC LIMIT 20"), sortOrders)
        assertEquals(rows(20), result)
    }

    @Test
    fun `a provider that rejects LIMIT is read unlimited and cut to the limit`() {
        val result = newestThreadsFirst(limit = 20) { sortOrder ->
            sortOrders += sortOrder
            if ("LIMIT" in sortOrder) throw IllegalArgumentException("invalid sort order") else rows(30)
        }

        assertEquals(listOf("date DESC LIMIT 20", "date DESC"), sortOrders)
        assertEquals(rows(20), result)
    }

    @Test
    fun `a provider that ignores LIMIT is cut to the limit`() {
        assertEquals(rows(20), newestThreadsFirst(limit = 20) { rows(30) })
    }

    @Test
    fun `a draft newer than the thread's last message dates the thread`() {
        assertEquals(1_700_000_500L, threadDate(providerDate = 1_700_000_000L, draftMillis = 1_700_000_500_000L))
    }

    @Test
    fun `an older draft leaves the thread at its last message`() {
        assertEquals(1_700_000_900L, threadDate(providerDate = 1_700_000_900L, draftMillis = 1_700_000_500_000L))
    }

    @Test
    fun `a provider date in milliseconds is read as seconds`() {
        assertEquals(1_700_000_900L, threadDate(providerDate = 1_700_000_900_123L, draftMillis = null))
        assertEquals(1_700_000_900L, threadDate(providerDate = 1_700_000_900_123L, draftMillis = 1_700_000_500_000L))
    }

    @Test
    fun `recipient ids are read from the threads table's space-separated list`() {
        assertEquals(listOf(3, 15), recipientIds("3 15"))
        assertEquals(emptyList<Int>(), recipientIds(""))
        assertEquals(emptyList<Int>(), recipientIds(null))
    }

    @Test
    fun `the inbox leaves out a thread without recipients`() {
        assertFalse(inboxShows(emptyList(), address = { "+1" }, blocked = { false }))
    }

    @Test
    fun `the inbox leaves out a thread with a blocked recipient`() {
        val addresses = mapOf(1 to "+1", 2 to "+2")
        assertFalse(inboxShows(listOf(1, 2), address = addresses::getValue, blocked = { it == "+2" }))
        assertTrue(inboxShows(listOf(1), address = addresses::getValue, blocked = { it == "+2" }))
    }

    @Test
    fun `with nothing blocked no recipient address is looked up`() {
        assertTrue(inboxShows(listOf(1, 2), address = { throw AssertionError("looked up $it") }, blocked = null))
    }

    @Test(expected = IllegalStateException::class)
    fun `an unlimited read that fails stays a failure`() {
        newestThreadsFirst<Int>(limit = null) { throw IllegalStateException("provider") }
    }

    @Test(expected = IllegalStateException::class)
    fun `a fallback read that fails stays a failure`() {
        newestThreadsFirst<Int>(limit = 20) { sortOrder ->
            if ("LIMIT" in sortOrder) throw IllegalArgumentException("invalid sort order")
            throw IllegalStateException("provider")
        }
    }
}

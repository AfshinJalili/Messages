package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
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

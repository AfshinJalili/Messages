package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telephony and the spam markers in memory; each change the backfill makes is recorded. */
private class FakeBackfillPlatform : SpamBackfill.Platform {
    val allowed = mutableSetOf<String>()
    val stored = mutableMapOf<String, SpamBackfill.Stored>()
    val steps = ArrayList<String>()
    private var nextSmsId = 100L

    override fun isAllowed(address: String) = address in allowed
    override fun threadId(address: String) = 7L
    override fun findStored(threadId: Long, row: SpamBackfill.Row) = stored[row.body]
    override fun insertSms(threadId: Long, row: SpamBackfill.Row): Long {
        val id = nextSmsId++
        stored[row.body] = SpamBackfill.Stored(id, isMms = false)
        steps += "insert SMS $id in $threadId"
        return id
    }

    override fun markSpam(smsId: Long, threadId: Long, reason: Int) {
        steps += "mark $smsId spam"
    }
}

class SpamBackfillTest {
    private val platform = FakeBackfillPlatform()
    private val row = SpamBackfill.Row(id = 1, address = "+989121234567", body = "جشنواره تخفیف", date = 1_700_000_000_000, reason = BLOCK_REASON_RULE)

    @Test
    fun `a legacy copy with no Telephony SMS is restored and marked spam`() {
        assertTrue(SpamBackfill.move(row, platform))
        assertEquals(listOf("insert SMS 100 in 7", "mark 100 spam"), platform.steps)
    }

    @Test
    fun `a legacy copy from a never-spam sender is restored, without a spam marker`() {
        platform.allowed += row.address
        assertTrue(SpamBackfill.move(row, platform))
        assertEquals(listOf("insert SMS 100 in 7"), platform.steps)
    }

    @Test
    fun `a legacy copy from a never-spam sender that Telephony still holds changes nothing`() {
        platform.allowed += row.address
        platform.stored[row.body] = SpamBackfill.Stored(42, isMms = false)
        assertTrue(SpamBackfill.move(row, platform))
        assertEquals(emptyList<String>(), platform.steps)
    }

    @Test
    fun `a legacy copy that Telephony still holds is marked, not inserted again`() {
        platform.stored[row.body] = SpamBackfill.Stored(42, isMms = false)
        assertTrue(SpamBackfill.move(row, platform))
        assertEquals(listOf("mark 42 spam"), platform.steps)
    }
}

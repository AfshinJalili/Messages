package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** First-run setup is one rising percent across conversation reconcile and message indexing. */
class ImportProgressMathTest {

    @Test
    fun `conversation share ends at the reserved weight`() {
        assertEquals(0, ImportProgressMath.conversationsPercent(0, 100))
        assertEquals(ImportProgressMath.CONVERSATIONS_SHARE, ImportProgressMath.conversationsPercent(100, 100))
        assertEquals(17, ImportProgressMath.conversationsPercent(50, 100))
    }

    @Test
    fun `messages after conversations fill the rest to 100`() {
        assertEquals(
            ImportProgressMath.CONVERSATIONS_SHARE,
            ImportProgressMath.messagesPercent(0, 80, afterConversations = true),
        )
        assertEquals(100, ImportProgressMath.messagesPercent(80, 80, afterConversations = true))
        assertEquals(67, ImportProgressMath.messagesPercent(40, 80, afterConversations = true))
    }

    @Test
    fun `messages alone use the full bar when conversations already finished earlier`() {
        assertEquals(0, ImportProgressMath.messagesPercent(0, 50, afterConversations = false))
        assertEquals(100, ImportProgressMath.messagesPercent(50, 50, afterConversations = false))
    }

    @Test
    fun `empty message work is complete`() {
        assertEquals(100, ImportProgressMath.messagesPercent(0, 0, afterConversations = true))
        assertEquals(100, ImportProgressMath.messagesPercent(0, 0, afterConversations = false))
    }

    @Test
    fun `hold never lets the percent fall`() {
        assertEquals(40, ImportProgressMath.hold(40, 35))
        assertEquals(55, ImportProgressMath.hold(40, 55))
        assertEquals(100, ImportProgressMath.hold(90, 100))
    }

    @Test
    fun `a full first-run path only rises`() {
        val steps = mutableListOf<Int>()
        var peak = 0
        fun push(next: Int) {
            peak = ImportProgressMath.hold(peak, next)
            steps += peak
        }
        for (done in 0 until 100 step 10) {
            push(ImportProgressMath.conversationsPercent(done + 1, 100))
        }
        push(ImportProgressMath.CONVERSATIONS_SHARE)
        for (done in listOf(0, 20, 40, 60, 80)) {
            push(ImportProgressMath.messagesPercent(done, 80, afterConversations = true))
        }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(100, steps.last())
        assertTrue(ImportProgressMath.CONVERSATIONS_SHARE in steps)
    }

    @Test
    fun `import progress fraction matches the percent`() {
        assertEquals(0f, ImportProgress(0).fraction)
        assertEquals(0.55f, ImportProgress(55).fraction)
        assertEquals(1f, ImportProgress(100).fraction)
    }
}

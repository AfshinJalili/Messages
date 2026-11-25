package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Permanently failing threads must not restart message import on every reconcile. */
class ImportFailureLedgerTest {

    @Test
    fun `record increments until the abandon limit`() {
        var failures = emptyMap<Long, Int>()
        failures = ImportFailureLedger.record(failures, 7L)
        assertEquals(1, failures[7L])
        failures = ImportFailureLedger.record(failures, 7L)
        failures = ImportFailureLedger.record(failures, 7L)
        assertEquals(ImportFailureLedger.MAX_ATTEMPTS, failures[7L])
        failures = ImportFailureLedger.record(failures, 7L)
        assertEquals(ImportFailureLedger.MAX_ATTEMPTS, failures[7L])
        assertTrue(ImportFailureLedger.isAbandoned(failures, 7L))
    }

    @Test
    fun `pending skips threads that are cached, were handled, or are abandoned`() {
        val failures = mapOf(2L to ImportFailureLedger.MAX_ATTEMPTS, 3L to 1)
        val cachedIds = setOf(1L)
        val handled = setOf(5L)
        val pending = ImportFailureLedger.pending(
            threadIds = listOf(1L, 2L, 3L, 4L, 5L),
            cached = { it in cachedIds },
            failures = failures,
            handled = handled,
        )
        assertEquals(listOf(3L, 4L), pending)
    }

    @Test
    fun `finished when every thread is cached, was handled, or is abandoned`() {
        val failures = mapOf(9L to ImportFailureLedger.MAX_ATTEMPTS)
        assertFalse(
            ImportFailureLedger.finished(
                threadIds = listOf(8L, 9L),
                cached = { false },
                failures = failures,
                handled = emptySet(),
            ),
        )
        assertTrue(
            ImportFailureLedger.finished(
                threadIds = listOf(8L, 9L),
                cached = { it == 8L },
                failures = failures,
                handled = emptySet(),
            ),
        )
        assertTrue(
            ImportFailureLedger.finished(
                threadIds = listOf(8L, 9L),
                cached = { false },
                failures = failures,
                handled = setOf(8L),
            ),
        )
    }

    @Test
    fun `a forever-bad thread is abandoned and does not block finish`() {
        var failures = emptyMap<Long, Int>()
        val good = 1L
        val bad = 2L
        val handled = setOf(good)
        // Simulate MAX_ATTEMPTS failed runs against the bad thread only.
        repeat(ImportFailureLedger.MAX_ATTEMPTS) {
            val pending = ImportFailureLedger.pending(
                threadIds = listOf(good, bad),
                cached = { false },
                failures = failures,
                handled = handled,
            )
            assertEquals(listOf(bad), pending)
            failures = ImportFailureLedger.record(failures, bad)
        }
        assertTrue(ImportFailureLedger.isAbandoned(failures, bad))
        assertTrue(
            ImportFailureLedger.pending(
                threadIds = listOf(good, bad),
                cached = { false },
                failures = failures,
                handled = handled,
            ).isEmpty(),
        )
        assertTrue(
            ImportFailureLedger.finished(
                threadIds = listOf(good, bad),
                cached = { false },
                failures = failures,
                handled = handled,
            ),
        )
    }

    @Test
    fun `successful empty threads are handled so import can finish`() {
        val handled = setOf(1L, 2L)
        assertTrue(
            ImportFailureLedger.finished(
                threadIds = listOf(1L, 2L),
                cached = { false },
                failures = emptyMap(),
                handled = handled,
            ),
        )
        assertTrue(
            ImportFailureLedger.pending(
                threadIds = listOf(1L, 2L),
                cached = { false },
                failures = emptyMap(),
                handled = handled,
            ).isEmpty(),
        )
    }

    @Test
    fun `clearFailure drops a recovered thread so a later failure starts over`() {
        val failures = ImportFailureLedger.record(emptyMap(), 5L)
        assertEquals(emptyMap<Long, Int>(), ImportFailureLedger.clearFailure(failures, 5L))
    }

    @Test
    fun `prefs round-trip keeps attempt counts and handled ids`() {
        val original = mapOf(10L to 1, 11L to ImportFailureLedger.MAX_ATTEMPTS)
        assertEquals(original, ImportFailureLedger.parseFailures(ImportFailureLedger.serializeFailures(original)))
        val handled = setOf(3L, 4L)
        assertEquals(handled, ImportFailureLedger.parseHandled(ImportFailureLedger.serializeHandled(handled)))
    }
}

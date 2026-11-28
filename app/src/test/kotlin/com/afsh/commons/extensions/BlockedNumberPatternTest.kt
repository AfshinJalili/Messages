package com.afsh.commons.extensions

import android.content.ContextWrapper
import com.afsh.commons.models.BlockedNumber
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An Android block-list entry with `*` is a pattern: `*` stands for any run of characters and
 * every other character is literal. The check runs on every incoming SMS, so no entry may throw.
 */
class BlockedNumberPatternTest {
    private val context = ContextWrapper(null)

    private fun blockedBy(pattern: String, number: String) =
        context.isNumberBlockedByPattern(number, arrayListOf(BlockedNumber(1, pattern, pattern, pattern)))

    @Test
    fun `star matches any run of characters`() {
        assertTrue(blockedBy("+98912*", "+989121234567"))
        assertTrue(blockedBy("*1234", "+989121234"))
        assertTrue(blockedBy("0912*67", "09121234567"))
        assertFalse(blockedBy("+98912*", "+989351234567"))
    }

    @Test
    fun `plus is literal`() {
        assertFalse(blockedBy("+98912*", "98912345"))
        assertFalse(blockedBy("+98912*", "++98912345"))
    }

    @Test
    fun `regex characters in an entry are literal`() {
        assertTrue(blockedBy("(021*", "(021) 1234"))
        assertFalse(blockedBy("(021*", "021 1234"))
        assertTrue(blockedBy("0.1*", "0.12"))
        assertFalse(blockedBy("0.1*", "0912"))
        assertTrue(blockedBy("09?2*", "09?21"))
        assertFalse(blockedBy("09?2*", "0921"))
    }

    @Test
    fun `a malformed entry never throws`() {
        listOf("(021*", ")*", "[*", "*\\", "\\E*", "a{2*", "**", "*").forEach { pattern ->
            blockedBy(pattern, "+989121234567")
        }
        assertTrue(blockedBy("\\E*", "\\E123"))
        assertTrue(blockedBy("*", "+989121234567"))
    }
}

package com.afsh.messages.helpers

import com.afsh.messages.extensions.config
import com.afsh.messages.receivers.PreferencesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blocked phrases: what is stored, and what a stored phrase catches. */
class BlockedPhrasesTest {
    private val context = PreferencesContext()

    @Test
    fun `a blank phrase is never stored`() {
        context.config.addBlockedKeyword("")
        context.config.addBlockedKeyword("   ")
        assertTrue(context.config.blockedKeywords.isEmpty())
    }

    @Test
    fun `a phrase already blocked in another case is not stored again`() {
        context.config.addBlockedKeyword("Prize")
        context.config.addBlockedKeyword(" prize ")
        assertEquals(setOf("Prize"), context.config.blockedKeywords)
    }

    @Test
    fun `a blank phrase that is already stored catches nothing`() {
        context.config.blockedKeywords = setOf("", "  ")
        assertTrue(keywordMatches(context.config.blockedKeywords, "Hi, see you at 8").isEmpty())
    }
}

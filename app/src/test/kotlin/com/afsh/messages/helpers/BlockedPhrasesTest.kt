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

    private fun matchedText(phrase: String, body: String) = keywordMatches(listOf(phrase), body).map { body.substring(it) }

    @Test
    fun `a phrase matches the same words written with other digits or letter forms`() {
        assertEquals(listOf("لغو۱۱"), matchedText("لغو11", "حراج بزرگ\nلغو۱۱"))
        assertEquals(listOf("كيش"), matchedText("کیش", "سفر به كيش ايرلاين"))
        assertEquals(listOf("LOTTERY"), matchedText("lottery", "WIN THE LOTTERY"))
    }

    @Test
    fun `a phrase matches through bidi isolates and embeddings`() {
        // Apps wrap numbers in FSI…PDI (U+2068, U+2069) or LRE…PDF (U+202A, U+202C).
        assertEquals(listOf("لغو\u206811\u2069"), matchedText("لغو11", "حراج\nلغو\u206811\u2069"))
        assertEquals(listOf("لغو\u202A11\u202C"), matchedText("لغو11", "حراج\nلغو\u202A11\u202C"))
    }

    @Test
    fun `the highlight covers a phrase stretched with tatweel`() {
        assertEquals(listOf("تـخـفـیـف"), matchedText("تخفیف", "۵۰ درصد تـخـفـیـف"))
    }

    @Test
    fun `the highlight keeps a diacritic with its letter`() {
        assertEquals(listOf("جشنِ"), matchedText("جشن", "جشنِ بزرگ"))
    }

    @Test
    fun `a blocked phrase in Persian digits silences the message`() {
        context.config.blockedKeywords = setOf("لغو11")
        assertEquals(
            BLOCK_REASON_KEYWORD,
            IncomingSpamClassifier.silenceReason(context, "+989121234567", "حراج بزرگ\nلغو۱۱"),
        )
    }
}

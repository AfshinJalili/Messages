package com.afsh.messages.helpers

import com.afsh.messages.extensions.config
import com.afsh.messages.receivers.PreferencesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Always-spam and never-spam senders: how a typed number is stored and when two senders are one. */
class SenderNumbersTest {
    private val context = PreferencesContext()

    @Test
    fun `a number typed with separators or Persian digits is the sender's number`() {
        listOf("0912 345 6789", "0912-345-6789", "(0912) 345 6789", "۰۹۱۲۳۴۵۶۷۸۹", "٠٩١٢٣٤٥٦٧٨٩").forEach { typed ->
            assertTrue(typed, setOf(typed).containsNumber("+989123456789"))
        }
    }

    @Test
    fun `a sender name matches only itself`() {
        assertTrue(setOf("HAMRAH_AVAL").containsNumber("HAMRAH_AVAL"))
        assertFalse(setOf("HAMRAH_AVAL").containsNumber("HAMRAH"))
    }

    @Test
    fun `a typed number is stored as digits`() {
        context.config.addSpamNumber("0912 345 6789")
        context.config.addAllowedNumber("‎+98 (935) 111-2233")
        assertEquals(setOf("09123456789"), context.config.spamNumbers)
        assertEquals(setOf("+989351112233"), context.config.allowedNumbers)
    }

    @Test
    fun `a typed sender name is stored as typed, trimmed`() {
        context.config.addSpamNumber(" BANK-PROMO ")
        assertEquals(setOf("BANK-PROMO"), context.config.spamNumbers)
    }
}

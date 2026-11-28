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

    @Test
    fun `a panel line is not the mobile number its last digits spell`() {
        assertFalse(setOf("50009121234567").containsNumber("+989121234567"))
        assertFalse(setOf("50009121234567").containsNumber("09121234567"))
    }

    @Test
    fun `allowing a mobile number leaves an always-spam panel line alone`() {
        context.config.addSpamNumber("50009121234567")
        context.config.addAllowedNumber("+989121234567")
        assertEquals(setOf("50009121234567"), context.config.spamNumbers)
    }

    @Test
    fun `one number written with or without its country code is one sender`() {
        listOf(
            "+989121234567" to "09121234567",
            "+989121234567" to "9121234567",
            "+989121234567" to "989121234567",
            "00989121234567" to "09121234567",
            "+1 555 0183" to "5550183",
            "3000" to "3000",
        ).forEach { (a, b) ->
            assertTrue("$a ~ $b", setOf(a).containsNumber(b))
            assertTrue("$b ~ $a", setOf(b).containsNumber(a))
        }
    }

    @Test
    fun `different numbers are different senders`() {
        listOf(
            "+4412345678" to "+14412345678",
            "09121234567" to "09121234568",
            "1000" to "21000",
            "*8000" to "8000",
        ).forEach { (a, b) ->
            assertFalse("$a ~ $b", setOf(a).containsNumber(b))
            assertFalse("$b ~ $a", setOf(b).containsNumber(a))
        }
    }
}

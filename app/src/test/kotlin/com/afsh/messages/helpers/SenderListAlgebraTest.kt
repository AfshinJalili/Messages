package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The set algebra behind Config.addSpamNumber / addAllowedNumber / removeAllowedNumber /
 * removeSpamNumber: matching uses [containsNumber], and the two lists replace each other on add.
 */
class SenderListAlgebraTest {
    @Test
    fun `containsNumber matches alpha senders exactly`() {
        assertTrue(setOf("HAMRAH_AVAL").containsNumber("HAMRAH_AVAL"))
        assertFalse(setOf("HAMRAH_AVAL").containsNumber("OTHER"))
    }

    @Test
    fun `adding to always-spam drops matching allowlist entries`() {
        val allowed = setOf("HAMRAH_AVAL", "BANK")
        val nextAllowed = allowed.filterNot { setOf(it).containsNumber("HAMRAH_AVAL") }.toSet()
        val spam = setOf("PROMO").plus("HAMRAH_AVAL")
        assertEquals(setOf("BANK"), nextAllowed)
        assertTrue(spam.containsNumber("HAMRAH_AVAL"))
    }

    @Test
    fun `adding to never-spam drops matching always-spam entries`() {
        val spam = setOf("HAMRAH_AVAL", "PROMO")
        val nextSpam = spam.filterNot { setOf(it).containsNumber("HAMRAH_AVAL") }.toSet()
        val allowed = setOf<String>().plus("HAMRAH_AVAL")
        assertEquals(setOf("PROMO"), nextSpam)
        assertTrue(allowed.containsNumber("HAMRAH_AVAL"))
    }

    @Test
    fun `removeAllowedNumber drops matching allowlist entries`() {
        val allowed = setOf("HAMRAH_AVAL", "TRUSTED")
        val next = allowed.filterNot { setOf(it).containsNumber("HAMRAH_AVAL") }.toSet()
        assertEquals(setOf("TRUSTED"), next)
    }
}

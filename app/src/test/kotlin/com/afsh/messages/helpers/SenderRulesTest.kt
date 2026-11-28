package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderRulesTest {
    @Test
    fun `a sender on neither list is automatic`() {
        assertEquals(SenderFilter.AUTOMATIC, senderFilter("09124186630", spamNumbers = emptySet(), allowedNumbers = emptySet()))
    }

    @Test
    fun `the lists name always and never spam, whatever the number's format`() {
        assertEquals(SenderFilter.ALWAYS_SPAM, senderFilter("+989124186630", setOf("09124186630"), emptySet()))
        assertEquals(SenderFilter.NEVER_SPAM, senderFilter("09124186630", emptySet(), setOf("+989124186630")))
    }

    @Test
    fun `never spam wins over always spam, as the classifier does`() {
        assertEquals(SenderFilter.NEVER_SPAM, senderFilter("0912", setOf("0912"), setOf("0912")))
    }

    @Test
    fun `several senders share a filter only when every one has it`() {
        assertEquals(SenderFilter.ALWAYS_SPAM, sharedSenderFilter(listOf("0912", "0913"), setOf("0912", "0913"), emptySet()))
        assertNull(sharedSenderFilter(listOf("0912", "0913"), setOf("0912"), emptySet()))
        assertNull(sharedSenderFilter(emptyList(), emptySet(), emptySet()))
    }
}

package com.afsh.messages.helpers

import com.afsh.messages.ui.thread.AttachOption
import org.fossify.commons.models.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A picked contact is shared as plain SMS text, and the attach sheet leads with the SMS-only options. */
class ContactTextTest {
    private fun number(value: String, normalized: String = value.filter(Char::isDigit)) = PhoneNumber(value, 0, "", normalized)

    @Test
    fun `name then each number on its own line`() {
        val text = contactAsText("مادربزرگ", listOf(number("0912 345 6789"), number("+98 21 1234 5678")))
        assertEquals("مادربزرگ\n0912 345 6789\n+98 21 1234 5678", text)
    }

    @Test
    fun `duplicate numbers are listed once`() {
        val text = contactAsText("Sara", listOf(number("0912 345 6789", "+989123456789"), number("09123456789", "+989123456789")))
        assertEquals("Sara\n0912 345 6789", text)
    }

    @Test
    fun `no number means nothing to share`() {
        assertNull(contactAsText("Sara", emptyList()))
        assertNull(contactAsText("Sara", listOf(number(" "))))
    }

    @Test
    fun `a blank name keeps only numbers`() {
        assertEquals("0912", contactAsText(" ", listOf(number("0912"))))
    }

    @Test
    fun `an inserted block sits on its own lines`() {
        assertEquals("Sara\n0912" to 9, insertBlock("", 0, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912" to 12, insertBlock("Hi", 2, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912\nbye" to 12, insertBlock("Hibye", 2, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912" to 12, insertBlock("Hi\n", 3, "Sara\n0912"))
    }

    @Test
    fun `the attach sheet leads with contact then send later`() {
        assertEquals(listOf(AttachOption.CONTACT, AttachOption.SEND_LATER), AttachOption.entries.take(2))
    }
}

package com.afsh.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.helpers.contactAsText
import com.afsh.messages.helpers.insertBlock
import com.afsh.messages.ui.thread.AttachOption
import org.fossify.commons.models.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** A picked contact is shared as plain SMS text, and the attach sheet leads with the SMS-only options. */
@RunWith(AndroidJUnit4::class)
class ContactTextChecks {
    private fun number(value: String, normalized: String = value.filter(Char::isDigit)) =
        PhoneNumber(value, 0, "", normalized)

    @Test
    fun nameThenEachNumberOnItsOwnLine() {
        val text = contactAsText("مادربزرگ", listOf(number("0912 345 6789"), number("+98 21 1234 5678")))
        assertEquals("مادربزرگ\n0912 345 6789\n+98 21 1234 5678", text)
    }

    @Test
    fun duplicateNumbersAreListedOnce() {
        val text = contactAsText("Sara", listOf(number("0912 345 6789", "+989123456789"), number("09123456789", "+989123456789")))
        assertEquals("Sara\n0912 345 6789", text)
    }

    @Test
    fun noNumberMeansNothingToShare() {
        assertNull(contactAsText("Sara", emptyList()))
        assertNull(contactAsText("Sara", listOf(number(" "))))
    }

    @Test
    fun blankNameKeepsOnlyNumbers() {
        assertEquals("0912", contactAsText(" ", listOf(number("0912"))))
    }

    @Test
    fun insertedBlockSitsOnItsOwnLines() {
        assertEquals("Sara\n0912" to 9, insertBlock("", 0, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912" to 12, insertBlock("Hi", 2, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912\nbye" to 12, insertBlock("Hibye", 2, "Sara\n0912"))
        assertEquals("Hi\nSara\n0912" to 12, insertBlock("Hi\n", 3, "Sara\n0912"))
    }

    @Test
    fun attachSheetLeadsWithContactThenSendLater() {
        assertEquals(listOf(AttachOption.CONTACT, AttachOption.SEND_LATER), AttachOption.entries.take(2))
    }
}

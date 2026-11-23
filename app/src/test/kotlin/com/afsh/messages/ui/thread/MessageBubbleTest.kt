package com.afsh.messages.ui.thread

import android.provider.Telephony
import com.afsh.messages.models.ThreadItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which bubbles share a run (so only the last shows the tail and time), and which way a paragraph reads. */
class MessageBubbleTest {
    private fun from(id: Long, date: Int, sender: String = "+98912", type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX) =
        message(id, type = type, date = date).copy(senderPhoneNumber = sender)

    @Test
    fun `same sender within a minute groups`() {
        assertTrue(groupedWith(from(1, 100), from(2, 160)))
        assertTrue(groupedWith(from(2, 160), from(1, 100)))
    }

    @Test
    fun `more than a minute apart does not group`() {
        assertFalse(groupedWith(from(1, 100), from(2, 161)))
    }

    @Test
    fun `another sender or direction does not group`() {
        assertFalse(groupedWith(from(1, 100), from(2, 110, sender = "+98935")))
        assertFalse(groupedWith(from(1, 100), from(2, 110, type = Telephony.Sms.MESSAGE_TYPE_SENT)))
    }

    @Test
    fun `a date pill, the unread divider or the list edge ends a run`() {
        assertFalse(groupedWith(from(1, 100), ThreadItem.ThreadDateTime(100)))
        assertFalse(groupedWith(from(1, 100), ThreadItem.ThreadUnreadSeparator))
        assertFalse(groupedWith(from(1, 100), null))
    }

    @Test
    fun `a paragraph reads the way its first strong letter does`() {
        assertTrue(isRtlParagraph("سلام hello"))
        assertTrue(isRtlParagraph("123 שלום"))
        assertFalse(isRtlParagraph("hello سلام"))
        assertFalse(isRtlParagraph("12:30 ✓"))
        assertFalse(isRtlParagraph(""))
    }
}

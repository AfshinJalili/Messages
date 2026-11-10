package com.afsh.messages

import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem.ThreadDateTime
import com.afsh.messages.models.ThreadItem.ThreadUnreadSeparator
import com.afsh.messages.models.buildThreadItems
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadSpamChecks {
    // All on one day, a minute apart, so only one date header appears.
    private fun message(id: Long, read: Boolean = false) = Message(
        id, "m$id", Telephony.Sms.MESSAGE_TYPE_INBOX, -1, ArrayList(), 1_750_000_000 + id.toInt() * 60, read, 1L, false, null,
        "5550100777", "", "", -1
    )

    private val spam = setOf(2L, 3L, 5L)

    private fun build(messages: List<Message>, hidden: Set<Long> = emptySet()) = buildThreadItems(
        messages = messages,
        isSpam = { it.id in spam },
        isHidden = { it.id in hidden },
    ).filterNot { it is ThreadDateTime }

    @Test
    fun spamShowsInlineWithoutUnreadSeparator() {
        val messages = listOf(message(1, read = true), message(2), message(3), message(4, read = true), message(5))
        val items = build(messages)
        check(items == messages) { "Unexpected items: $items" }
    }

    @Test
    fun unreadSeparatorSkipsSpamAndStartsAtFirstRealUnread() {
        val messages = listOf(message(1, read = true), message(2), message(4), message(5))
        val items = build(messages)
        check(items == listOf(messages[0], messages[1], ThreadUnreadSeparator, messages[2], messages[3])) { "Unexpected items: $items" }
    }

    @Test
    fun hiddenMessagesAreLeftOut() {
        val messages = listOf(message(1, read = true), message(2), message(3), message(4, read = true))
        val items = build(messages, hidden = setOf(3L))
        check(items == listOf(messages[0], messages[1], messages[3])) { "Unexpected items: $items" }
    }
}

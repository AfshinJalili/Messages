package com.afsh.messages

import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem.ThreadDateTime
import com.afsh.messages.models.ThreadItem.ThreadUnreadSeparator
import com.afsh.messages.models.buildThreadItems
import org.junit.Test
import org.junit.runner.RunWith

/** The "New messages" divider stays where it opened after those messages are read. */
@RunWith(AndroidJUnit4::class)
class UnreadDividerChecks {
    // All on one day, a minute apart, so only one date header appears.
    private fun message(id: Long, read: Boolean) = Message(
        id, "m$id", Telephony.Sms.MESSAGE_TYPE_INBOX, -1, ArrayList(), 1_750_000_000 + id.toInt() * 60, read, 1L, false, null,
        "5550100777", "", "", -1
    )

    private fun build(messages: List<Message>, pinned: Set<Long> = emptySet()) = buildThreadItems(
        messages = messages,
        isSpam = { false },
        isHidden = { false },
        isExpanded = { false },
        isNew = { !it.read || it.getStableId() in pinned },
    ).filterNot { it is ThreadDateTime }

    @Test
    fun dividerGoesAboveFirstUnread() {
        val messages = listOf(message(1, read = true), message(2, read = false), message(3, read = false))
        val items = build(messages)
        check(items == listOf(messages[0], ThreadUnreadSeparator, messages[1], messages[2])) { "Unexpected items: $items" }
    }

    @Test
    fun dividerStaysAfterTheVisitReadsThem() {
        val unread = listOf(message(1, read = true), message(2, read = false), message(3, read = false))
        val pinned = unread.filterNot { it.read }.map { it.getStableId() }.toSet()
        val read = unread.map { it.copy(read = true) }
        val items = build(read, pinned)
        check(items == listOf(read[0], ThreadUnreadSeparator, read[1], read[2])) { "Unexpected items: $items" }
    }

    @Test
    fun noDividerOnTheNextVisit() {
        val read = listOf(message(1, read = true), message(2, read = true))
        val items = build(read)
        check(items.none { it == ThreadUnreadSeparator }) { "Unexpected divider: $items" }
    }
}

package org.fossify.messages

import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem.ThreadDateTime
import org.fossify.messages.models.ThreadItem.ThreadSpamGroup
import org.fossify.messages.models.ThreadItem.ThreadUnreadSeparator
import org.fossify.messages.models.buildThreadItems
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadSpamGroupChecks {
    // All on one day, a minute apart, so only one date header appears.
    private fun message(id: Long, read: Boolean = false) = Message(
        id, "m$id", Telephony.Sms.MESSAGE_TYPE_INBOX, -1, ArrayList(), 1_750_000_000 + id.toInt() * 60, read, 1L, false, null,
        "5550100777", "", "", -1
    )

    private val messages = listOf(message(1, read = true), message(2), message(3), message(4, read = true), message(5))
    private val spam = setOf(2L, 3L, 5L)

    private fun build(expanded: Set<Long> = emptySet(), hidden: Set<Long> = emptySet()) = buildThreadItems(
        messages = messages,
        isSpam = { it.id in spam },
        isHidden = { it.id in hidden },
        isExpanded = { it in expanded },
    ).filterNot { it is ThreadDateTime }

    @Test
    fun consecutiveSpamCollapsesIntoOneGroup() {
        val items = build()
        check(items == listOf(messages[0], ThreadSpamGroup(2, listOf(2, 3), false), messages[3], ThreadSpamGroup(5, listOf(5), false))) {
            "Unexpected items: $items"
        }
    }

    @Test
    fun expandedGroupShowsItsMessagesWithoutUnreadSeparator() {
        val items = build(expanded = setOf(2L))
        check(items == listOf(messages[0], ThreadSpamGroup(2, listOf(2, 3), true), messages[1], messages[2], messages[3], ThreadSpamGroup(5, listOf(5), false))) {
            "Unexpected items: $items"
        }
        check(items.none { it == ThreadUnreadSeparator }) { "Unread spam must not start the unread separator" }
    }

    @Test
    fun hiddenMessageDoesNotSplitAGroup() {
        val items = build(hidden = setOf(4L))
        check(items == listOf(messages[0], ThreadSpamGroup(2, listOf(2, 3, 5), false))) { "Unexpected items: $items" }
    }
}

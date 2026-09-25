package org.fossify.messages.models

import android.provider.Telephony
import org.joda.time.DateTime
import org.joda.time.LocalDate

/**
 * Thread item representations for the main thread recyclerview. [Message] is also a [ThreadItem]
 */
sealed class ThreadItem {
    data class ThreadDateTime(val date: Int) : ThreadItem()
    data class ThreadError(val messageId: Long, val messageText: String) : ThreadItem()
    data class ThreadSent(val messageId: Long, val delivered: Boolean) : ThreadItem()
    data class ThreadSending(val messageId: Long) : ThreadItem()
    data object ThreadUnreadSeparator : ThreadItem()

    /** A run of consecutive spam messages, keyed by its first message id. */
    data class ThreadSpamGroup(val key: Long, val messageIds: List<Long>, val expanded: Boolean) : ThreadItem()
}

/**
 * Builds the thread list from [messages] sorted by date. Consecutive spam becomes one [ThreadItem.ThreadSpamGroup];
 * a collapsed group hides its messages. Spam never gets the unread separator, since the Spam screen owns its read state.
 */
fun buildThreadItems(
    messages: List<Message>,
    isSpam: (Message) -> Boolean,
    isHidden: (Message) -> Boolean,
    isExpanded: (groupKey: Long) -> Boolean,
): ArrayList<ThreadItem> {
    val items = ArrayList<ThreadItem>()
    var previousDay: LocalDate? = null
    var hadUnreadItems = false
    val cnt = messages.size
    var spamRunEnd = -1
    var i = 0
    while (i < cnt) {
        val index = i++
        val message = messages.getOrNull(index) ?: continue
        if (isHidden(message)) continue
        val separatorIndex = items.size
        val day = DateTime(message.millis()).toLocalDate()
        if (day != previousDay) {
            items.add(ThreadItem.ThreadDateTime((day.toDateTimeAtStartOfDay().millis / 1000).toInt()))
            previousDay = day
        }

        if (isSpam(message) && index > spamRunEnd) {
            var end = index
            while (end + 1 < cnt && messages[end + 1].let { isSpam(it) || isHidden(it) }) end++
            spamRunEnd = end
            val ids = messages.subList(index, end + 1).filter { isSpam(it) && !isHidden(it) }.map { it.id }
            val expanded = isExpanded(message.id)
            items.add(ThreadItem.ThreadSpamGroup(message.id, ids, expanded))
            if (!expanded) {
                i = end + 1
                continue
            }
        }
        items.add(message)

        if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
            items.add(ThreadItem.ThreadError(message.id, message.body))
        }

        if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
            items.add(ThreadItem.ThreadSending(message.id))
        }

        if (!message.read && message.isReceivedMessage() && !message.isScheduled && !isSpam(message)) {
            if (!hadUnreadItems) {
                items.add(separatorIndex, ThreadItem.ThreadUnreadSeparator)
            }
            hadUnreadItems = true
        }
    }

    return items
}

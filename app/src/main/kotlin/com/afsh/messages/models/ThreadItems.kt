package com.afsh.messages.models

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
}

/**
 * Builds the thread list from [messages] sorted by date. Spam shows inline, but never gets the unread separator,
 * since the Spam screen owns its read state.
 * The separator goes above the first message [isNew] holds; by default that is the first unread one.
 */
fun buildThreadItems(
    messages: List<Message>,
    isSpam: (Message) -> Boolean,
    isHidden: (Message) -> Boolean,
    isNew: (Message) -> Boolean = { !it.read },
): ArrayList<ThreadItem> {
    val items = ArrayList<ThreadItem>()
    var previousDay: LocalDate? = null
    var hadUnreadItems = false
    for (message in messages) {
        if (isHidden(message)) continue
        val separatorIndex = items.size
        val day = DateTime(message.millis()).toLocalDate()
        if (day != previousDay) {
            items.add(ThreadItem.ThreadDateTime((day.toDateTimeAtStartOfDay().millis / 1000).toInt()))
            previousDay = day
        }

        items.add(message)

        if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
            items.add(ThreadItem.ThreadError(message.id, message.body))
        }

        if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
            items.add(ThreadItem.ThreadSending(message.id))
        }

        if (isNew(message) && message.isReceivedMessage() && !message.isScheduled && !isSpam(message)) {
            if (!hadUnreadItems) {
                items.add(separatorIndex, ThreadItem.ThreadUnreadSeparator)
            }
            hadUnreadItems = true
        }
    }

    return items
}

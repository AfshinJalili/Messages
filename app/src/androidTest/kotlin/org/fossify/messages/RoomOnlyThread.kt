package org.fossify.messages

import android.content.Context
import android.provider.Telephony
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A conversation that lives only in Room, for inbox checks that must not touch real threads.
 *
 * It is a scheduled thread with a number no provider thread uses, so reconciliation keeps it; and
 * it holds a pending scheduled message, because reconciliation deletes temporary threads that have
 * none. A fresh install empties the messages table on its first launch, so that runs first, or it
 * would take the pending message (and then the thread) with it.
 */
class RoomOnlyThread(private val context: Context, val conversation: Conversation) {
    private val pending = Message(
        id = conversation.threadId - 1, body = conversation.snippet, type = Telephony.Sms.MESSAGE_TYPE_OUTBOX, status = 0,
        participants = ArrayList(),
        date = (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(1)).toInt(),
        read = true, threadId = conversation.threadId, isMMS = false, attachment = null,
        senderPhoneNumber = conversation.phoneNumber, senderName = "", senderPhotoUri = "", subscriptionId = -1,
        isScheduled = true,
    )

    fun insert() {
        val cleared = CountDownLatch(1)
        context.clearAllMessagesIfNeeded { cleared.countDown() }
        check(cleared.await(CLEANUP_TIMEOUT_S, TimeUnit.SECONDS)) { "First-run message cleanup finishes" }
        context.messagesDB.insertOrUpdate(pending)
        context.conversationsDB.insertOrUpdate(conversation)
    }

    fun delete() {
        context.messagesDB.delete(pending.id)
        context.conversationsDB.deleteThreadId(conversation.threadId)
    }

    private companion object {
        const val CLEANUP_TIMEOUT_S = 5L
    }
}

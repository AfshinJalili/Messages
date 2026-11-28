package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.THREAD_ID

/** A message notification's Delete action: deletes every message the notification shows, then clears it. */
class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val ids = intent.getLongArrayExtra(MESSAGE_IDS) ?: return
        val areMms = intent.getBooleanArrayExtra(MESSAGES_ARE_MMS) ?: return
        if (ids.size != areMms.size) return
        MessagingEvents.clearConversationNotification(threadId)
        goAsyncInBackground {
            // SMS and MMS ids overlap: each id is deleted from its own table.
            val messages = ids.indices.map { ids[it] to areMms[it] }
            messages.forEach { (id, isMms) -> context.deleteMessage(id, isMms) }
            ConversationRepository.from(context).refreshFromTelephony(listOf(threadId))
            messages.forEach { (id, isMms) ->
                MessagingEvents.publish(MessagingEvent.MessageDeleted(threadId = threadId, messageId = id, isMms = isMms))
            }
        }
    }

    companion object {
        private const val MESSAGE_IDS = "message_ids"
        private const val MESSAGES_ARE_MMS = "messages_are_mms"

        /** Deletes the messages of [threadId] with [ids], each one an MMS where [areMms] says so. */
        fun intent(context: Context, threadId: Long, ids: LongArray, areMms: BooleanArray): Intent =
            Intent(context, DeleteSmsReceiver::class.java)
                .putExtra(THREAD_ID, threadId)
                .putExtra(MESSAGE_IDS, ids)
                .putExtra(MESSAGES_ARE_MMS, areMms)
    }
}

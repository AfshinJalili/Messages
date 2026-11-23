package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.IS_MMS
import com.afsh.messages.helpers.MESSAGE_ID
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.THREAD_ID

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            ConversationRepository.from(context).refreshFromTelephony(listOf(threadId))
            MessagingEvents.publish(MessagingEvent.MessageDeleted(threadId = threadId, messageId = messageId, isMms = isMms))
        }
    }
}

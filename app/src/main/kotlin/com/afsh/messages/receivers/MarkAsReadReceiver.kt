package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MARK_AS_READ
import com.afsh.messages.helpers.THREAD_ID

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                goAsyncInBackground {
                    // Publishes MessagingEvent.ReadStateChanged, which clears the notification, without a full reconcile.
                    ConversationRepository.from(context).markThreadRead(threadId)
                }
            }
        }
    }
}

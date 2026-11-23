package com.afsh.messages.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.*
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.REPLY
import com.afsh.messages.helpers.TelephonyReader
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.messaging.sendMessageCompat

class DirectReplyReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        var body = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString() ?: return

        body = body.removeDiacriticsIfNeeded(context.config.useSimpleCharacters)

        if (address != null) {
            var subscriptionId: Int? = null
            val availableSIMs = context.subscriptionManagerCompat().activeSubscriptionInfoList
            if ((availableSIMs?.size ?: 0) > 1) {
                val currentSIMCardIndex = context.config.getUseSIMIdAtNumber(address)
                val wantedId = availableSIMs?.getOrNull(currentSIMCardIndex)
                if (wantedId != null) {
                    subscriptionId = wantedId.subscriptionId
                }
            }

            ensureBackgroundThread {
                var messageId = 0L
                try {
                    context.sendMessageCompat(body, listOf(address), subscriptionId, emptyList())
                    val message = TelephonyReader(context).getMessages(
                        threadId = threadId, includeScheduledMessages = false, limit = 1
                    ).lastOrNull()
                    if (message != null) {
                        context.messagesDB.insertOrUpdate(message)
                        messageId = message.id

                        context.updateLastConversationMessage(threadId)
                    }
                } catch (e: Exception) {
                    context.showErrorToast(e)
                }

                val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
                val bitmap = context.getNotificationBitmap(photoUri)
                Handler(Looper.getMainLooper()).post {
                    context.notificationHelper.appendDirectReplyNotification(
                        messageId = messageId,
                        address = address,
                        body = body,
                        threadId = threadId,
                        bitmap = bitmap,
                    )
                }

                // markThreadMessagesRead also publishes MessagingEvent.ReadStateChanged.
                context.markThreadMessagesRead(threadId)
                context.conversationsDB.markRead(threadId)
                if (messageId != 0L) {
                    MessagingEvents.publish(MessagingEvent.MessageSent(threadId = threadId, messageId = messageId, isMms = false))
                }
            }
        }
    }
}

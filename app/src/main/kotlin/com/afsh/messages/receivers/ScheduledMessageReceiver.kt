package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.SCHEDULED_MESSAGE_ID
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.messaging.sendMessageCompat
import kotlin.time.Duration.Companion.minutes

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "simple.messenger:scheduled.message.receiver"
        )
        wakelock.acquire(1.minutes.inWholeMilliseconds)

        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                handleIntent(context, intent)
            } finally {
                try {
                    if (wakelock.isHeld) wakelock.release()
                } catch (_: Exception) {
                }

                pendingResult.finish()
            }
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(SCHEDULED_MESSAGE_ID, 0L)
        val message = try {
            context.messagesDB.getScheduledMessageWithId(threadId, messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val addresses = message.participants.getAddresses()
        val attachments = message.attachment?.attachments ?: emptyList()

        try {
            Handler(Looper.getMainLooper()).post {
                context.sendMessageCompat(message.body, addresses, message.subscriptionId, attachments)
            }

            // delete temporary conversation and message as it's already persisted to the telephony db now
            context.deleteScheduledMessage(messageId)
            ConversationRepository.from(context).removeFromCache(messageId)
            // Temporary scheduled thread was deleted; ScheduledMessageSent triggers a reconcile so the real Telephony thread appears.
            MessagingEvents.publish(MessagingEvent.ScheduledMessageSent(threadId = threadId, messageId = messageId))
        } catch (e: Exception) {
            context.showErrorToast(e)
        } catch (e: Error) {
            context.showErrorToast(
                e.localizedMessage ?: context.getString(com.afsh.commons.R.string.unknown_error_occurred)
            )
        }
    }
}

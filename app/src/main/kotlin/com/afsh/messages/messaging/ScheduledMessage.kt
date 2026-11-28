package com.afsh.messages.messaging

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.AlarmManagerCompat
import com.afsh.commons.extensions.showErrorToast
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.SCHEDULED_MESSAGE_ID
import com.afsh.messages.models.Message
import com.afsh.messages.receivers.ScheduledMessageReceiver

/**
 * All things related to scheduled messages are here.
 */

/** The alarm names the message only: reconcile and deletion can move a scheduled message to another thread. */
fun Context.getScheduleSendPendingIntent(message: Message): PendingIntent {
    val intent = Intent(this, ScheduledMessageReceiver::class.java)
    intent.putExtra(SCHEDULED_MESSAGE_ID, message.id)

    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(this, message.id.toInt(), intent, flags)
}

fun Context.scheduleMessage(message: Message) {
    val pendingIntent = getScheduleSendPendingIntent(message)
    val triggerAtMillis = message.millis()

    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
}

fun Context.cancelScheduleSendPendingIntent(messageId: Long) {
    val intent = Intent(this, ScheduledMessageReceiver::class.java)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    PendingIntent.getBroadcast(this, messageId.toInt(), intent, flags).cancel()
}

/**
 * Sends a scheduled message once its alarm fires. Room, the transport and the app's events go through
 * [platform]. Blocking: runs on the alarm receiver's worker thread.
 */
class ScheduledMessageSender(private val platform: Platform) {

    /** Room, the transport and the app's events, as sending a due message needs them. Blocking. */
    interface Platform {
        /** The scheduled message [messageId], in whatever thread it is now; null when it is gone. */
        fun scheduledMessage(messageId: Long): Message?
        /** Hands [message] to the SMS or MMS transport. */
        fun send(message: Message)
        fun dropScheduled(messageId: Long)
        /** Drops temporary scheduled thread [threadId] once no scheduled message is left in it; a real thread stays. */
        fun dropEmptyTemporaryThread(threadId: Long)
        /** The scheduled message went; the app reconciles so the real Telephony thread shows up. */
        fun publishSent(threadId: Long, messageId: Long)
        fun failed(error: Throwable)
    }

    /**
     * Sends scheduled message [messageId] to the thread it is in now. The alarm was set with the message
     * alone because reconcile and deletion move scheduled messages between threads without re-arming it.
     */
    fun sendDue(messageId: Long) {
        val message = platform.scheduledMessage(messageId) ?: return
        try {
            platform.send(message)
            // The scheduled message, and a temporary thread it leaves empty, go: it is in Telephony now.
            platform.dropScheduled(messageId)
            platform.dropEmptyTemporaryThread(message.threadId)
            platform.publishSent(message.threadId, messageId)
        } catch (e: Exception) {
            platform.failed(e)
        } catch (e: Error) {
            platform.failed(e)
        }
    }

    companion object {
        fun from(context: Context) = ScheduledMessageSender(AndroidPlatform(context.applicationContext))
    }

    private class AndroidPlatform(private val app: Context) : Platform {
        override fun scheduledMessage(messageId: Long): Message? = try {
            app.messagesDB.getScheduledMessage(messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        override fun send(message: Message) {
            val addresses = message.participants.getAddresses()
            val attachments = message.attachment?.attachments ?: emptyList()
            Handler(Looper.getMainLooper()).post {
                app.sendMessageCompat(message.body, addresses, message.subscriptionId, attachments)
            }
        }

        override fun dropScheduled(messageId: Long) = app.deleteScheduledMessage(messageId)

        override fun dropEmptyTemporaryThread(threadId: Long) {
            val conversations = ConversationRepository.from(app)
            val isTemporary = conversations.cachedConversation(threadId)?.isScheduled == true
            if (isTemporary && app.messagesDB.getScheduledThreadMessages(threadId).isEmpty()) {
                conversations.removeFromCache(threadId)
            }
        }

        override fun publishSent(threadId: Long, messageId: Long) {
            MessagingEvents.publish(MessagingEvent.ScheduledMessageSent(threadId = threadId, messageId = messageId))
        }

        override fun failed(error: Throwable) {
            if (error is Exception) {
                app.showErrorToast(error)
            } else {
                app.showErrorToast(error.localizedMessage ?: app.getString(com.afsh.commons.R.string.unknown_error_occurred))
            }
        }
    }
}

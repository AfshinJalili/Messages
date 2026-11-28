package com.afsh.messages.messaging

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.AlarmManagerCompat
import com.afsh.commons.extensions.showErrorToast
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.SCHEDULED_MESSAGE_ID
import com.afsh.messages.models.Message
import com.afsh.messages.receivers.ScheduledMessageReceiver
import java.util.concurrent.ConcurrentHashMap

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
 * Sends a scheduled message: when its alarm fires, or at once (Send now, or Retry after a failed send).
 * Room, the transport and the app's events go through [platform]. Blocking: runs on a worker thread.
 */
class ScheduledMessageSender(private val platform: Platform) {

    /** Room, the transport and the app's events, as sending a scheduled message needs them. Blocking. */
    interface Platform {
        /** The scheduled message [messageId], in whatever thread it is now; null when it is gone. */
        fun scheduledMessage(messageId: Long): Message?
        /**
         * Hands [message] to the SMS or MMS transport. Throws when it did not go; a [StoredSendException]
         * when Telephony holds the whole message all the same.
         */
        fun send(message: Message)
        fun dropScheduled(messageId: Long)
        /** Drops temporary scheduled thread [threadId] once no scheduled message is left in it; a real thread stays. */
        fun dropEmptyTemporaryThread(threadId: Long)
        /** The scheduled message went; the app reconciles so the real Telephony thread shows up. */
        fun publishSent(threadId: Long, messageId: Long)
        /** Keeps scheduled message [messageId] for the person, marked failed (see [Message.isFailedSchedule]). */
        fun markFailed(messageId: Long)
        /** Tells the person [message] was not sent; it is in Telephony's thread when [inTelephony], else in its own. */
        fun publishFailed(message: Message, error: Throwable, inTelephony: Boolean)
    }

    /**
     * Sends scheduled message [messageId] to the thread it is in now, on [subscriptionId] or its own SIM.
     * The alarm names the message alone because reconcile and deletion move scheduled messages between
     * threads without setting it again. The scheduled copy goes only once the message is on the device;
     * one that never got there stays, marked failed. True when it went; false when it did not, or when
     * another send of it is still running.
     */
    fun send(messageId: Long, subscriptionId: Int? = null): Boolean {
        // Send now, Retry and the alarm can each start while another is sending; only the first one sends.
        if (!sending.add(messageId)) return false
        try {
            return sendOnce(messageId, subscriptionId)
        } finally {
            sending.remove(messageId)
        }
    }

    private fun sendOnce(messageId: Long, subscriptionId: Int?): Boolean {
        val scheduled = platform.scheduledMessage(messageId) ?: return false
        val message = if (subscriptionId == null) scheduled else scheduled.copy(subscriptionId = subscriptionId)
        val failure = try {
            platform.send(message)
            null
        } catch (e: Exception) {
            e
        } catch (e: Error) {
            e
        }
        if (failure != null && failure !is StoredSendException) {
            platform.markFailed(messageId)
            platform.publishFailed(message, failure, inTelephony = false)
            return false
        }
        // On the device now, sent or failed there: the scheduled copy, and a temporary thread it leaves empty, go.
        platform.dropScheduled(messageId)
        platform.dropEmptyTemporaryThread(message.threadId)
        platform.publishSent(message.threadId, messageId)
        if (failure != null) platform.publishFailed(message, failure, inTelephony = true)
        return failure == null
    }

    companion object {
        /** Scheduled messages being sent in this process, across every sender. */
        private val sending: MutableSet<Long> = ConcurrentHashMap.newKeySet()

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
            app.sendMessage(message.body, addresses, message.subscriptionId, message.attachment?.attachments.orEmpty())
            app.reportSendMessageUsage(addresses)
        }

        override fun dropScheduled(messageId: Long) = app.deleteScheduledMessage(messageId)

        override fun dropEmptyTemporaryThread(threadId: Long) {
            try {
                val conversations = ConversationRepository.from(app)
                val isTemporary = conversations.cachedConversation(threadId)?.isScheduled == true
                if (isTemporary && app.messagesDB.getScheduledThreadMessages(threadId).isEmpty()) {
                    conversations.removeFromCache(threadId)
                }
            } catch (e: Exception) {
                app.showErrorToast(e)
            }
        }

        override fun publishSent(threadId: Long, messageId: Long) {
            MessagingEvents.publish(MessagingEvent.ScheduledMessageSent(threadId = threadId, messageId = messageId))
        }

        override fun markFailed(messageId: Long) {
            try {
                app.messagesDB.setScheduledType(messageId, Telephony.Sms.MESSAGE_TYPE_FAILED)
            } catch (e: Exception) {
                // Room is failing too. The row stays queued and past its time, so it is hidden and soon
                // cleaned up; the person still hears it was not sent.
                app.showErrorToast(e)
            }
        }

        override fun publishFailed(message: Message, error: Throwable, inTelephony: Boolean) {
            val addresses = message.participants.getAddresses()
            // The open thread shows a failure only with a reason.
            val reason = (if (error is Exception) app.sendErrorText(error) else error.localizedMessage)
                ?: app.getString(com.afsh.commons.R.string.unknown_error_occurred)
            MessagingEvents.publish(
                MessagingEvent.SendFailed(
                    // A temporary thread may be gone; Telephony keeps the stored copy in the real one.
                    threadId = if (inTelephony) app.getThreadId(addresses.toSet()) else message.threadId,
                    messageId = message.id,
                    isMms = message.isMMS,
                    recipientName = message.participants.getThreadTitle(),
                    reason = reason,
                )
            )
        }
    }
}

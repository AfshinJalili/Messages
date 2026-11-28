package com.afsh.messages.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.toast
import com.afsh.messages.extensions.*
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.REPLY
import com.afsh.messages.helpers.TelephonyReader
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.messaging.sendMessageCompat
import com.afsh.messages.models.Message

/** A message notification's inline reply: sends it to everyone in the thread, as the thread screen would. */
class DirectReplyReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        var body = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString() ?: return

        body = body.removeDiacriticsIfNeeded(context.config.useSimpleCharacters)

        goAsyncInBackground {
            // A group thread's notification names only the sender: the reply goes to the whole thread,
            // and sendMessage picks SMS or group MMS from its settings, as for the thread screen.
            val recipients = context.getThreadParticipants(threadId, null).getAddresses()
                .ifEmpty { listOfNotNull(sender) }
            val sent = if (recipients.isEmpty()) {
                Log.w(TAG, "No recipients for an inline reply in thread $threadId")
                context.toast(com.afsh.commons.R.string.unknown_error_occurred)
                null
            } else {
                send(context, body, recipients)
            }

            // Replying reads the thread. The ReadStateChanged this publishes clears the notification,
            // which also ends the reply's spinner, whether or not the thread is muted or open.
            ConversationRepository.from(context).markThreadRead(threadId)
            sent?.let {
                MessagingEvents.publish(MessagingEvent.MessageSent(threadId = it.threadId, messageId = it.id, isMms = it.isMMS))
            }
        }
    }

    /** Sends [body] to [recipients] and returns the stored message, or null when it is not on the device. */
    @SuppressLint("MissingPermission")
    private fun send(context: Context, body: String, recipients: List<String>): Message? {
        var subscriptionId: Int? = null
        val availableSIMs = context.subscriptionManagerCompat().activeSubscriptionInfoList
        if ((availableSIMs?.size ?: 0) > 1) {
            val currentSIMCardIndex = context.config.getUseSIMIdAtNumber(recipients.first())
            val wantedId = availableSIMs?.getOrNull(currentSIMCardIndex)
            if (wantedId != null) {
                subscriptionId = wantedId.subscriptionId
            }
        }

        return try {
            context.sendMessageCompat(body, recipients, subscriptionId, emptyList())
            // Read the reply back from the thread it went to; the newest message of the notification's
            // thread may be the incoming one, or an older reply when this one was not stored.
            val sentThreadId = context.getThreadId(recipients.toSet())
            TelephonyReader(context).getMessages(
                threadId = sentThreadId, includeScheduledMessages = false, limit = READ_BACK_LIMIT,
            ).lastOrNull { !it.isReceivedMessage() && it.body == body }?.also { sent ->
                context.messagesDB.insertOrUpdate(sent)
                ConversationRepository.from(context).refreshFromTelephony(listOf(sent.threadId))
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
            null
        }
    }

    private companion object {
        const val TAG = "DirectReplyReceiver"

        /** Newest messages read back to find the reply: a message may arrive while it sends. */
        const val READ_BACK_LIMIT = 5
    }
}

package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.models.PhoneNumber
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.extensions.getNameFromAddress
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.insertNewSMS
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.helpers.ConversationPreview
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.IncomingSpamClassifier
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.models.Message
import com.afsh.messages.models.SpamMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext

        ensureBackgroundThread {
            try {
                // A part the platform couldn't parse comes back null. Storing the readable rest beats losing it all.
                val decoded = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
                val parts = decoded.filterNotNull()
                if (parts.size < decoded.size) {
                    Log.w(TAG, "${decoded.size - parts.size} of ${decoded.size} parts of an incoming SMS couldn't be read")
                }
                if (parts.isEmpty()) return@ensureBackgroundThread

                // Stored under the localized "Unknown" rather than dropped, as the AOSP Mms app did.
                val address = parts.last().originatingAddress?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(com.afsh.commons.R.string.unknown)
                val subject = parts.last().pseudoSubject.orEmpty()
                val status = parts.last().status
                val body = buildString { parts.forEach { append(it.messageBody.orEmpty()) } }
                val date = System.currentTimeMillis()

                val threadId = appContext.getThreadId(address)
                val subscriptionId = intent.getIntExtra("subscription", -1)

                handleMessageSync(
                    context = appContext,
                    address = address,
                    subject = subject,
                    body = body,
                    date = date,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    status = status,
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleMessageSync(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int = 0,
        threadId: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
        subscriptionId: Int,
        status: Int,
    ) {
        // Stored first, so a lookup or write below that fails can't lose the message.
        val newMessageId = context.insertNewSMS(
            address = address,
            subject = subject,
            body = body,
            date = date,
            read = read,
            threadId = threadId,
            type = type,
            subscriptionId = subscriptionId
        )
        if (newMessageId == 0L) {
            // Telephony refused the row (a full disk, say). A Room row or an alert would point at nothing.
            Log.e(TAG, "Couldn't store an incoming SMS in Telephony")
            return
        }

        val spamReason = IncomingSpamClassifier.silenceReason(context, address, body)
        // Marked before the conversation row is written, so a spam-only thread never shows in the inbox.
        if (spamReason != null) {
            context.messagesDB.insertSpamMarker(SpamMessage(id = newMessageId, threadId = threadId, reason = spamReason))
        }

        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)

        val conversations = ConversationRepository.from(context)
        conversations.conversation(threadId)?.let { conv ->
            runCatching { conversations.insertOrUpdate(conv) }
        }

        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
        }

        val participant = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(PhoneNumber(value = address, type = 0, label = "", normalizedNumber = address)),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )

        val message = Message(
            id = newMessageId,
            body = body,
            type = type,
            status = status,
            participants = arrayListOf(participant),
            date = (date / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = address,
            senderName = senderName,
            senderPhotoUri = photoUri,
            subscriptionId = subscriptionId
        )

        context.messagesDB.insertOrUpdate(message)
        // Conversation write above may have refreshed before this row existed in Room.
        ConversationPreview.refresh(context, threadId)

        if (context.shouldUnarchive()) {
            conversations.setArchived(threadId, false)
        }

        // Room already has this thread; MessagingEvent.IncomingMessage never triggers a full reconcile.
        MessagingEvents.publish(
            MessagingEvent.IncomingMessage(
                threadId = threadId,
                messageId = newMessageId,
                isMms = false,
                isSpam = spamReason != null,
                address = address,
                senderName = senderName,
                body = body,
                photoUri = photoUri,
            )
        )
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}

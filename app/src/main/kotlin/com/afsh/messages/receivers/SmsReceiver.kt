package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import com.afsh.messages.extensions.getConversations
import com.afsh.messages.extensions.getNameFromAddress
import com.afsh.messages.extensions.getNotificationBitmap
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.insertNewSMS
import com.afsh.messages.extensions.insertOrUpdateConversation
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.extensions.showReceivedMessageNotification
import com.afsh.messages.extensions.updateConversationArchivedStatus
import com.afsh.messages.helpers.IncomingSpamClassifier
import com.afsh.messages.helpers.refreshConversations
import com.afsh.messages.helpers.refreshMessages
import com.afsh.messages.models.Message
import com.afsh.messages.models.SpamMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext

        ensureBackgroundThread {
            try {
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (parts.isEmpty()) return@ensureBackgroundThread

                val address = parts.last().originatingAddress.orEmpty()
                if (address.isBlank()) return@ensureBackgroundThread
                val subject = parts.last().pseudoSubject.orEmpty()
                val status = parts.last().status
                val body = buildString { parts.forEach { append(it.messageBody.orEmpty()) } }
                val date = System.currentTimeMillis()

                val spamReason = IncomingSpamClassifier.silenceReason(appContext, address, body)
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
                    spamReason = spamReason,
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
        spamReason: Int?,
    ) {
        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)

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

        // Marked before the conversation row is written, so a spam-only thread never shows in the inbox.
        if (spamReason != null && newMessageId != 0L) {
            context.messagesDB.insertSpamMarker(SpamMessage(id = newMessageId, threadId = threadId, reason = spamReason))
        }

        context.getConversations(threadId).firstOrNull()?.let { conv ->
            runCatching { context.insertOrUpdateConversation(conv) }
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

        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(threadId, false)
        }

        refreshMessages()
        refreshConversations()

        if (spamReason == null) {
            context.showReceivedMessageNotification(
                messageId = newMessageId,
                address = address,
                senderName = senderName,
                body = body,
                threadId = threadId,
                bitmap = bitmap
            )
        }
    }
}

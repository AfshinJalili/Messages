package com.afsh.messages.receivers

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import com.klinker.android.send_message.MmsReceivedReceiver
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.getNameFromAddress
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.IncomingSpamClassifier
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.TelephonyReader
import com.afsh.messages.models.Message

/**
 * Leaves the library's isAddressBlocked and isContentBlocked hooks at false: a yes there deletes
 * the downloaded MMS for good. [IncomingSpamClassifier] decides once the MMS is stored, as for SMS.
 */
class MmsReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        // Read the row the library just stored. Each download finishes on its own thread, so the
        // newest MMS may be another download or an outgoing message.
        val messageId = runCatching { ContentUris.parseId(messageUri) }.getOrDefault(-1L)
        val mms = TelephonyReader(context).getMmsById(messageId)
        if (mms == null) {
            Log.w(TAG, "Couldn't read the downloaded MMS $messageUri")
            return
        }
        val address = mms.getSender()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: ""
        ensureBackgroundThread {
            handleMmsMessage(context, mms, address)
        }
    }

    override fun onError(context: Context, error: String) {
        context.showErrorToast(context.getString(R.string.couldnt_download_mms))
    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        address: String,
    ) {
        // MMS spam is only silenced: it stays in Telephony and in the inbox, since spam markers are SMS-only.
        val spamReason = IncomingSpamClassifier.silenceReason(context, address, mms.body)
        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
        }
        val firstAttachment = mms.attachment?.attachments?.firstOrNull()
        val attachmentUri = firstAttachment?.getUri()?.toString()

        val conversations = ConversationRepository.from(context)
        val conversation = conversations.conversation(mms.threadId)
        if (conversation != null) {
            runCatching { conversations.insertOrUpdate(conversation) }
            if (context.shouldUnarchive()) {
                conversations.setArchived(mms.threadId, false)
            }
            InboxRepository.refreshUnreadCounts(context, listOf(mms.threadId))
        }

        // Room already has this thread; MessagingEvent.IncomingMessage never triggers a full reconcile.
        MessagingEvents.publish(
            MessagingEvent.IncomingMessage(
                threadId = mms.threadId,
                messageId = mms.id,
                isMms = true,
                isSpam = spamReason != null,
                address = address,
                senderName = senderName,
                body = mms.body,
                attachmentUri = attachmentUri,
            )
        )
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}

package com.afsh.messages.receivers

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.getConversations
import com.afsh.messages.extensions.getLatestMMS
import com.afsh.messages.extensions.getNameFromAddress
import com.afsh.messages.extensions.insertOrUpdateConversation
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.extensions.showReceivedMessageNotification
import com.afsh.messages.extensions.updateConversationArchivedStatus
import com.afsh.messages.helpers.IncomingSpamClassifier
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.ReceiverUtils.isMessageFilteredOut
import com.afsh.messages.helpers.refreshConversations
import com.afsh.messages.helpers.refreshMessages
import com.afsh.messages.models.Message

class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        if (context.isNumberBlocked(address)) return true
        if (context.baseConfig.blockUnknownNumbers) {
            return context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use { cursor ->
                SimpleContactsHelper(context).existsSync(address, cursor) == ContactLookupResult.NotFound
            }
        }

        return false
    }

    override fun isContentBlocked(context: Context, content: String): Boolean {
        return isMessageFilteredOut(context, content)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: ""
        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            handleMmsMessage(context, mms, size, address)
        }
    }

    override fun onError(context: Context, error: String) {
        context.showErrorToast(context.getString(R.string.couldnt_download_mms))
    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        // MMS spam is only silenced: it stays in Telephony and in the inbox, since spam markers are SMS-only.
        val spamReason = IncomingSpamClassifier.silenceReason(context, address, mms.body)
        if (spamReason == null) {
            val glideBitmap = try {
                Glide.with(context)
                    .asBitmap()
                    .load(mms.attachment!!.attachments.first().getUri())
                    .centerCrop()
                    .into(size, size)
                    .get()
            } catch (e: Exception) {
                null
            }

            val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
                context.getNameFromAddress(address, it)
            }

            context.showReceivedMessageNotification(
                messageId = mms.id,
                address = address,
                senderName = senderName,
                body = mms.body,
                threadId = mms.threadId,
                bitmap = glideBitmap
            )
        }

        val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return
        runCatching { context.insertOrUpdateConversation(conversation) }
        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(mms.threadId, false)
        }
        InboxRepository.refreshUnreadCounts(context, listOf(mms.threadId))
        refreshMessages()
        refreshConversations()
    }
}

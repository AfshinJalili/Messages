package com.afsh.messages.receivers

import com.afsh.messages.helpers.ConversationRepository
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.getNameFromAddress
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.messagingUtils
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.TelephonyReader

/** Handles updating databases and publishing send outcomes when an SMS message is sent or fails. */
class SmsStatusSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val messageUri: Uri? = intent.data
        val type = if (receiverResultCode == Activity.RESULT_OK) {
            Sms.MESSAGE_TYPE_SENT
        } else {
            Sms.MESSAGE_TYPE_FAILED
        }
        context.messagingUtils.updateSmsMessageSendingStatus(messageUri, type)
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val messageUri = intent.data ?: return
        val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: 0L
        val errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)

        ensureBackgroundThread {
            val succeeded = receiverResultCode == Activity.RESULT_OK
            val type = if (succeeded) Sms.MESSAGE_TYPE_SENT else Sms.MESSAGE_TYPE_FAILED
            context.messagesDB.updateType(messageId, type)

            val address = TelephonyReader(context).getSmsAddress(messageId)
            val threadId = ConversationRepository.from(context).smsThreadId(messageId) ?: context.getThreadId(address)

            if (succeeded) {
                MessagingEvents.publish(
                    MessagingEvent.MessageSent(
                        threadId = threadId,
                        messageId = messageId,
                        isMms = false,
                    )
                )
            } else {
                val recipientName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
                    context.getNameFromAddress(address, it)
                }
                val reason = context.messagingUtils.sendErrorReason(receiverResultCode, errorCode).orEmpty()
                MessagingEvents.publish(
                    MessagingEvent.SendFailed(
                        threadId = threadId,
                        messageId = messageId,
                        isMms = false,
                        recipientName = recipientName,
                        reason = reason,
                    )
                )
            }
        }
    }
}

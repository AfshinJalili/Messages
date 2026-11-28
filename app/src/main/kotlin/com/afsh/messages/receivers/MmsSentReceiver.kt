package com.afsh.messages.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.ConversationSync
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import java.io.File
import java.io.IOException

/** Handles updating databases and publishing send outcomes when an MMS message is sent or fails. */
class MmsSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI))
        val originalResentMessageId = intent.getLongExtra(EXTRA_ORIGINAL_RESENT_MESSAGE_ID, -1L)
        val messageBox = if (receiverResultCode == Activity.RESULT_OK) {
            Telephony.Mms.MESSAGE_BOX_SENT
        } else {
            Telephony.Mms.MESSAGE_BOX_FAILED
        }

        val values = ContentValues(1).apply {
            put(Telephony.Mms.MESSAGE_BOX, messageBox)
        }

        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (e: SQLiteException) {
            context.showErrorToast(e)
        }

        // In case of resent message, delete original to prevent duplication
        if (originalResentMessageId != -1L) {
            context.deleteMessage(originalResentMessageId, true)
        }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null && isSentPduFile(filePath, context.cacheDir)) {
            File(filePath).delete()
        }
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = intent.getStringExtra(EXTRA_CONTENT_URI)?.let(Uri::parse)
        val messageId = uri?.lastPathSegment?.toLongOrNull() ?: 0L
        ensureBackgroundThread {
            // Without a full reconcile, Room still needs this thread's snippet/date/unread.
            val threadId = ConversationSync.syncThreadForMmsUri(context, uri) ?: 0L
            if (receiverResultCode == Activity.RESULT_OK) {
                MessagingEvents.publish(
                    MessagingEvent.MessageSent(
                        threadId = threadId,
                        messageId = messageId,
                        isMms = true,
                    )
                )
            } else {
                val recipientName = context.conversationsDB.getConversationWithThreadId(threadId)?.title
                    ?.ifEmpty { null }
                    ?: ConversationRepository.from(context).conversation(threadId)?.title
                    ?: ""
                val reason = context.getString(R.string.unknown_error_occurred_sending_message, receiverResultCode)
                MessagingEvents.publish(
                    MessagingEvent.SendFailed(
                        threadId = threadId,
                        messageId = messageId,
                        isMms = true,
                        recipientName = recipientName,
                        reason = reason,
                    )
                )
            }
        }
    }

    companion object {
        /**
         * android-smsmms writes the PDU it sends to `cacheDir/send.<n>.dat` and names it in the sent
         * intent. Only such a file is deleted, whatever path an intent names.
         */
        internal fun isSentPduFile(path: String, cacheDir: File): Boolean = try {
            val file = File(path).canonicalFile
            file.parentFile == cacheDir.canonicalFile && SENT_PDU_NAME.matches(file.name)
        } catch (_: IOException) {
            false
        }

        private val SENT_PDU_NAME = Regex("""send\.\d+\.dat""")

        private const val EXTRA_CONTENT_URI = "content_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_ORIGINAL_RESENT_MESSAGE_ID = "original_message_id"
    }
}

package com.afsh.messages.helpers

import android.content.Context
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.MONTH_SECONDS
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.models.RecycleBinMessage

/**
 * Owns the recycle bin: soft-deleting messages, restoring them, and erasing them for good.
 *
 * A message in the bin keeps its Room row; the bin only holds its id and the time it was binned
 * (`deleted_ts`, from [clock]). Every move or restore refreshes the conversation's preview so the
 * inbox never shows a binned message. Erasing goes through [deleteMessage], which removes the
 * message from Telephony and Room.
 */
class RecycleBinRepository(
    private val dao: MessagesDao,
    private val deleteMessage: (id: Long, isMms: Boolean) -> Unit,
    private val refreshPreview: (threadId: Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (Exception) -> Unit = {},
) {

    fun moveToRecycleBin(messageId: Long) {
        try {
            val threadId = dao.getThreadIdForMessage(messageId)
            dao.insertRecycleBinEntry(RecycleBinMessage(messageId, clock()))
            if (threadId != null) refreshPreview(threadId)
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun restore(messageId: Long) {
        try {
            val threadId = dao.getThreadIdForMessage(messageId)
            dao.deleteFromRecycleBin(messageId)
            if (threadId != null) refreshPreview(threadId)
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** Takes every binned message of [threadId] out of the bin. */
    fun restoreConversation(threadId: Long) {
        dao.deleteThreadMessagesFromRecycleBin(threadId)
        refreshPreview(threadId)
    }

    /** Erases every binned message of [threadId] for good. */
    fun emptyConversation(threadId: Long) {
        dao.getThreadMessagesFromRecycleBin(threadId).forEach { deleteMessage(it.id, it.isMMS) }
    }

    /** Erases every binned message for good. */
    fun emptyAll() {
        dao.getAllRecycleBinMessages().forEach { deleteMessage(it.id, it.isMMS) }
    }

    /** Erases messages binned more than [RETENTION_MS] ago. A failure leaves the bin as it is. */
    fun purgeExpired() {
        try {
            dao.getOldRecycleBinMessages(timestamp = clock() - RETENTION_MS)
                .forEach { deleteMessage(it.id, it.isMMS) }
        } catch (_: Exception) {
        }
    }

    companion object {
        /** How long a message stays in the bin before [purgeExpired] erases it: 30 days. */
        const val RETENTION_MS = MONTH_SECONDS * 1000L

        fun from(context: Context): RecycleBinRepository {
            val app = context.applicationContext
            return RecycleBinRepository(
                dao = app.getMessagesDB().MessagesDao(),
                deleteMessage = { id, isMms -> app.deleteMessage(id, isMms) },
                refreshPreview = { threadId -> ConversationPreview.refresh(app, threadId) },
                onError = { e -> app.showErrorToast(e) },
            )
        }
    }
}

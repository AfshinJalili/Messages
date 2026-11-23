package com.afsh.messages.helpers

import android.content.Context
import com.afsh.commons.extensions.showErrorToast
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.interfaces.DraftsDao
import com.afsh.messages.models.Draft

/**
 * Owns the unsent text of each conversation, keyed by Telephony thread id. Reads never throw: a
 * failed read is an empty draft. A failed write is handed to [onError].
 */
class DraftsRepository(
    private val dao: DraftsDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (Exception) -> Unit = {},
) {

    /** The draft text for [threadId], or "" when there is none. */
    fun get(threadId: Long): String =
        try {
            dao.getDraftById(threadId)?.body.orEmpty()
        } catch (_: Exception) {
            ""
        }

    /** Every draft, keyed by thread id. Empty when the read fails. */
    fun getAll(): Map<Long, String> =
        try {
            dao.getAll().associate { it.threadId to it.body }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }

    fun save(threadId: Long, body: String) {
        try {
            dao.insertOrUpdate(Draft(threadId = threadId, body = body, date = clock()))
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun delete(threadId: Long) {
        try {
            dao.delete(threadId)
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Keeps the composer's state as the thread's draft. Only plain text is kept: empty text, or
     * text with attachments, removes the draft.
     */
    fun store(threadId: Long, text: String, hasAttachments: Boolean) {
        if (text.isNotEmpty() && !hasAttachments) save(threadId, text) else delete(threadId)
    }

    companion object {
        fun from(context: Context): DraftsRepository {
            val app = context.applicationContext
            return DraftsRepository(
                dao = app.getMessagesDB().DraftsDao(),
                onError = { e ->
                    e.printStackTrace()
                    app.showErrorToast(e)
                },
            )
        }
    }
}

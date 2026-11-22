package com.afsh.messages.helpers

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.afsh.messages.extensions.getConversations
import com.afsh.messages.extensions.insertOrUpdateConversation
import com.afsh.commons.extensions.getLongValue

/**
 * Targeted Room updates used by message receivers that skip a full Telephony reconcile.
 * Each path writes the affected conversation (and sometimes unread) so the inbox LiveData,
 * badges, and open thread still refresh without a full Telephony pass.
 */
object ConversationSync {

    /** Pull one thread from Telephony into Room (snippet, date, unread, title, photo). */
    fun syncThread(context: Context, threadId: Long) {
        if (threadId == 0L) return
        val conversation = context.getConversations(threadId).firstOrNull() ?: return
        runCatching { context.insertOrUpdateConversation(conversation) }
    }

    /** Resolve the Telephony thread id for an MMS content uri, then [syncThread]. Returns the thread id, if found. */
    fun syncThreadForMmsUri(context: Context, messageUri: Uri?): Long? {
        val threadId = threadIdForMms(context, messageUri) ?: return null
        syncThread(context, threadId)
        InboxRepository.refreshUnreadCounts(context, listOf(threadId))
        return threadId
    }

    private fun threadIdForMms(context: Context, messageUri: Uri?): Long? {
        if (messageUri == null) return null
        return try {
            context.contentResolver.query(
                messageUri,
                arrayOf(Telephony.Mms.THREAD_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLongValue(Telephony.Mms.THREAD_ID) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

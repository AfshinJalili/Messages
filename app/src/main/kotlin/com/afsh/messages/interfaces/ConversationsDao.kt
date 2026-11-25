package com.afsh.messages.interfaces

import com.afsh.messages.models.ThreadUnreadCount
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.ConversationWithSnippetOverride

// Inbox and archive read denormalized preview_* / all_spam columns. Correlated per-row
// subqueries over messages belonged to an earlier schema and must not return here.
private const val PREVIEW_COLUMNS = "preview_snippet as new_snippet, preview_date as new_date, *"

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT $PREVIEW_COLUMNS FROM conversations WHERE archived = 0 AND all_spam = 0")
    fun getNonArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getNonArchived(): List<Conversation> {
        return getNonArchivedWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT $PREVIEW_COLUMNS FROM conversations WHERE archived = 0 AND all_spam = 0")
    fun observeNonArchivedWithLatestSnippet(): LiveData<List<ConversationWithSnippetOverride>>

    @Query("SELECT $PREVIEW_COLUMNS FROM conversations WHERE archived = 1 AND all_spam = 0")
    fun getAllArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllArchived(): List<Conversation> {
        return getAllArchivedWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) as new_snippet, NULL as new_date, * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getAllWithMessagesInRecycleBinWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllWithMessagesInRecycleBin(): List<Conversation> {
        return getAllWithMessagesInRecycleBinWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT * FROM conversations")
    fun getAll(): List<Conversation>

    @Query("SELECT COUNT(*) FROM conversations")
    fun getCount(): Int

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query(
        """SELECT $PREVIEW_COLUMNS FROM conversations WHERE all_spam = 0
        AND (title LIKE :pattern ESCAPE '\' OR phone_number LIKE :pattern ESCAPE '\')
        AND (preview_snippet IS NOT NULL
            OR NOT EXISTS (SELECT 1 FROM messages WHERE messages.thread_id = conversations.thread_id))
        ORDER BY date DESC"""
    )
    fun searchPeople(pattern: String): List<ConversationWithSnippetOverride>

    @Query("UPDATE conversations SET read = 1, unread_count = 0 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = (:count = 0), unread_count = :count WHERE thread_id = :threadId")
    fun updateUnreadCount(threadId: Long, count: Int)

    /** Every thread's stored unread count, without the inbox's per-row subqueries. */
    @Query("SELECT thread_id, unread_count FROM conversations WHERE archived = 0")
    fun getUnreadCounts(): List<ThreadUnreadCount>

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET archived = 1 WHERE thread_id = :threadId")
    fun moveToArchive(threadId: Long)

    @Query("UPDATE conversations SET archived = 0 WHERE thread_id = :threadId")
    fun unarchive(threadId: Long)

    @Query(
        "UPDATE conversations SET preview_snippet = :snippet, preview_date = :date, all_spam = :allSpam WHERE thread_id = :threadId"
    )
    fun updatePreview(threadId: Long, snippet: String?, date: Int?, allSpam: Boolean)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}

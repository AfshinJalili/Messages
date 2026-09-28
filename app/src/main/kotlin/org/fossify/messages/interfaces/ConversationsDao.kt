package org.fossify.messages.interfaces

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationWithSnippetOverride

private const val LATEST_VISIBLE_MESSAGE = "FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id " +
    "WHERE recycle_bin_messages.id IS NULL AND messages.thread_id = conversations.thread_id " +
    "AND NOT (messages.is_mms = 0 AND messages.id IN (SELECT id FROM spam_messages)) ORDER BY messages.date DESC LIMIT 1"

private const val THREAD_SPAM_COUNT = "(SELECT COUNT(*) FROM spam_messages WHERE spam_messages.thread_id = conversations.thread_id)"

// Only threads with spam take their date from Room, so drafts and scheduled dates keep working elsewhere.
private const val VISIBLE_COLUMNS = "(SELECT body $LATEST_VISIBLE_MESSAGE) as new_snippet, " +
    "CASE WHEN $THREAD_SPAM_COUNT > 0 THEN (SELECT date $LATEST_VISIBLE_MESSAGE) END as new_date, *"

// message_count comes from Telephony and includes MMS, so any unmarked message keeps the thread visible.
private const val NOT_ALL_SPAM = "NOT (message_count > 0 AND $THREAD_SPAM_COUNT >= message_count)"

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT $VISIBLE_COLUMNS FROM conversations WHERE archived = 0 AND $NOT_ALL_SPAM")
    fun getNonArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getNonArchived(): List<Conversation> {
        return getNonArchivedWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT $VISIBLE_COLUMNS FROM conversations WHERE archived = 0 AND $NOT_ALL_SPAM")
    fun observeNonArchivedWithLatestSnippet(): LiveData<List<ConversationWithSnippetOverride>>

    @Query("SELECT $VISIBLE_COLUMNS FROM conversations WHERE archived = 1 AND $NOT_ALL_SPAM")
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

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query(
        """SELECT * FROM conversations WHERE $NOT_ALL_SPAM
        AND (title LIKE :pattern ESCAPE '\' OR phone_number LIKE :pattern ESCAPE '\')
        AND EXISTS (SELECT 1 $LATEST_VISIBLE_MESSAGE)
        ORDER BY date DESC"""
    )
    fun searchPeople(pattern: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1, unread_count = 0 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = (:count = 0), unread_count = :count WHERE thread_id = :threadId")
    fun updateUnreadCount(threadId: Long, count: Int)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET archived = 1 WHERE thread_id = :threadId")
    fun moveToArchive(threadId: Long)

    @Query("UPDATE conversations SET archived = 0 WHERE thread_id = :threadId")
    fun unarchive(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}

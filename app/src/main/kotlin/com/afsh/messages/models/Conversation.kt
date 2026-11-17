package com.afsh.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations", indices = [(Index(value = ["thread_id"], unique = true))])
data class Conversation(
    @PrimaryKey @ColumnInfo(name = "thread_id") var threadId: Long,
    @ColumnInfo(name = "snippet") var snippet: String,
    @ColumnInfo(name = "date") var date: Int,
    @ColumnInfo(name = "read") var read: Boolean,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "photo_uri") var photoUri: String,
    @ColumnInfo(name = "is_group_conversation") var isGroupConversation: Boolean,
    @ColumnInfo(name = "phone_number") var phoneNumber: String,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false,
    @ColumnInfo(name = "uses_custom_title") var usesCustomTitle: Boolean = false,
    @ColumnInfo(name = "archived") var isArchived: Boolean = false,
    @ColumnInfo(name = "unread_count") var unreadCount: Int = 0,
    @ColumnInfo(name = "message_count") var messageCount: Int = 0,
    /** Latest non-spam, non-recycled body; null keeps [snippet] for the inbox. */
    @ColumnInfo(name = "preview_snippet") var previewSnippet: String? = null,
    /** When the thread has spam, the latest visible message date; otherwise null keeps [date]. */
    @ColumnInfo(name = "preview_date") var previewDate: Int? = null,
    /** True when every Telephony message is a spam SMS; the inbox hides the row. */
    @ColumnInfo(name = "all_spam") var allSpam: Boolean = false,
) {

    companion object {
        fun areItemsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.threadId == new.threadId
        }

        fun areContentsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.snippet == new.snippet &&
                old.date == new.date &&
                old.read == new.read &&
                old.title == new.title &&
                old.photoUri == new.photoUri &&
                old.isGroupConversation == new.isGroupConversation &&
                old.phoneNumber == new.phoneNumber &&
                old.unreadCount == new.unreadCount &&
                old.isArchived == new.isArchived &&
                old.isScheduled == new.isScheduled &&
                old.usesCustomTitle == new.usesCustomTitle &&
                old.messageCount == new.messageCount &&
                old.previewSnippet == new.previewSnippet &&
                old.previewDate == new.previewDate &&
                old.allSpam == new.allSpam
        }
    }
}

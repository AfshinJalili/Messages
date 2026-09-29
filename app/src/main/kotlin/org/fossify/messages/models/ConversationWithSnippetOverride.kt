package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ConversationWithSnippetOverride(
    @ColumnInfo(name = "new_snippet") val snippet: String?,
    @ColumnInfo(name = "new_date") val date: Int?,
    @Embedded val conversation: Conversation
) {
    fun toConversation() =
        if (snippet == null && date == null) {
            conversation
        } else {
            conversation.copy(snippet = snippet ?: conversation.snippet, date = date ?: conversation.date)
        }
}

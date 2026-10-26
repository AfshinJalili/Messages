package org.fossify.messages.ui.library

import androidx.compose.runtime.Immutable
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.inbox.InboxRow

/** A starred message as 18 · Starred lists it. */
@Immutable
data class StarredMessage(
    val messageId: Long,
    val isMms: Boolean,
    val threadId: Long,
    val sender: String,
    val body: String,
    val millis: Long,
)

@Immutable
data class StarredUiState(val loading: Boolean = true, val messages: List<StarredMessage> = emptyList())

@Immutable
data class ArchiveUiState(
    val loading: Boolean = true,
    val rows: List<InboxRow> = emptyList(),
    val selected: Set<Long> = emptySet(),
) {
    val selecting get() = selected.isNotEmpty()
    val selectedConversations get() = rows.filter { it.threadId in selected }.map { it.conversation }
}

/** A conversation with messages in the recycle bin: [messageCount] of them, the latest deleted at [deletedMillis]. */
@Immutable
data class RecycledConversation(val conversation: Conversation, val messageCount: Int, val deletedMillis: Long)

@Immutable
data class RecycleBinUiState(val loading: Boolean = true, val conversations: List<RecycledConversation> = emptyList())

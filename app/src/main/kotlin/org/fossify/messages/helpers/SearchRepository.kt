package org.fossify.messages.helpers

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.messageSearchResult
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.interfaces.MessagesDao
import org.fossify.messages.interfaces.ConversationsDao
import org.fossify.messages.models.CONVERSATION_SEARCH_RESULT_ID
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchMatch
import org.fossify.messages.models.SearchResult

/** Local cache search. Recycled and spam messages are excluded before results reach the UI. */
class SearchRepository(
    context: Context,
    private val messages: MessagesDao = context.messagesDB,
    private val conversations: ConversationsDao = context.conversationsDB,
) {
    private val context = context.applicationContext
    private val gson = Gson()

    suspend fun search(
        query: String,
        includeMessageResults: Boolean = true,
    ): List<SearchMatch> = withContext(Dispatchers.IO) {
        val text = query.trim()
        val pattern = literalSearchPattern(text)
        val browsePhotos = text.isEmpty() && includeMessageResults
        val people = if (browsePhotos) {
            emptyList()
        } else {
            conversations.searchPeople(pattern).map { conversation ->
                ensureActive()
                conversationMatch(conversation)
            }
        }
        if (!includeMessageResults) return@withContext people

        // Attachment filenames live inside JSON. Match decoded names, never JSON escape sequences.
        val attachmentPattern = filenamePattern(text)
        val candidates = messages.searchCandidates(pattern, attachmentPattern, mediaOnly = browsePhotos)
        val messageMatches = candidates.mapNotNull { message ->
            ensureActive()
            messageMatch(message, text)
        }
        people + messageMatches
    }

    private fun conversationMatch(conversation: Conversation) = SearchMatch(
        SearchResult(
            messageId = CONVERSATION_SEARCH_RESULT_ID,
            title = conversation.title,
            snippet = conversation.phoneNumber,
            date = (conversation.date * 1000L).formatDateOrTime(
                context,
                hideTimeOnOtherDays = true,
                showCurrentYear = true,
            ),
            threadId = conversation.threadId,
            photoUri = conversation.photoUri,
        )
    )

    private fun messageMatch(message: Message, text: String): SearchMatch? {
        val attachments = message.attachment?.attachments.orEmpty()
        val filename = attachments.firstOrNull { it.filename.normalizeSearchText().contains(text.normalizeSearchText(), ignoreCase = true) }?.filename
        val bodyMatches = message.body.normalizeSearchText().contains(text.normalizeSearchText(), ignoreCase = true)
        if (!bodyMatches && filename == null) return null

        val snippet = if (bodyMatches && message.body.isNotBlank()) message.body else filename.orEmpty()
        return SearchMatch(
            result = context.messageSearchResult(message).copy(snippet = snippet),
            media = attachments.isNotEmpty(),
            photo = attachments.any { it.mimetype.startsWith("image/") },
        )
    }

    private fun filenamePattern(text: String): String {
        val encoded = gson.toJson(text).removeSurrounding("\"")
        // Anchor the candidate to filename values so common letters do not match every JSON key.
        return "%\"filename\":\"${literalSearchPattern(encoded)}\"%"
    }

}

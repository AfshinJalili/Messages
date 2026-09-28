package org.fossify.messages.models

const val CONVERSATION_SEARCH_RESULT_ID = -1L

data class SearchMatch(val result: SearchResult, val media: Boolean = false, val photo: Boolean = false) {
    val person get() = result.messageId == CONVERSATION_SEARCH_RESULT_ID
    val key get() = if (person) "person:${result.threadId}" else "message:${result.isMms}:${result.messageId}"
}


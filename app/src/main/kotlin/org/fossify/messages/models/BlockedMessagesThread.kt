package org.fossify.messages.models

data class BlockedMessagesThread(
    val address: String,
    val title: String,
    val photoUri: String,
    val snippet: String,
    val date: Long,
    val count: Int,
    val messages: List<BlockedMessage>,
) {
    val key: Int
        get() = address.hashCode()
}

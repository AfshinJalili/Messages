package org.fossify.messages.models

/** One Spam screen row: the spam SMS of one Telephony thread. */
data class BlockedMessagesThread(
    val threadId: Long,
    val address: String,
    val title: String,
    val photoUri: String,
    val snippet: String,
    val date: Long,
    val count: Int,
    val unreadCount: Int,
    val reason: Int,
    val messageIds: List<Long>,
) {
    val key: Int
        get() = threadId.hashCode()
}

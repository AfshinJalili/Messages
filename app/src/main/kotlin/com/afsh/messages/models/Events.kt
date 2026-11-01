package com.afsh.messages.models

class Events {
    class RefreshMessages
    class RefreshConversations
    class ConversationReadStateChanged(val threadId: Long, val read: Boolean, val unreadCount: Int? = null)
}

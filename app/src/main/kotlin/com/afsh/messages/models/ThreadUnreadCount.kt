package com.afsh.messages.models

import androidx.room.ColumnInfo

/** One thread's stored unread count, for comparing with Telephony before writing. */
data class ThreadUnreadCount(
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
)

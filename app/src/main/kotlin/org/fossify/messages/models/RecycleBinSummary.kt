package org.fossify.messages.models

import androidx.room.ColumnInfo

/** A conversation's share of the recycle bin. [deletedTs] is the latest deletion, in milliseconds. */
data class RecycleBinSummary(
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "deleted_ts") val deletedTs: Long,
)

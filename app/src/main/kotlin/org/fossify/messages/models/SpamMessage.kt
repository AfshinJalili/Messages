package org.fossify.messages.models

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.fossify.messages.R
import org.fossify.messages.helpers.BLOCK_REASON_AI
import org.fossify.messages.helpers.BLOCK_REASON_KEYWORD
import org.fossify.messages.helpers.BLOCK_REASON_NUMBER

/** Marks a Telephony SMS as spam. The message itself stays in Telephony; only the app hides it. */
@Entity(tableName = "spam_messages", indices = [Index(value = ["thread_id"])])
data class SpamMessage(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "reason") val reason: Int,
    @ColumnInfo(name = "marked_ts") val markedTS: Long = System.currentTimeMillis(),
)

@StringRes
fun spamReasonLabel(reason: Int): Int = when (reason) {
    BLOCK_REASON_KEYWORD -> R.string.block_reason_keyword
    BLOCK_REASON_NUMBER -> R.string.block_reason_number
    BLOCK_REASON_AI -> R.string.block_reason_ai
    else -> R.string.block_reason_rule
}

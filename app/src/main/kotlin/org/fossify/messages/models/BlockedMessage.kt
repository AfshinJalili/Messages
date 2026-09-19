package org.fossify.messages.models

import android.provider.Telephony
import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.helpers.BLOCK_REASON_AI
import org.fossify.messages.helpers.BLOCK_REASON_KEYWORD
import org.fossify.messages.helpers.BLOCK_REASON_NUMBER

@Entity(tableName = "blocked_messages")
data class BlockedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "reason") val reason: Int,
) {
    @StringRes
    fun reasonLabel(): Int = when (reason) {
        BLOCK_REASON_KEYWORD -> R.string.block_reason_keyword
        BLOCK_REASON_NUMBER -> R.string.block_reason_number
        BLOCK_REASON_AI -> R.string.block_reason_ai
        else -> R.string.block_reason_rule
    }

    /** Display-only, for showing spam in the regular thread UI. Never written to any provider. */
    fun toMessage(sender: SimpleContact) = Message(
        id = id,
        body = body,
        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
        status = Telephony.Sms.STATUS_NONE,
        participants = arrayListOf(sender),
        date = (date / 1000).toInt(),
        read = false,
        threadId = 0L,
        isMMS = false,
        attachment = null,
        senderPhoneNumber = address,
        senderName = sender.name,
        senderPhotoUri = sender.photoUri,
        subscriptionId = -1
    )
}

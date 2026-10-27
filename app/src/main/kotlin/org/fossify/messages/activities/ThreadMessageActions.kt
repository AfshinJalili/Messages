package org.fossify.messages.activities

import android.content.Intent
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.models.Message
import org.fossify.messages.ui.components.confirm
import org.joda.time.DateTime

/** One message copies as-is; several copy as a dated transcript. */
internal fun SimpleActivity.copyMessages(messages: List<Message>) {
    val text = if (messages.size == 1) {
        messages.first().body
    } else {
        val format = "${config.dateFormat}, ${getTimeFormat()}"
        messages.filter { it.body.isNotEmpty() }.joinToString("\n\n") { message ->
            val sender = if (message.isReceivedMessage()) message.senderName else getString(R.string.me)
            "[${DateTime(message.millis()).toString(format)}] $sender: ${message.body}"
        }
    }
    if (text.isNotEmpty()) copyToClipboard(text)
}

/**
 * Deleting messages confirms (D03), as 82 does for conversations: into the recycle bin when it is
 * on, otherwise for good. Delete forever is in the recycle bin, not here.
 */
internal fun SimpleActivity.confirmDeleteMessages(
    messages: List<Message>,
    isRecycleBin: Boolean,
    delete: (messages: List<Message>, toRecycleBin: Boolean) -> Unit,
) {
    val count = messages.size
    val toRecycleBin = config.useRecycleBin && !isRecycleBin
    confirm(
        title = resources.getQuantityString(R.plurals.delete_messages_title, count, count),
        explanation = resources.getQuantityString(
            if (toRecycleBin) R.plurals.delete_messages_to_bin_text else R.plurals.delete_messages_forever_text,
            count,
        ),
        confirmLabel = getString(if (toRecycleBin) org.fossify.commons.R.string.delete else R.string.delete_forever),
        destructive = !toRecycleBin,
    ) {
        ensureBackgroundThread { delete(messages, toRecycleBin) }
    }
}

internal fun SimpleActivity.confirmRestoreMessages(messages: List<Message>, restore: (List<Message>) -> Unit) {
    val count = messages.size
    confirm(
        title = resources.getQuantityString(R.plurals.restore_messages_title, count, count),
        explanation = resources.getQuantityString(R.plurals.restore_messages_text, count),
        confirmLabel = getString(R.string.restore),
    ) {
        ensureBackgroundThread { restore(messages) }
    }
}

internal fun SimpleActivity.forwardMessage(message: Message) {
    Intent(this, NewConversationActivity::class.java).apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message.body)
        message.attachment?.attachments?.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it.getUri()) }
        startActivity(this)
    }
}

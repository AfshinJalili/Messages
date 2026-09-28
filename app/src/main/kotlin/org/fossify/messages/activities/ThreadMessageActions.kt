package org.fossify.messages.activities

import android.content.Intent
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.models.Message
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

internal fun SimpleActivity.confirmDeleteMessages(
    messages: List<Message>,
    isRecycleBin: Boolean,
    delete: (messages: List<Message>, toRecycleBin: Boolean) -> Unit,
) {
    // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
    val items = try {
        resources.getQuantityString(R.plurals.delete_messages, messages.size, messages.size)
    } catch (e: Exception) {
        showErrorToast(e)
        return
    }
    val offerRecycleBin = config.useRecycleBin && !isRecycleBin
    val question = String.format(
        getString(if (offerRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation),
        items,
    )
    DeleteConfirmationDialog(this, question, offerRecycleBin) { skipRecycleBin ->
        ensureBackgroundThread {
            if (messages.isNotEmpty()) delete(messages, !skipRecycleBin && offerRecycleBin)
        }
    }
}

internal fun SimpleActivity.confirmRestoreMessages(messages: List<Message>, restore: (List<Message>) -> Unit) {
    val items = try {
        resources.getQuantityString(R.plurals.delete_messages, messages.size, messages.size)
    } catch (e: Exception) {
        showErrorToast(e)
        return
    }
    ConfirmationDialog(this, String.format(getString(R.string.restore_confirmation), items)) {
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

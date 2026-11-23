package com.afsh.messages.activities

import android.content.Intent
import com.afsh.commons.extensions.copyToClipboard
import com.afsh.commons.extensions.launchActivityIntent
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.KEY_PHONE
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.dialNumber
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.ui.components.showRenameDialog

/** The conversation menu's own actions: archive, mute, mark unread, rename, and the number's dial, copy and add to contacts. */
internal class ThreadConversationActions(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val people get() = visit.people
    private val conversations by lazy { ConversationRepository.from(activity) }

    fun setArchived(archived: Boolean) {
        ensureBackgroundThread {
            conversations.setArchived(visit.threadId, archived)
            activity.runOnUiThread {
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                activity.finish()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        activity.config.setConversationMuted(visit.threadId, muted)
        visit.showFacts()
        activity.toast(
            activity.resources.getQuantityString(if (muted) R.plurals.conversation_muted else R.plurals.conversation_unmuted, 1, 1),
        )
    }

    fun markAsUnread() {
        ensureBackgroundThread {
            conversations.markThreadUnread(visit.threadId)
            activity.runOnUiThread {
                activity.finish()
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(threadId = visit.threadId, reconcile = false))
            }
        }
    }

    fun rename() {
        val current = visit.conversation ?: return
        activity.showRenameDialog(current) { title ->
            ensureBackgroundThread {
                visit.conversation = conversations.rename(current, newTitle = title)
                activity.runOnUiThread { visit.showFacts() }
            }
        }
    }

    fun dial() {
        activity.dialNumber(people.participants.first().phoneNumbers.first().normalizedNumber)
    }

    fun copyNumber() {
        val phoneNumber = visit.conversation?.phoneNumber
            ?.ifEmpty { people.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value }
            ?: return
        activity.copyToClipboard(phoneNumber)
    }

    fun addNumberToContact() {
        val phoneNumber = people.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            activity.launchActivityIntent(this)
        }
    }
}

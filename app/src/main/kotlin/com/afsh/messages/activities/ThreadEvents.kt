package com.afsh.messages.activities

import android.content.Intent
import android.telephony.SmsMessage
import com.afsh.commons.extensions.copyToClipboard
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.extensions.shareTextIntent
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.extensions.isVCardMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.extensions.launchConversationDetails
import com.afsh.messages.extensions.launchViewIntent
import com.afsh.messages.extensions.removeDiacriticsIfNeeded
import com.afsh.messages.helpers.EXTRA_VCARD_URI
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Message
import com.afsh.messages.ui.components.inform
import com.afsh.messages.ui.thread.ComposerEvent
import com.afsh.messages.ui.thread.MessageAction
import com.afsh.messages.ui.thread.PeopleEvent
import com.afsh.messages.ui.thread.ThreadEvent
import com.afsh.messages.ui.thread.ThreadMenuAction
import com.afsh.messages.ui.thread.ThreadStateHolder
import com.afsh.messages.ui.thread.cardSource
import com.afsh.messages.ui.thread.mediaItems
import com.afsh.messages.ui.thread.messageFacts
import com.afsh.messages.ui.thread.senderLine

/** What [ThreadVisit.holder] cannot do alone: each event goes to the part of the visit that owns it. */
internal class ThreadEvents(private val visit: ThreadVisit) : ThreadStateHolder.Host {
    private val activity get() = visit.activity
    private val holder get() = visit.holder
    private val people get() = visit.people

    override fun onEvent(event: ThreadEvent) {
        when (event) {
            ThreadEvent.Back -> activity.onBackPressedDispatcher.onBackPressed()
            ThreadEvent.OpenDetails -> if (visit.conversation != null && !visit.isRecycleBin) activity.launchConversationDetails(visit.threadId)
            ThreadEvent.Dial -> if (people.participants.isNotEmpty()) visit.actions.dial()
            is ThreadEvent.Menu -> if (people.participants.isNotEmpty()) onMenuAction(event.action)
            is ThreadEvent.SearchSubmit -> visit.loader.searchNext(event.query)
            is ThreadEvent.Act -> onMessageAction(event.action, event.messages)
            // Until the provider shows it leave the failed state, a second tap would transmit it again.
            is ThreadEvent.Retry -> if (holder.startRetry(event.message)) {
                if (event.message.isScheduled) {
                    // A scheduled message whose send failed never reached Telephony: it goes now, on the SIM shown.
                    visit.sender.sendScheduledNow(event.message, holder.sendSubscriptionId)
                } else {
                    visit.sender.resend(event.message, holder.sendSubscriptionId)
                }
            }
            is ThreadEvent.OpenAttachment -> openAttachment(event.message, event.attachment)
            is ThreadEvent.CopyText -> activity.copyToClipboard(event.text)
            ThreadEvent.JumpToLatest -> {
                activity.notificationManager.cancel(visit.threadId.hashCode())
                holder.scrollToBottom()
            }
            ThreadEvent.LoadOlder -> visit.loader.loadOlder()
            // The rest is the holder's own.
            else -> Unit
        }
    }

    override fun onComposerEvent(event: ComposerEvent) {
        val composer = visit.composer
        when (event) {
            ComposerEvent.Send -> composer.send()
            ComposerEvent.Dictate -> composer.launchDictation()
            is ComposerEvent.Attach -> composer.attach(event.option)
            ComposerEvent.Schedule -> composer.launchScheduleSendDialog(holder.scheduledMillis)
            is ComposerEvent.OpenAttachment -> event.attachment.let {
                if (it.mimetype.isVCardMimeType()) {
                    activity.startActivity(Intent(activity, VCardViewerActivity::class.java).putExtra(EXTRA_VCARD_URI, it.uri))
                } else {
                    activity.launchViewIntent(it.uri, it.mimetype, it.filename)
                }
            }
            ComposerEvent.ReplyInfo ->
                activity.inform(activity.getString(R.string.cannot_reply_title), activity.getString(R.string.invalid_short_code_desc))
            // The rest is the holder's own.
            else -> Unit
        }
    }

    override fun onPeopleEvent(event: PeopleEvent) {
        val managePeople = visit.managePeople
        when (event) {
            is PeopleEvent.Add -> managePeople.add(event.recipient)
            is PeopleEvent.Remove -> managePeople.remove(event.recipient)
            PeopleEvent.Done -> managePeople.apply()
            PeopleEvent.AllowContacts -> managePeople.allowContacts()
        }
    }

    override fun hideKeyboard() = activity.hideKeyboard()

    override fun viewportChanged() = visit.loader.checkReadState()

    override fun backEnabledChanged(enabled: Boolean) {
        activity.backCallback.isEnabled = enabled
    }

    override fun simChosen(subscriptionId: Int) = people.chooseSim(subscriptionId)

    override fun isMms(text: String, hasAttachments: Boolean) = visit.sender.isMms(text, hasAttachments)

    override fun smsLength(text: String): Pair<Int, Int>? {
        val config = activity.config
        if (!config.showCharacterCounter) return null
        val length = SmsMessage.calculateLength(text.removeDiacriticsIfNeeded(config.useSimpleCharacters), false)
        return length[0] to length[2]
    }

    private fun onMenuAction(action: ThreadMenuAction) {
        val actions = visit.actions
        val spam = visit.spam
        when (action) {
            ThreadMenuAction.SEARCH -> holder.openSearch()
            ThreadMenuAction.ALWAYS_SPAM -> spam.alwaysSpamSender()
            ThreadMenuAction.NEVER_SPAM -> spam.neverSpamSender()
            ThreadMenuAction.BLOCK_ON_PHONE -> spam.blockOnThisPhone()
            ThreadMenuAction.DELETE -> visit.deletion.askDeleteConversation()
            ThreadMenuAction.RESTORE -> visit.deletion.askRestoreConversation()
            ThreadMenuAction.ARCHIVE -> actions.setArchived(true)
            ThreadMenuAction.UNARCHIVE -> actions.setArchived(false)
            ThreadMenuAction.RENAME -> actions.rename()
            ThreadMenuAction.DETAILS -> activity.launchConversationDetails(visit.threadId)
            ThreadMenuAction.ADD_TO_CONTACT -> actions.addNumberToContact()
            ThreadMenuAction.COPY_NUMBER -> actions.copyNumber()
            ThreadMenuAction.ADD_PERSON -> visit.managePeople.toggle()
            ThreadMenuAction.MARK_UNREAD -> actions.markAsUnread()
            ThreadMenuAction.MUTE -> actions.setMuted(true)
            ThreadMenuAction.UNMUTE -> actions.setMuted(false)
        }
    }

    private fun onMessageAction(action: MessageAction, selected: List<Message>) {
        if (selected.isEmpty()) return
        val first = selected.first()
        when (action) {
            MessageAction.COPY -> activity.copyMessages(selected)
            MessageAction.FORWARD -> activity.forwardMessage(first)
            MessageAction.STAR, MessageAction.UNSTAR -> {
                selected.forEach { activity.config.setMessageStarred(it.id, it.isMMS, action == MessageAction.STAR) }
                holder.clearSelection()
                visit.publish()
            }
            MessageAction.DELETE -> activity.confirmDeleteMessages(selected, visit.isRecycleBin) { toDelete, toRecycleBin ->
                visit.deletion.deleteMessages(toDelete, toRecycleBin, fromRecycleBin = false)
            }
            MessageAction.SHARE -> activity.shareTextIntent(first.body)
            MessageAction.SELECT_TEXT -> if (first.body.isNotBlank()) {
                activity.startActivity(SelectTextActivity.intent(activity, activity.senderLine(first), first.body))
            }
            MessageAction.DETAILS -> activity.startActivity(MessageDetailsActivity.intent(activity, activity.messageFacts(first, people.simLabels)))
            MessageAction.EDIT_SCHEDULED -> visit.composer.editScheduled(first)
            MessageAction.SEND_NOW -> visit.composer.sendScheduledNow(first)
            MessageAction.SAVE_AS -> visit.composer.saveAttachments(selected.flatMap { it.attachment?.attachments.orEmpty() })
            MessageAction.NOT_SPAM -> {
                holder.clearSelection()
                visit.spam.askNotSpam(selected)
            }
            MessageAction.RESTORE -> activity.confirmRestoreMessages(selected) {
                visit.deletion.deleteMessages(it, toRecycleBin = false, fromRecycleBin = true)
            }
            // The holder selects all itself.
            MessageAction.SELECT_ALL -> Unit
        }
    }

    private fun openAttachment(message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        if (attachment.mimetype.isVCardMimeType()) {
            activity.startActivity(
                Intent(activity, VCardViewerActivity::class.java)
                    .putExtra(EXTRA_VCARD_URI, uri)
                    .putExtra(VCardViewerActivity.EXTRA_VCARD_SOURCE, activity.cardSource(message)),
            )
        } else if (attachment.mimetype.isImageMimeType() || attachment.mimetype.isVideoMimeType()) {
            // 45: every photo and video loaded in this conversation, oldest first, opened at [attachment].
            val (items, initial) = activity.mediaItems(visit.data.list, message, attachment, people.simLabels)
            if (initial < 0) {
                activity.launchViewIntent(uri, attachment.mimetype, attachment.filename)
            } else {
                activity.startActivity(MediaViewerActivity.intent(activity, items, initial))
            }
        } else {
            activity.launchViewIntent(uri, attachment.mimetype, attachment.filename)
        }
    }
}

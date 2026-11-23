package com.afsh.messages.helpers

import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.telephony.SmsManager
import androidx.documentfile.provider.DocumentFile
import com.afsh.commons.extensions.getFilenameFromPath
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.helpers.isSPlus
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.copy
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.filterNotInByKey
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.removeDiacriticsIfNeeded
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.messaging.cancelScheduleSendPendingIntent
import com.afsh.messages.messaging.isLongMmsMessage
import com.afsh.messages.messaging.scheduleMessage
import com.afsh.messages.messaging.sendMessageCompat
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment

/**
 * Owns sending from a conversation: sending now, scheduling with exact alarms, editing and sending a
 * scheduled message, retrying a failed one, and saving a message's attachments to files.
 *
 * Who the message goes to comes from [people]. What only the conversation screen knows (its thread, its
 * loaded messages, its composer) and what it shows goes through [host]; the transport, alarms and Room
 * go through [platform]. Calls start on the main thread; work that reads or writes storage runs on
 * [background], and [Host] calls made from there say so.
 */
class MessageSendCoordinator(
    private val platform: Platform,
    private val people: ParticipantResolver,
    private val host: Host,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> Long = { generateRandomId() },
    private val background: (() -> Unit) -> Unit = { ensureBackgroundThread(it) },
) {

    /** The SMS/MMS transport, alarms and storage. Blocking. */
    interface Platform {
        val useSimpleCharacters: Boolean
        val sendGroupAsMms: Boolean
        /** Whether [text] is long enough that the settings send it as MMS. */
        fun isLongMms(text: String): Boolean
        fun defaultSmsSubscriptionId(): Int
        fun transmit(text: String, addresses: List<String>, subscriptionId: Int, attachments: List<Attachment>, resend: Message?, forceMms: Boolean)
        /** The provider's newest [limit] messages of [threadId]. */
        fun latestMessages(threadId: Long, limit: Int): List<Message>
        /** Always true before Android 12. */
        fun canScheduleExactAlarms(): Boolean
        /** Sets the exact alarm that sends [message]; it replaces any alarm for the same id. */
        fun setAlarm(message: Message)
        /** Deletes a scheduled message and cancels its alarm. */
        fun dropScheduled(messageId: Long)
        fun conversation(threadId: Long): Conversation?
        fun saveConversation(conversation: Conversation)
        fun createTemporaryThread(message: Message, threadId: Long, cached: Conversation?)
        /** Stores [message] in Room and updates the inbox: preview, outgoing bump, unarchive and the change event. */
        fun persist(message: Message)
        /** Copies [attachments] into the file or folder at [destination]. False when the folder cannot be opened. */
        fun writeAttachments(attachments: List<Attachment>, destination: Uri): Boolean
    }

    /** The conversation screen, as sending needs it. */
    interface Host {
        val threadId: Long
        val conversation: Conversation?
        /** The loaded messages. Read on the background thread. */
        fun loaded(): List<Message>
        /** The conversation moved to [threadId], as a first scheduled message makes a temporary thread. Background thread. */
        fun moveToThread(threadId: Long)
        /** [message] was stored: show it at the bottom of the timeline. Background thread. */
        fun showStored(message: Message)
        /** Reload the thread from the provider. Background thread. */
        fun reload()
        /** A message went: clear the composer. Main thread. */
        fun sent()
        /** A message was scheduled: clear the composer and its send time. Background thread. */
        fun scheduled()
        /** A Retry could not send: [message] gets its Retry back. */
        fun retryFailed(message: Message)
        /** The time picked is not in the future; [retryMillis] reopens the time picker at it, when not null. */
        fun timeInPast(retryMillis: Long?)
        /** Android 12+ without the exact alarm permission: ask for it. */
        fun askForExactAlarms()
        /** Background thread. */
        fun attachmentsSaved()
        fun failed(error: Exception)
        /** [message] is null when the error has none. */
        fun failed(message: String?)
    }

    sealed interface SaveRequest {
        /** One attachment: the person picks a file of [mimetype], suggested as [title]. */
        data class File(val mimetype: String, val title: String) : SaveRequest
        /** Several attachments: the person picks a folder. */
        data object Folder : SaveRequest
    }

    /** False from a send until the thread reloads; a stored message asks for that reload. */
    @Volatile
    private var refreshedSinceSent = false

    private var pendingAttachmentsToSave: List<Attachment>? = null

    /** A send has not been followed by a reload yet. */
    val needsRefresh: Boolean get() = !refreshedSinceSent

    /** The thread reloaded from the provider. */
    fun reloaded() {
        refreshedSinceSent = true
    }

    /** Whether [text] goes as MMS: with attachments, to a group with group MMS on, or when long. */
    fun isMms(text: String, hasAttachments: Boolean): Boolean {
        val isGroupMms = people.participants.size > 1 && platform.sendGroupAsMms
        return hasAttachments || isGroupMms || platform.isLongMms(text)
    }

    /** Sends the composer's [text] and [attachments] now, or at [scheduledMillis]. No [subscriptionId] means the system default. */
    fun send(text: String, attachments: List<AttachmentSelection>, subscriptionId: Int?, scheduledMillis: Long?) {
        val body = text.removeDiacriticsIfNeeded(platform.useSimpleCharacters)
        val subscription = subscriptionId ?: platform.defaultSmsSubscriptionId()
        if (scheduledMillis != null) {
            sendScheduled(body, attachments, subscription, scheduledMillis)
        } else {
            sendNow(body, attachments.toAttachments(), subscription)
        }
    }

    private fun sendNow(text: String, attachments: List<Attachment>, subscriptionId: Int) {
        try {
            refreshedSinceSent = false
            platform.transmit(text, people.addresses, subscriptionId, attachments, resend = null, forceMms = false)
            background {
                val threadId = host.threadId
                val messages = platform.latestMessages(threadId, limit = maxOf(1, attachments.size))
                    .filterNotInByKey(host.loaded()) { it.getStableId() }
                messages.forEach(::store)
            }
            host.sent()
        } catch (e: Exception) {
            host.failed(e)
        } catch (e: Error) {
            host.failed(e.localizedMessage)
        }
    }

    private fun sendScheduled(text: String, selections: List<AttachmentSelection>, subscriptionId: Int, millis: Long) {
        if (millis < clock() + 1000L) {
            host.timeInPast(retryMillis = millis)
            return
        }

        refreshedSinceSent = false
        try {
            background {
                val messageId = newId()
                val isFirst = host.loaded().isEmpty()
                val message = scheduledMessage(text, selections, subscriptionId, messageId, millis, isFirst)
                if (isFirst) {
                    // A temporary thread until a real message is sent.
                    host.moveToThread(message.threadId)
                    platform.createTemporaryThread(message, message.threadId, host.conversation)
                }
                platform.conversation(host.threadId)?.let { conversation ->
                    val nowSeconds = (clock() / 1000).toInt()
                    platform.saveConversation(conversation.copy(date = nowSeconds, snippet = message.body))
                }
                platform.setAlarm(message)
                store(message)
                host.scheduled()
            }
        } catch (e: Exception) {
            host.failed(e.localizedMessage)
        }
    }

    /** Saves a scheduled [message] with new [text] and time [millis], which must be in the future. */
    fun updateScheduled(message: Message, text: String, millis: Long) {
        if (millis <= clock()) {
            host.timeInPast(retryMillis = null)
            return
        }
        background {
            val updated = message.copy(
                body = text,
                date = (millis / 1000).toInt(),
                attachment = message.attachment?.copy(text = text),
            )
            // The alarm is keyed by the message id, so this replaces the old one.
            platform.setAlarm(updated)
            store(updated)
            platform.conversation(host.threadId)?.let { current ->
                if (current.snippet == message.body) platform.saveConversation(current.copy(snippet = text))
            }
        }
    }

    /**
     * Send now: the scheduled [message] goes as a normal one on its own SIM, with [attachments] (its
     * attachments, back in the composer), and its schedule is dropped.
     */
    fun sendScheduledNow(message: Message, attachments: List<AttachmentSelection>) {
        sendNow(message.body, attachments.toAttachments(), message.subscriptionId)
        background {
            platform.dropScheduled(message.id)
            host.reload()
        }
    }

    /** Retry: resends the failed [message] at once, leaving the composer as it is. */
    fun resend(message: Message, subscriptionId: Int?) {
        val subscription = subscriptionId ?: platform.defaultSmsSubscriptionId()
        try {
            refreshedSinceSent = false
            platform.transmit(
                message.body, people.addresses, subscription, message.attachment?.attachments.orEmpty(), message,
                // Today's settings must not turn a failed MMS into an SMS, which could not replace its row.
                forceMms = message.isMMS,
            )
            background { host.reload() }
        } catch (e: Exception) {
            host.retryFailed(message)
            host.failed(e)
        } catch (e: Error) {
            host.retryFailed(message)
            host.failed(e.localizedMessage)
        }
    }

    /** Runs [action] when exact alarms may be set; otherwise asks for the permission. */
    fun whenExactAlarmsAllowed(action: () -> Unit) {
        if (platform.canScheduleExactAlarms()) action() else host.askForExactAlarms()
    }

    /** Keeps [attachments] for [saveTo] and says where the person picks to save them. Null for none. */
    fun saveAttachments(attachments: List<Attachment>): SaveRequest? {
        if (attachments.isEmpty()) return null
        pendingAttachmentsToSave = attachments
        return if (attachments.size == 1) {
            val attachment = attachments.first()
            SaveRequest.File(attachment.mimetype, attachment.uriString.split("/").last())
        } else {
            SaveRequest.Folder
        }
    }

    /** Writes the attachments kept by [saveAttachments] to [destination], a file or a folder. */
    fun saveTo(destination: Uri) {
        background {
            try {
                if (platform.writeAttachments(pendingAttachmentsToSave.orEmpty(), destination)) host.attachmentsSaved()
            } catch (e: Exception) {
                host.failed(e)
            } finally {
                pendingAttachmentsToSave = null
            }
        }
    }

    private fun store(message: Message) {
        host.showStored(message)
        platform.persist(message)
    }

    private fun scheduledMessage(
        text: String,
        selections: List<AttachmentSelection>,
        subscriptionId: Int,
        messageId: Long,
        millis: Long,
        isFirst: Boolean,
    ) = Message(
        id = messageId,
        body = text,
        type = MESSAGE_TYPE_QUEUED,
        status = STATUS_NONE,
        participants = people.participants,
        date = (millis / 1000).toInt(),
        read = false,
        threadId = if (isFirst) messageId else host.threadId,
        isMMS = isMms(text, selections.isNotEmpty()),
        attachment = MessageAttachment(messageId, text, selections.toAttachments(messageId)),
        senderPhoneNumber = "",
        senderName = "",
        senderPhotoUri = "",
        subscriptionId = subscriptionId,
        isScheduled = true,
    )

    companion object {
        fun from(context: Context, people: ParticipantResolver, host: Host) =
            MessageSendCoordinator(AndroidPlatform(context), people, host)
    }

    /** [context] is the activity: the transport shows its own error toasts. */
    private class AndroidPlatform(private val context: Context) : Platform {
        private val app = context.applicationContext
        private val telephony = TelephonyReader(app)
        private val conversations = ConversationRepository.from(app)

        override val useSimpleCharacters get() = app.config.useSimpleCharacters
        override val sendGroupAsMms get() = app.config.sendGroupMessageMMS
        override fun isLongMms(text: String) = app.isLongMmsMessage(text)
        override fun defaultSmsSubscriptionId() = SmsManager.getDefaultSmsSubscriptionId()

        override fun transmit(text: String, addresses: List<String>, subscriptionId: Int, attachments: List<Attachment>, resend: Message?, forceMms: Boolean) =
            context.sendMessageCompat(text, addresses, subscriptionId, attachments, resend, forceMms)

        override fun latestMessages(threadId: Long, limit: Int): List<Message> = telephony.getMessages(threadId, limit = limit)

        override fun canScheduleExactAlarms() =
            !isSPlus() || (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

        override fun setAlarm(message: Message) = app.scheduleMessage(message)

        override fun dropScheduled(messageId: Long) {
            app.deleteScheduledMessage(messageId)
            app.cancelScheduleSendPendingIntent(messageId)
        }

        override fun conversation(threadId: Long) = app.conversationsDB.getConversationWithThreadId(threadId)

        override fun saveConversation(conversation: Conversation) {
            app.conversationsDB.insertOrUpdate(conversation)
        }

        override fun createTemporaryThread(message: Message, threadId: Long, cached: Conversation?) =
            conversations.createScheduledConversation(message, threadId, cached)

        override fun persist(message: Message) {
            app.messagesDB.insertOrUpdate(message)
            ConversationPreview.refresh(app, message.threadId)
            val isOutgoing = !message.isReceivedMessage() && !message.isScheduled
            if (isOutgoing) {
                InboxRepository.bumpAfterOutgoing(app, message.threadId, message.body)
            }
            if (app.shouldUnarchive()) {
                conversations.setArchived(message.threadId, false)
            }
            if (isOutgoing || app.shouldUnarchive()) {
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(threadId = message.threadId, reconcile = true))
            }
        }

        override fun writeAttachments(attachments: List<Attachment>, destination: Uri): Boolean {
            if (DocumentsContract.isTreeUri(destination)) {
                val outputDir = DocumentFile.fromTreeUri(app, destination) ?: return false
                attachments.forEach { attachment ->
                    val documentFile = outputDir.createFile(
                        attachment.mimetype,
                        attachment.filename.takeIf { it.isNotBlank() } ?: attachment.uriString.getFilenameFromPath(),
                    ) ?: return@forEach
                    app.contentResolver.copy(src = attachment.getUri(), dst = documentFile.uri)
                }
            } else {
                app.contentResolver.copy(attachments.first().getUri(), destination)
            }
            return true
        }
    }
}

private fun List<AttachmentSelection>.toAttachments(messageId: Long = -1L) =
    mapTo(ArrayList()) { Attachment(null, messageId, it.uri.toString(), it.mimetype, 0, 0, it.filename) }

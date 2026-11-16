package com.afsh.messages

import android.content.Context
import android.provider.Telephony
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.afsh.messages.ui.inbox.InboxRow
import java.util.Locale

/**
 * A message for in-memory screens and disposable Room rows. By default a read, received SMS on
 * thread 1; name only what the check is about.
 */
fun fixtureMessage(
    id: Long,
    body: String = "m$id",
    threadId: Long = 1L,
    date: Int = 1_750_000_000,
    read: Boolean = true,
    type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
    status: Int = -1,
    isMms: Boolean = false,
    attachment: MessageAttachment? = null,
    address: String = "5550100777",
    senderName: String = "",
    subscriptionId: Int = -1,
    scheduled: Boolean = false,
) = Message(
    id = id, body = body, type = type, status = status, participants = ArrayList(), date = date, read = read,
    threadId = threadId, isMMS = isMms, attachment = attachment, senderPhoneNumber = address, senderName = senderName,
    senderPhotoUri = "", subscriptionId = subscriptionId, isScheduled = scheduled,
)

/** A conversation for in-memory screens and disposable Room rows; by default a read one-to-one thread. */
fun fixtureConversation(
    threadId: Long,
    title: String,
    snippet: String = "hi",
    date: Int = 1,
    read: Boolean = true,
    phone: String = "5550100",
    isGroup: Boolean = false,
    scheduled: Boolean = false,
    archived: Boolean = false,
    unreadCount: Int = 0,
    messageCount: Int = 0,
) = Conversation(
    threadId = threadId, snippet = snippet, date = date, read = read, title = title, photoUri = "",
    isGroupConversation = isGroup, phoneNumber = phone, isScheduled = scheduled, isArchived = archived,
    unreadCount = unreadCount, messageCount = messageCount,
)

/** An inbox row around [conversation]. */
fun inboxRow(conversation: Conversation, draft: String? = null, pinned: Boolean = false, muted: Boolean = false) =
    InboxRow(conversation, draft = draft, pinned = pinned, muted = muted)

/** The two rows the inbox notices and list-pane checks are shown and captured with. */
val sampleInboxRows = listOf(
    inboxRow(fixtureConversation(1, "Mina Farahani", snippet = "The train leaves at six.", phone = "5550142")),
    inboxRow(fixtureConversation(2, "Dad", snippet = "Photo • That’s the one!", phone = "5550176")),
)

/** [count] written the way the screen writes numbers: in the app locale's digits. */
fun Context.localizedNumber(count: Int): String = String.format(resources.configuration.locales.get(0) ?: Locale.getDefault(), "%d", count)

/** The plural [id] for [count], with the count filled in. */
fun Context.quantity(id: Int, count: Int): String = resources.getQuantityString(id, count, count)

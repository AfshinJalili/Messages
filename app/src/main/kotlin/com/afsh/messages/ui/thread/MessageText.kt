package com.afsh.messages.ui.thread

import android.content.Context
import android.provider.Telephony
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Message
import com.afsh.messages.ui.components.dayLabel
import com.afsh.messages.ui.ltr
import com.afsh.messages.ui.media.MediaItem
import com.afsh.messages.ui.message.DeliveryMark
import com.afsh.messages.ui.message.MessageFacts
import org.fossify.commons.extensions.formatTime
import org.fossify.commons.extensions.getTimeFormatWithSeconds
import org.joda.time.DateTime

/** "Mina Farahani · 12:41" or "You · 12:41": who wrote [message], and when. */
fun Context.senderLine(message: Message): String {
    val who = if (message.isReceivedMessage()) message.senderName.ifEmpty { message.senderPhoneNumber } else getString(R.string.you)
    return getString(R.string.detail_pair, who, message.millis().formatTime(this))
}

/** "Shared by Mina · Today" under a contact card. */
fun Context.cardSource(message: Message): String {
    val by = if (message.isReceivedMessage()) message.senderName.ifEmpty { message.senderPhoneNumber } else getString(R.string.you)
    return getString(R.string.detail_pair, getString(R.string.shared_by, by), dayLabel(this, message.millis()))
}

/** 13's facts: type, from, to, when, and how far it got. [simLabels] are by subscription id. */
fun Context.messageFacts(message: Message, simLabels: Map<Int, String>): MessageFacts {
    val received = message.isReceivedMessage()
    val sim = simLabels[message.subscriptionId]?.let { getString(R.string.message_sim_label, it) }
    fun person(name: String, number: String) = if (name.isEmpty() || name == number) ltr(number) else getString(R.string.detail_pair, name, ltr(number))
    val others = message.participants.joinToString(", ") { person(it.name, it.phoneNumbers.firstOrNull()?.value.orEmpty()) }
    val you = listOfNotNull(getString(R.string.you), sim).joinToString(" · ")
    val time = DateTime(message.millis()).toString("${config.dateFormat} ${getTimeFormatWithSeconds()}")
    val sent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT
    val delivered = sent && message.status == Telephony.Sms.STATUS_COMPLETE
    val rows = buildList {
        add(getString(R.string.message_type) to getString(if (message.isMMS) R.string.mms else R.string.sms))
        add(getString(R.string.message_from) to if (received) person(message.senderName, message.senderPhoneNumber) else you)
        add(getString(R.string.message_to) to if (received) you else others)
        add(getString(if (received) R.string.message_received_at else R.string.message_sent_at) to time)
        when {
            message.type == Telephony.Sms.MESSAGE_TYPE_FAILED -> add(getString(R.string.message_status) to getString(R.string.message_not_sent_short))
            delivered -> add(getString(R.string.message_status) to getString(R.string.message_delivered))
            sent -> add(getString(R.string.message_status) to getString(R.string.message_sent))
        }
    }
    val mark = when {
        delivered -> DeliveryMark.DELIVERED
        sent -> DeliveryMark.SENT
        else -> DeliveryMark.NONE
    }
    return MessageFacts(message.body, message.millis().formatTime(this), mark, rows)
}

/**
 * 45: every photo and video in [messages], oldest first, and the index of [attachment] of [message] among them;
 * the index is -1 when it is not there.
 */
fun Context.mediaItems(messages: List<Message>, message: Message, attachment: Attachment, simLabels: Map<Int, String>): Pair<List<MediaItem>, Int> {
    val media = messages.filterNot { it.isScheduled }.flatMap { owner ->
        owner.attachment?.attachments.orEmpty()
            .filter { it.mimetype.isImageMimeType() || it.mimetype.isVideoMimeType() }
            .map { owner to it }
    }
    val items = media.map { (owner, item) ->
        val millis = owner.millis()
        MediaItem(
            uri = item.uriString,
            mimetype = item.mimetype,
            filename = item.filename,
            sender = if (owner.isReceivedMessage()) owner.senderName.ifEmpty { ltr(owner.senderPhoneNumber) } else getString(R.string.you),
            time = getString(R.string.day_and_time, dayLabel(this, millis), millis.formatTime(this)),
            caption = owner.body,
            facts = messageFacts(owner, simLabels),
        )
    }
    val initial = media.indexOfFirst { (owner, item) -> owner.id == message.id && item.uriString == attachment.uriString }
    return items to initial
}

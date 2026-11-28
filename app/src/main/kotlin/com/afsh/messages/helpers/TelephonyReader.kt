package com.afsh.messages.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import android.telephony.SubscriptionManager
import androidx.core.net.toUri
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.extensions.getIntValue
import com.afsh.commons.extensions.getIntValueOr
import com.afsh.commons.extensions.getLongValue
import com.afsh.commons.extensions.getStringValue
import com.afsh.commons.extensions.isNumberBlocked
import com.afsh.commons.extensions.queryCursor
import com.afsh.commons.helpers.isQPlus
import com.afsh.commons.models.PhoneNumber
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.extensions.getNameAndPhotoFromPhoneNumber
import com.afsh.messages.extensions.getThreadParticipants
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.queryCursorUnsafe
import com.afsh.messages.helpers.AttachmentUtils.parseAttachmentNames
import com.afsh.messages.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.google.android.mms.pdu_alt.PduHeaders
import org.xmlpull.v1.XmlPullParserException

/**
 * Reads messages and thread facts straight from the Telephony provider (SMS, MMS, MMS parts and
 * addresses, the threads table). It never writes. Callers on the main thread must hop to a
 * background thread first: every read is a blocking content-provider query.
 *
 * Contact names and photos are resolved through the participant cache, and SMS from blocked
 * numbers are left out.
 */
class TelephonyReader(context: Context) {
    private val context: Context = context.applicationContext

    /**
     * The newest [limit] messages of [threadId] (SMS, MMS and, when [includeScheduledMessages],
     * scheduled ones), oldest first. [dateFrom] (seconds) pages backwards: only messages older than
     * it. With [includeAllUnread] the window grows to reach the oldest unread incoming message,
     * or [oldestLoadedDate] if that is older. SMS in [skipUnreadSmsIds] (spam, kept unread on purpose)
     * don't count as unread for that window.
     */
    fun getMessages(
        threadId: Long,
        dateFrom: Int = -1,
        includeScheduledMessages: Boolean = true,
        limit: Int = MESSAGES_LIMIT,
        includeAllUnread: Boolean = false,
        oldestLoadedDate: Int? = null,
        skipUnreadSmsIds: Collection<Long> = emptyList(),
    ): ArrayList<Message> = context.getMessages(
        threadId, dateFrom, includeScheduledMessages, limit, includeAllUnread, oldestLoadedDate, skipUnreadSmsIds,
    )

    /** MMS of [threadId] (all threads when null), in [sortOrder]; [dateFrom] keeps older ones only. */
    fun getMms(threadId: Long? = null, sortOrder: String? = null, dateFrom: Int = -1): ArrayList<Message> =
        context.getMMS(threadId, sortOrder, dateFrom)

    /** The newest MMS on the phone, or null. */
    fun getLatestMms(): Message? = context.getLatestMMS()

    /** The From address of MMS [messageId], or "" when it can't be read. */
    fun getMmsSender(messageId: Long): String = context.getMMSSender(messageId)

    /** The text and media parts of MMS [messageId]. */
    fun getMmsAttachment(messageId: Long): MessageAttachment = context.getMmsAttachment(messageId)

    /** The body of the newest SMS or MMS in [threadId], or "". */
    fun getThreadSnippet(threadId: Long): String = context.getThreadSnippet(threadId)

    /** Thread count and latest thread date (seconds); `-1 to -1` when the provider can't be read. */
    fun threadDigest(): Pair<Int, Int> = context.telephonyThreadDigest()

    /** Every Telephony thread id, oldest thread first. */
    fun threadIds(): List<Long> {
        val ids = mutableListOf<Long>()
        context.queryCursor(Threads.CONTENT_URI, arrayOf(Threads._ID), null, null, "${Threads.DATE} ASC", true) { cursor ->
            ids.add(cursor.getLongValue(Threads._ID))
        }
        return ids
    }

    /**
     * SMS [messageId] as a [Message], or null when it is missing or can't be read. Unlike
     * [getMessages] it does not drop blocked senders: callers ask for one known row.
     */
    fun getSms(messageId: Long): Message? {
        var message: Message? = null
        context.queryCursor(Sms.CONTENT_URI, SMS_PROJECTION, "${Sms._ID} = ?", arrayOf(messageId.toString())) { cursor ->
            val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
            message = context.smsMessage(cursor, senderNumber)
        }
        return message
    }

    /** The address of SMS [messageId], or "" when it can't be read. */
    fun getSmsAddress(messageId: Long): String {
        try {
            val selectionArgs = arrayOf(messageId.toString())
            context.contentResolver.query(Sms.CONTENT_URI, arrayOf(Sms.ADDRESS), "${Sms._ID} = ?", selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getStringValue(Sms.ADDRESS)
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }
}

// Load through the oldest unread message, including read messages between unread bubbles.
private fun Context.getUnreadWindowSize(threadId: Long, oldestLoadedDate: Int?, skipSmsIds: Collection<Long>): Int {
    fun aggregate(uri: Uri, expression: String, selection: String, args: Array<String>): Long =
        contentResolver.query(uri, arrayOf(expression), selection, args, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
        } ?: 0L
    val sources = listOf(Sms.CONTENT_URI to 1L, Mms.CONTENT_URI to 1000L)
    val oldest = sources.mapNotNull { (uri, scale) ->
        val isSms = uri == Sms.CONTENT_URI
        val box = if (isSms) Sms.TYPE else Mms.MESSAGE_BOX
        val skip = if (isSms && skipSmsIds.isNotEmpty()) " AND ${Sms._ID} NOT IN (${skipSmsIds.joinToString()})" else ""
        aggregate(uri, "MIN(date)", "thread_id=? AND read=0 AND $box=1$skip", arrayOf(threadId.toString()))
            .takeIf { it > 0 }?.times(scale)
    }.plus(listOfNotNull(oldestLoadedDate?.toLong()?.times(1000))).minOrNull() ?: return 0
    return sources.sumOf { (uri, scale) ->
        aggregate(uri, "COUNT(*)", "thread_id=? AND date>=?", arrayOf(threadId.toString(), (oldest / scale).toString()))
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun Context.getMessages(
    threadId: Long,
    dateFrom: Int = -1,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT,
    includeAllUnread: Boolean = false,
    oldestLoadedDate: Int? = null,
    skipUnreadSmsIds: Collection<Long> = emptyList(),
): ArrayList<Message> {
    val displayLimit =
        if (includeAllUnread) maxOf(limit, getUnreadWindowSize(threadId, oldestLoadedDate, skipUnreadSmsIds)) else limit
    val uri = Sms.CONTENT_URI

    val rangeQuery = if (dateFrom == -1) "" else "AND ${Sms.DATE} < ${dateFrom.toLong() * 1000}"
    val selection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT $displayLimit"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = getBlockedNumbers()
    var messages = ArrayList<Message>()
    queryCursor(uri, SMS_PROJECTION, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val isNumberBlocked = blockStatus.getOrPut(senderNumber) { isNumberBlocked(senderNumber, blockedNumbers) }
        if (isNumberBlocked) {
            return@queryCursor
        }

        messages.add(smsMessage(cursor, senderNumber))
    }

    messages.addAll(getMMS(threadId, sortOrder, dateFrom))

    if (includeScheduledMessages) {
        try {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
            messages.addAll(scheduledMessages)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    messages = messages
        .filter { it.participants.isNotEmpty() }
        .filterNot { it.isDueBefore(System.currentTimeMillis()) }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id })
        .takeLast(displayLimit)
        .toMutableList() as ArrayList<Message>

    return messages
}

private val SMS_PROJECTION = arrayOf(
    Sms._ID,
    Sms.BODY,
    Sms.TYPE,
    Sms.ADDRESS,
    Sms.DATE,
    Sms.READ,
    Sms.THREAD_ID,
    Sms.SUBSCRIPTION_ID,
    Sms.STATUS
)

/** One [SMS_PROJECTION] row as a [Message], with sender name and photos from the participant cache. */
private fun Context.smsMessage(cursor: Cursor, senderNumber: String): Message {
    val id = cursor.getLongValue(Sms._ID)
    val body = cursor.getStringValue(Sms.BODY)
    val type = cursor.getIntValue(Sms.TYPE)
    val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
    val senderName = namePhoto.name
    val photoUri = namePhoto.photoUri ?: ""
    val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
    val read = cursor.getIntValue(Sms.READ) == 1
    val thread = cursor.getLongValue(Sms.THREAD_ID)
    val subscriptionId = cursor.getIntValueOr(
        key = Sms.SUBSCRIPTION_ID,
        defaultValue = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    )

    val status = cursor.getIntValue(Sms.STATUS)
    val participants = senderNumber.split(ADDRESS_SEPARATOR).map { number ->
        val phoneNumber = PhoneNumber(number, 0, "", number)
        val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
        SimpleContact(
            rawId = 0,
            contactId = 0,
            name = participantPhoto.name,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(phoneNumber),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )
    }
    return Message(
        id = id,
        body = body,
        type = type,
        status = status,
        participants = ArrayList(participants),
        date = date,
        read = read,
        threadId = thread,
        isMMS = false,
        attachment = null,
        senderPhoneNumber = senderNumber,
        senderName = senderName,
        senderPhotoUri = photoUri,
        subscriptionId = subscriptionId
    )
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
private fun Context.getMMS(
    threadId: Long? = null,
    sortOrder: String? = null,
    dateFrom: Int = -1,
): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID,
        Mms.STATUS
    )

    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (threadId == null && dateFrom != -1) {
        // Should not multiply 1000 here, because date in mms's database is different from sms's.
        selection = "${Sms.DATE} < ${dateFrom.toLong()}"
    } else if (threadId != null && dateFrom == -1) {
        selection = "${Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId.toString())
    } else if (threadId != null) {
        selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} < ${dateFrom.toLong()}"
        selectionArgs = arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getLongValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getLongValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Mms.STATUS)
        val participants = getThreadParticipants(threadId, contactsMap)

        val isMMS = true
        val attachment = getMmsAttachment(mmsId)
        val body = attachment.text
        var senderNumber = ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            senderNumber = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            senderName = namePhoto.name
            senderPhotoUri = namePhoto.photoUri ?: ""
        }

        val message =
            Message(
                id = mmsId,
                body = body,
                type = type,
                status = status,
                participants = participants,
                date = date,
                read = read,
                threadId = threadId,
                isMMS = isMMS,
                attachment = attachment,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = senderPhotoUri,
                subscriptionId = subscriptionId
            )
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

private fun Context.getMMSSender(msgId: Long): String {
    val uri = "${Mms.CONTENT_URI}/$msgId/addr".toUri()
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms.Addr.TYPE} = ?"
    val selectionArgs = arrayOf(PduHeaders.FROM.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }
    return ""
}


/**
 * Cheap Telephony fingerprint for launch: thread count and latest date only.
 * Skips contact lookups, blocked-number filters, unread scans and draft merges that
 * [TelephonyConversations.conversations] pays for on a full reconcile.
 */
private fun Context.telephonyThreadDigest(): Pair<Int, Int> {
    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = arrayOf(Threads.DATE)
    val selection = "${Threads.MESSAGE_COUNT} > 0"
    var count = 0
    var latest = 0
    try {
        contentResolver.queryCursorUnsafe(uri, projection, selection, emptyArray(), null) { cursor ->
            count++
            var date = cursor.getLongValue(Threads.DATE)
            if (date.toString().length > 10) date /= 1000
            if (date > latest) latest = date.toInt()
        }
    } catch (_: Exception) {
        return -1 to -1
    }
    return count to latest
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
private fun Context.getMmsAttachment(id: Long): MessageAttachment {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        "content://mms/part".toUri()
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT,
        Mms.Part.FILENAME,
        Mms.Part.NAME,
        Mms.Part.CONTENT_LOCATION,
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentNames: List<String>? = null
    var attachmentCount = 0
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getLongValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        val filename = cursor.getStringValue(Mms.Part.FILENAME).orEmpty()
            .ifBlank { cursor.getStringValue(Mms.Part.NAME).orEmpty() }
            .ifBlank { cursor.getStringValue(Mms.Part.CONTENT_LOCATION).orEmpty() }
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor
                .getStringValue(Mms.Part.TEXT)
                ?.take(MAX_MESSAGE_LENGTH)
                .orEmpty()
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val fileUri = Uri.withAppendedPath(uri, partId.toString())
            messageAttachment.attachments.add(
                Attachment(
                    id = partId,
                    messageId = id,
                    uriString = fileUri.toString(),
                    mimetype = mimetype,
                    width = 0,
                    height = 0,
                    filename = filename
                )
            )
        } else if (mimetype != "application/smil") {
            val attachmentName = filename.ifBlank { attachmentNames?.getOrNull(attachmentCount).orEmpty() }
            val attachment = Attachment(
                id = partId,
                messageId = id,
                uriString = Uri.withAppendedPath(uri, partId.toString()).toString(),
                mimetype = mimetype,
                width = 0,
                height = 0,
                filename = attachmentName
            )
            messageAttachment.attachments.add(attachment)
            attachmentCount++
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            attachmentNames = try {
                parseAttachmentNames(text)
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                null
            }
        }
    }

    return messageAttachment
}

private fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

private fun Context.getThreadSnippet(threadId: Long): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    // Message.date is in seconds; the SMS table keeps milliseconds.
    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.millis()?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, "${Sms.DATE} DESC LIMIT 1")
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (_: Exception) {
    }
    return snippet
}

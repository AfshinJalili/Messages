package com.afsh.messages.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import android.provider.Telephony.ThreadsColumns
import android.telephony.SubscriptionManager
import android.text.TextUtils
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.afsh.commons.extensions.areDigitsOnly
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.extensions.getIntValue
import com.afsh.commons.extensions.getLongValue
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.extensions.getStringValue
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.extensions.isNumberBlocked
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.extensions.queryCursor
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.trimToComparableNumber
import com.afsh.commons.helpers.MyContactsContentProvider
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.models.PhoneNumber
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.R
import com.afsh.messages.databases.MessagesDatabase
import com.afsh.messages.helpers.Config
import com.afsh.messages.helpers.ConversationPreview
import com.afsh.messages.helpers.MessagingCache
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.NotificationHelper
import com.afsh.messages.helpers.ShortcutHelper
import com.afsh.messages.helpers.TelephonyReader
import com.afsh.messages.helpers.generateRandomId
import com.afsh.messages.interfaces.AttachmentsDao
import com.afsh.messages.interfaces.ConversationsDao
import com.afsh.messages.interfaces.DraftsDao
import com.afsh.messages.interfaces.MessageAttachmentsDao
import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.messaging.MessagingUtils
import com.afsh.messages.messaging.SmsSender
import com.afsh.messages.messaging.scheduleMessage
import com.afsh.messages.models.BlockedMessagesThread
import com.afsh.messages.models.SpamReviewMessage
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.SearchResult
import com.afsh.messages.ui.components.conversationListTimestamp
import com.afsh.messages.models.NamePhoto
import kotlin.time.Duration.Companion.minutes

val Context.config: Config
    get() = Config.newInstance(applicationContext)

fun Context.getMessagesDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao
    get() = getMessagesDB().ConversationsDao()

val Context.attachmentsDB: AttachmentsDao
    get() = getMessagesDB().AttachmentsDao()

val Context.messageAttachmentsDB: MessageAttachmentsDao
    get() = getMessagesDB().MessageAttachmentsDao()

val Context.messagesDB: MessagesDao
    get() = getMessagesDB().MessagesDao()

val Context.draftsDB: DraftsDao
    get() = getMessagesDB().DraftsDao()

val Context.notificationHelper
    get() = NotificationHelper(this)

val Context.messagingUtils
    get() = MessagingUtils(this)

val Context.smsSender
    get() = SmsSender.getInstance(applicationContext as Application)

val Context.shortcutHelper get() = ShortcutHelper(this)

private fun Context.forEachUnreadSms(action: (id: Long, threadId: Long) -> Unit) {
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID),
        selection = "${Sms.READ}=0 AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { action(it.getLongValue(Sms._ID), it.getLongValue(Sms.THREAD_ID)) }
}

// Spam is left out of getUnreadCountsByThread, so this is the only place it is counted.
fun Context.getUnreadSpamCounts(): Map<Long, Int> {
    val result = HashMap<Long, Int>()
    val spamIds = messagesDB.getSpamIds().toHashSet()
    if (spamIds.isEmpty()) return result
    forEachUnreadSms { id, threadId ->
        if (id in spamIds) result[threadId] = (result[threadId] ?: 0) + 1
    }
    return result
}

/**
 * The spam SMS, newest first, read from Telephony: a marker whose SMS was deleted elsewhere shows
 * nothing. Each sender is resolved once.
 */
fun Context.getSpamMessages(): List<SpamReviewMessage> {
    class Row(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val read: Boolean,
    )

    val markers = messagesDB.getSpamMarkers().associateBy { it.id }
    if (markers.isEmpty()) return emptyList()
    val rows = ArrayList<Row>()
    // Ids are inlined rather than bound, so there is no 999-argument limit.
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.READ),
        selection = "${Sms._ID} IN (${markers.keys.joinToString()})",
        selectionArgs = null,
        showErrors = false
    ) {
        rows += Row(
            id = it.getLongValue(Sms._ID),
            threadId = it.getLongValue(Sms.THREAD_ID),
            address = it.getStringValue(Sms.ADDRESS).orEmpty(),
            body = it.getStringValue(Sms.BODY).orEmpty(),
            date = it.getLongValue(Sms.DATE),
            read = it.getIntValue(Sms.READ) == 1
        )
    }

    val senders = HashMap<Long, Pair<String, String>>()
    return rows.map { row ->
        val (title, photo) = senders.getOrPut(row.threadId) {
            val conversation = conversationsDB.getConversationWithThreadId(row.threadId)
            if (conversation != null) {
                conversation.title to conversation.photoUri
            } else {
                getNameAndPhotoFromPhoneNumber(row.address).let { it.name to it.photoUri.orEmpty() }
            }
        }
        SpamReviewMessage(
            id = row.id,
            threadId = row.threadId,
            address = row.address,
            sender = title,
            photoUri = photo,
            body = row.body,
            date = row.date,
            read = row.read,
            reason = markers.getValue(row.id).reason,
        )
    }.sortedByDescending { it.date }
}

/** The spam SMS grouped by Telephony thread, newest thread first. */
fun Context.getSpamThreads(): List<BlockedMessagesThread> =
    getSpamMessages().groupBy { it.threadId }.map { (threadId, messages) ->
        val newest = messages.first()
        BlockedMessagesThread(
            threadId = threadId,
            address = newest.address,
            title = newest.sender,
            photoUri = newest.photoUri,
            snippet = newest.body,
            date = newest.date,
            count = messages.size,
            unreadCount = messages.count { !it.read },
            reason = newest.reason,
            messageIds = messages.map { it.id }
        )
    }

// Spam deleted by another app leaves its marker behind, and extra markers can hide a thread that has real messages.
fun Context.removeOrphanSpamMarkers() {
    val markers = messagesDB.getSpamMarkers()
    if (markers.isEmpty()) return
    val markerIds = markers.map { it.id }
    // A failed query must not look like "every SMS is gone", so no cursor means no cleanup.
    val existing = contentResolver.query(Sms.CONTENT_URI, arrayOf(Sms._ID), "${Sms._ID} IN (${markerIds.joinToString()})", null, null)
        ?.use { cursor -> HashSet<Long>().apply { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        ?: return
    val removed = markers.filter { it.id !in existing }
    removed.forEach { messagesDB.deleteSpamMarker(it.id) }
    if (removed.isNotEmpty()) ConversationPreview.refresh(this, removed.map { it.threadId })
}

fun Context.getUnreadCountsByThread(): Map<Long, Int> {
    val result = HashMap<Long, Int>(128)

    fun bump(id: Long) {
        result[id] = (result[id] ?: 0) + 1
    }

    val spamIds = messagesDB.getSpamIds().toHashSet()
    forEachUnreadSms { id, threadId ->
        if (id !in spamIds) bump(threadId)
    }

    // Unread MMS
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms.THREAD_ID),
        selection = "${Mms.READ}=0 AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { bump(it.getLongValue(Mms.THREAD_ID)) }

    return result
}

fun Context.getConversations(
    threadId: Long? = null,
    privateContacts: ArrayList<SimpleContact> = ArrayList(),
    failOnError: Boolean = false,
    onProgress: ((done: Int, total: Int) -> Unit)? = null,
): ArrayList<Conversation> {
    val archiveAvailable = config.isArchiveAvailable

    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = mutableListOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS,
        Threads.MESSAGE_COUNT,
    )

    if (archiveAvailable) {
        projection += Threads.ARCHIVED
    }

    var selection = "${Threads.MESSAGE_COUNT} > 0"
    var selectionArgs = arrayOf<String>()
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs += threadId.toString()
    }

    val sortOrder = "${Threads.DATE} DESC"

    val conversations = ArrayList<Conversation>()
    val lookups = ThreadLookups(this, privateContacts, preload = threadId == null)
    val blockedNumbers = getBlockedNumbers()
    val unreadMap = getUnreadCountsByThread()
    val draftsByThread = draftsDB.getAll().associateBy { it.threadId }
    val telephony = TelephonyReader(this)
    try {
        queryCursorUnsafe(
            uri,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        ) { cursor ->
            onProgress?.invoke(cursor.position, cursor.count)
            val id = cursor.getLongValue(Threads._ID)
            var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
            if (snippet.isEmpty()) {
                snippet = telephony.getThreadSnippet(id)
            }

            var date = cursor.getLongValue(Threads.DATE)
            if (date.toString().length > 10) {
                date /= 1000
            }

            // drafts are stored locally they take priority over the original date
            val draft = draftsByThread[id]
            if (draft != null) {
                date = draft.date / 1000
            }

            val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
            val recipientIds =
                rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }.toMutableList()
            val phoneNumbers = recipientIds.mapTo(ArrayList()) { lookups.address(it) }
            if (phoneNumbers.isEmpty() || phoneNumbers.any {
                    isNumberBlocked(
                        it,
                        blockedNumbers
                    )
                }) {
                return@queryCursorUnsafe
            }

            val names = phoneNumbers.map { lookups.name(it) }
            val title = TextUtils.join(", ", names.toTypedArray())
            val photoUri = if (phoneNumbers.size == 1) lookups.photoUri(phoneNumbers.first()) else ""
            val isGroupConversation = phoneNumbers.size > 1
            val unreadCount = unreadMap[id] ?: 0
            val read = unreadCount == 0
            val archived =
                if (archiveAvailable) cursor.getIntValue(Threads.ARCHIVED) == 1 else false
            val conversation = Conversation(
                threadId = id,
                snippet = snippet,
                date = date.toInt(),
                read = read,
                title = title,
                photoUri = photoUri,
                isGroupConversation = isGroupConversation,
                phoneNumber = phoneNumbers.first(),
                isArchived = archived,
                unreadCount = unreadCount,
                messageCount = cursor.getIntValue(Threads.MESSAGE_COUNT),
            )
            conversations.add(conversation)
        }
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && archiveAvailable
        ) {
            config.isArchiveAvailable = false
            return getConversations(threadId, privateContacts, failOnError)
        } else {
            if (failOnError) throw sqliteException
            showErrorToast(sqliteException)
        }
    } catch (e: Exception) {
        if (failOnError) throw e
        showErrorToast(e)
    }

    conversations.sortByDescending { it.date }
    return conversations
}

/**
 * Per-call caches for [getConversations]. Each thread used to cost one canonical-address query per
 * recipient plus two contact lookups, so a first import of a few hundred threads took half a minute.
 * Addresses now come from one query, and names and photos come from [MessagingCache.namePhoto], which
 * outlives the call, so a later dirty-check reconcile does not look them up again.
 */
private class ThreadLookups(
    private val context: Context,
    private val privateContacts: ArrayList<SimpleContact>,
    preload: Boolean,
) {
    private val addresses: Map<Int, String>? = if (preload) loadAddresses() else null
    private val contacts = HashMap<String, Pair<String, String>>()
    private val canReadContacts = context.hasPermission(PERMISSION_READ_CONTACTS)

    fun address(id: Int): String = addresses?.get(id) ?: context.getPhoneNumberFromAddressId(id)

    /** Same order as [getThreadContactNames]: the system contact, then a private contact, then the number. */
    fun name(number: String): String {
        val name = contact(number).first
        if (name.isNotEmpty()) return name
        return privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }?.name ?: number
    }

    fun photoUri(number: String): String = contact(number).second

    // The app-wide cache is cleared when contacts change, so a refresh after the first one makes no
    // contact queries. A miss is cached as the number itself.
    private fun contact(number: String): Pair<String, String> = contacts.getOrPut(number) {
        if (!canReadContacts || number.isEmpty()) return@getOrPut "" to ""
        val found = context.getNameAndPhotoFromPhoneNumber(number)
        (if (found.name == number) "" else found.name) to found.photoUri.orEmpty()
    }

    private fun loadAddresses(): Map<Int, String>? = try {
        val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
        val map = HashMap<Int, String>()
        context.contentResolver.query(uri, arrayOf(Mms._ID, Mms.Addr.ADDRESS), null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Mms._ID)
            val addressColumn = cursor.getColumnIndexOrThrow(Mms.Addr.ADDRESS)
            while (cursor.moveToNext()) {
                map[cursor.getInt(idColumn)] = cursor.getString(addressColumn).orEmpty()
            }
        }
        map
    } catch (_: Exception) {
        // Some providers refuse an unfiltered query; fall back to one query per recipient.
        null
    }
}

internal fun Context.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit,
) {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        ?: throw IllegalStateException("Conversation provider returned no cursor")
    cursor.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Context.getConversationIds(): List<Long> {
    val projection = arrayOf(Threads._ID)
    val sortOrder = "${Threads.DATE} ASC"
    val conversationIds = mutableListOf<Long>()
    queryCursor(Threads.CONTENT_URI, projection, null, null, sortOrder, true) { cursor ->
        val id = cursor.getLongValue(Threads._ID)
        conversationIds.add(id)
    }
    return conversationIds
}

fun Context.getSmsThreadId(messageId: Long): Long? {
    if (messageId == 0L) return null
    messagesDB.getThreadIdForMessage(messageId)?.takeIf { it != 0L }?.let { return it }
    return contentResolver.query(
        Sms.CONTENT_URI,
        arrayOf(Sms.THREAD_ID),
        "${Sms._ID} = ?",
        arrayOf(messageId.toString()),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLongValue(Sms.THREAD_ID).takeIf { it != 0L } else null
    }
}

fun Context.getMessageRecipientAddress(messageId: Long): String {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(messageId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Sms.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }

    return ""
}

fun Context.getThreadParticipants(
    threadId: Long,
    contactsMap: HashMap<Int, SimpleContact>?,
): ArrayList<SimpleContact> {
    MessagingCache.participantsCache.get(threadId)?.let {
        return it.map { contact ->
            contact.copy(
                phoneNumbers = contact.phoneNumbers.toArrayList(),
                birthdays = contact.birthdays.toArrayList(),
                anniversaries = contact.anniversaries.toArrayList()
            )
        }.toArrayList()
    }

    val uri = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val number = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(number)
                    val name = namePhoto.name
                    val photoUri = namePhoto.photoUri ?: ""
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val contact = SimpleContact(
                        rawId = addressId,
                        contactId = addressId,
                        name = name,
                        photoUri = photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList()
                    )
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    MessagingCache.participantsCache.put(threadId, participants)
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(
    phoneNumbers: List<String>,
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach { number ->
        val name = SimpleContactsHelper(this).getNameFromPhoneNumber(number)
        if (name != number) {
            names.add(name)
        } else {
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
            if (privateContact == null) {
                names.add(name)
            } else {
                names.add(privateContact.name)
            }
        }
    }
    return names
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

fun Context.getSuggestedContacts(
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val sortOrder = "${Sms.DATE} DESC LIMIT 50"
    val blockedNumbers = getBlockedNumbers()

    queryCursor(uri, projection, null, null, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        var senderName = namePhoto.name
        var photoUri = namePhoto.photoUri ?: ""
        if (isNumberBlocked(senderNumber, blockedNumbers)) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull {
                    it.phoneNumbers.first().normalizedNumber == senderNumber
                }
                if (privateContact != null) {
                    senderName = privateContact.name
                    photoUri = privateContact.photoUri
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        }

        val phoneNumber = PhoneNumber(senderNumber, 0, "", senderNumber)
        val contact = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(phoneNumber),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )
        if (!contacts.map { it.phoneNumbers.first().normalizedNumber.trimToComparableNumber() }
                .contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
    MessagingCache.namePhoto.get(number)?.let { return it }
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI
    )

    val result = try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                NamePhoto(name, photoUri)
            } else {
                NamePhoto(number, null)
            }
        }
    } catch (_: Exception) {
        NamePhoto(number, null)
    }

    MessagingCache.namePhoto.put(number, result)
    return result
}

fun Context.insertNewSMS(
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int,
): Long {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    return try {
        val newUri = contentResolver.insert(uri, contentValues)
        newUri?.lastPathSegment?.toLong() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun Context.deleteConversation(threadId: Long) {
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    contentResolver.delete(Sms.CONTENT_URI, selection, selectionArgs)
    contentResolver.delete(Mms.CONTENT_URI, selection, selectionArgs)

    conversationsDB.deleteThreadId(threadId)
    messagesDB.deleteThreadMessages(threadId)
    MessagingCache.participantsCache.remove(threadId)

    if (config.customNotifications.contains(threadId.toString())) {
        config.removeCustomNotificationsByThreadId(threadId)
        notificationManager.deleteNotificationChannel(threadId.toString())
    }
    if(shortcutHelper.getShortcut(threadId) != null) {
        shortcutHelper.removeShortcutForThread(threadId)
    }
}

fun Context.updateConversationArchivedStatus(threadId: Long, archived: Boolean) {
    val uri = Threads.CONTENT_URI
    val values = ContentValues().apply {
        put(Threads.ARCHIVED, archived)
    }
    val selection = "${Threads._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.update(uri, values, selection, selectionArgs)
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && config.isArchiveAvailable
        ) {
            config.isArchiveAvailable = false
            return
        } else {
            throw sqliteException
        }
    }
    if (archived) {
        conversationsDB.moveToArchive(threadId)
    } else {
        conversationsDB.unarchive(threadId)
    }
}

fun Context.deleteMessage(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
        messagesDB.delete(id)
        if (!isMMS) messagesDB.deleteSpamMarker(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.deleteScheduledMessage(messageId: Long) {
    try {
        messagesDB.delete(messageId)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.markMessageRead(id: Long, isMMS: Boolean): Boolean {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    if (contentResolver.update(uri, contentValues, selection, selectionArgs) == 0) return false
    messagesDB.markRead(id, isMMS)
    return true
}

fun Context.markVisibleMessagesRead(threadId: Long, visibleMessages: List<Message>): Set<Long> {
    val completed = HashSet<Long>()
    visibleMessages.filter { it.threadId == threadId && it.isReceivedMessage() && !it.isScheduled && !it.read }.forEach { message ->
        try {
            if (markMessageRead(message.id, message.isMMS)) completed.add(message.getStableId())
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
    val count = getUnreadCountsByThread()[threadId] ?: 0
    conversationsDB.updateUnreadCount(threadId, count)
    MessagingEvents.publish(MessagingEvent.ReadStateChanged(threadId, count == 0, count))
    return completed
}

fun Context.markThreadMessagesRead(threadId: Long) {
    val id = threadId.toString()

    val smsValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val smsSelection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=? AND (${Sms.READ}=? OR ${Sms.SEEN}=?)${notSpamSelection(threadId)}"
    val smsArgs = arrayOf(id, Sms.MESSAGE_TYPE_INBOX.toString(), "0", "0")
    contentResolver.update(Sms.CONTENT_URI, smsValues, smsSelection, smsArgs)

    val mmsValues = ContentValues().apply {
        put(Mms.READ, 1)
        put(Mms.SEEN, 1)
    }
    val mmsSelection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=? AND (${Mms.READ}=? OR ${Mms.SEEN}=?)"
    val mmsArgs = arrayOf(id, Mms.MESSAGE_BOX_INBOX.toString(), "0", "0")
    contentResolver.update(Mms.CONTENT_URI, mmsValues, mmsSelection, mmsArgs)

    messagesDB.markThreadRead(threadId)
    conversationsDB.markRead(threadId)
    config.clearManuallyUnread(threadId)
    MessagingEvents.publish(MessagingEvent.ReadStateChanged(threadId, read = true, unreadCount = 0))
}

// Read state of spam belongs to the Spam screen, so thread-wide read and unread leave it alone.
private fun Context.notSpamSelection(threadId: Long): String {
    val spamIds = messagesDB.getThreadSpamIds(threadId)
    return if (spamIds.isEmpty()) "" else " AND ${Sms._ID} NOT IN (${spamIds.joinToString()})"
}

/**
 * Marks only the newest received message unread, as Google Messages does, so the inbox shows a dot
 * rather than the size of the whole thread. The message is remembered as manually unread, so the
 * thread opens without a "new messages" divider above it.
 */
fun Context.markThreadMessagesUnread(threadId: Long) {
    val id = threadId.toString()
    var newestSms: Pair<Long, Long>? = null // id to date in ms
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.DATE),
        selection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}${notSpamSelection(threadId)}",
        selectionArgs = arrayOf(id),
        sortOrder = "${Sms.DATE} DESC LIMIT 1",
        showErrors = false,
    ) { newestSms = it.getLongValue(Sms._ID) to it.getLongValue(Sms.DATE) }

    var newestMms: Pair<Long, Long>? = null // id to date in ms
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms._ID, Mms.DATE),
        selection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
        selectionArgs = arrayOf(id),
        sortOrder = "${Mms.DATE} DESC LIMIT 1",
        showErrors = false,
    ) { newestMms = it.getLongValue(Mms._ID) to it.getLongValue(Mms.DATE) * 1000 }

    val sms = newestSms
    val mms = newestMms
    val target = if (mms != null && (sms == null || mms.second > sms.second)) {
        mms.first to true
    } else {
        sms?.let { it.first to false }
    }
    if (target != null) {
        val (messageId, isMms) = target
        val uri = if (isMms) Mms.CONTENT_URI else Sms.CONTENT_URI
        val values = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        contentResolver.update(uri, values, "${Sms._ID}=?", arrayOf(messageId.toString()))
        messagesDB.markUnread(messageId, isMms)
        config.setManuallyUnread(threadId, messageId, isMms)
    }
    val count = getUnreadCountsByThread()[threadId] ?: 0
    if (count > 0) conversationsDB.updateUnreadCount(threadId, count) else conversationsDB.markUnread(threadId)
    MessagingEvents.publish(MessagingEvent.ReadStateChanged(threadId, read = false, unreadCount = count.takeIf { it > 0 }))
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return try {
        Threads.getOrCreateThreadId(this, address)
    } catch (_: Exception) {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return try {
        Threads.getOrCreateThreadId(this, addresses)
    } catch (_: Exception) {
        0L
    }
}

fun Context.getNameFromAddress(address: String, privateCursor: Cursor?): String {
    var sender = getNameAndPhotoFromPhoneNumber(address).name
    if (address == sender) {
        val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
        sender = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }?.name ?: address
    }
    return sender
}

fun Context.getContactFromAddress(address: String, callback: ((contact: SimpleContact?) -> Unit)) {
    val privateCursor = getMyContactsCursor(false, true)
    SimpleContactsHelper(this).getAvailableContacts(false) {
        val contact = it.firstOrNull { it.doesHavePhoneNumber(address) }
        if (contact == null) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }
            callback(privateContact)
        } else {
            callback(contact)
        }
    }
}

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .centerCrop()

    return try {
        Glide.with(this)
            .asBitmap()
            .load(photoUri)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()
    } catch (_: Exception) {
        null
    }
}

fun Context.updateLastConversationMessage(threadId: Long) {
    updateLastConversationMessage(setOf(threadId))
}

fun Context.updateLastConversationMessage(threadIds: Iterable<Long>) {
    // update the date and the snippet of the threads, by triggering the
    // following Android code (which runs even if no messages are deleted):
    // https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android14-release/src/com/android/providers/telephony/MmsSmsProvider.java#1409
    val uri = Threads.CONTENT_URI
    val selection =
        "1 = 0" // always-false condition, because we don't actually want to delete any messages
    try {
        contentResolver.delete(uri, selection, null)
        for (threadId in threadIds) {
            val newConversation = getConversations(threadId)[0]
            insertOrUpdateConversation(newConversation)
        }
    } catch (_: Exception) {
    }
}

// fix a glitch at enabling Release version minifying from 5.12.3
// reset messages in 5.14.3 again, as PhoneNumber is no longer minified
// reset messages in 5.19.1 again, as SimpleContact is no longer minified
fun Context.clearAllMessagesIfNeeded(callback: () -> Unit) {
    if (!config.wasDbCleared) {
        ensureBackgroundThread {
            messagesDB.deleteAll()
            config.wasDbCleared = true
            Handler(Looper.getMainLooper()).post(callback)
        }
    } else {
        callback()
    }
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}

/**
 * Merges a Telephony [incoming] conversation snapshot with the cached Room row while preserving
 * app-owned metadata, optimistic local send bumps, and protection against stale reconcile reads.
 *
 * A stale provider snapshot (no newer date and no higher message count) must not undo a local
 * mark-read, whereas a newly arrived message must immediately surface its unread state and count.
 */
internal fun mergeConversationWithCache(
    incoming: Conversation,
    cached: Conversation?,
): Conversation {
    if (cached == null) return incoming
    var updated = incoming
    if (cached.usesCustomTitle) {
        updated = updated.copy(
            title = cached.title,
            usesCustomTitle = true,
        )
    }
    if (cached.isScheduled) {
        updated = updated.copy(
            date = cached.date,
            isScheduled = true,
        )
    }
    // Keep a newer local preview (e.g. optimistic send bump) until Telephony catches up.
    if (cached.date > updated.date) {
        updated = updated.copy(
            date = cached.date,
            snippet = cached.snippet,
        )
    }
    val hasNewerMessage = incoming.date > cached.date || incoming.messageCount > cached.messageCount
    if (cached.read && cached.unreadCount == 0 && !updated.read && !hasNewerMessage) {
        updated = updated.copy(read = true, unreadCount = 0)
    }
    // REPLACE would clear denormalized preview columns; keep them until refresh below.
    return updated.copy(
        previewSnippet = cached.previewSnippet,
        previewDate = cached.previewDate,
        allSpam = cached.allSpam,
    )
}

fun Context.insertOrUpdateConversation(
    conversation: Conversation,
    cachedConv: Conversation? = conversationsDB.getConversationWithThreadId(conversation.threadId),
) {
    val updatedConv = mergeConversationWithCache(conversation, cachedConv)
    conversationsDB.insertOrUpdate(updatedConv)
    ConversationPreview.refresh(this, updatedConv.threadId)
}

fun Context.renameConversation(conversation: Conversation, newTitle: String): Conversation {
    val updatedConv = conversation.copy(title = newTitle, usesCustomTitle = true)
    try {
        conversationsDB.insertOrUpdate(updatedConv)
        ensureBackgroundThread {
            shortcutHelper.createOrUpdateShortcut(updatedConv)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return updatedConv
}

fun Context.createTemporaryThread(
    message: Message,
    threadId: Long = generateRandomId(),
    cachedConv: Conversation?,
) {
    val simpleContactHelper = SimpleContactsHelper(this)
    val addresses = message.participants.getAddresses()
    val photoUri = if (addresses.size == 1) {
        simpleContactHelper.getPhotoUriFromPhoneNumber(addresses.first())
    } else {
        ""
    }

    val title = if (cachedConv != null && cachedConv.usesCustomTitle) {
        cachedConv.title
    } else {
        message.participants.getThreadTitle()
    }

    val conversation = Conversation(
        threadId = threadId,
        snippet = message.body,
        date = message.date,
        read = true,
        title = title,
        photoUri = photoUri,
        isGroupConversation = addresses.size > 1,
        phoneNumber = addresses.first(),
        isScheduled = true,
        usesCustomTitle = cachedConv?.usesCustomTitle == true,
        isArchived = false,
        unreadCount = 0,
    )
    try {
        conversationsDB.insertOrUpdate(conversation)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.updateScheduledMessagesThreadId(messages: List<Message>, newThreadId: Long) {
    val scheduledMessages = messages.map { it.copy(threadId = newThreadId) }.toTypedArray()
    messagesDB.insertMessages(*scheduledMessages)
}

fun Context.clearExpiredScheduledMessages(threadId: Long, messagesToDelete: List<Message>? = null) {
    val messages = messagesToDelete ?: messagesDB.getScheduledThreadMessages(threadId)
    val cutoff = System.currentTimeMillis() - 1.minutes.inWholeMilliseconds

    try {
        messages.filter { it.isScheduled && it.millis() < cutoff }.forEach { msg ->
            messagesDB.delete(msg.id)
        }
        if (messages.filterNot { it.isScheduled && it.millis() < cutoff }.isEmpty()) {
            // delete empty temporary thread
            val conversation = conversationsDB.getConversationWithThreadId(threadId)
            if (conversation != null && conversation.isScheduled) {
                conversationsDB.deleteThreadId(threadId)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

fun Context.rescheduleAllScheduledMessages() {
    val scheduledMessages = try {
        messagesDB.getAllScheduledMessages()
    } catch (_: Exception) {
        return
    }

    scheduledMessages.forEach { message ->
        runCatching { scheduleMessage(message) }
    }
}

fun Context.getDefaultKeyboardHeight(): Int {
    return resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
}

fun Context.shouldUnarchive(): Boolean {
    return config.isArchiveAvailable && !config.keepConversationsArchived
}

fun Context.messageSearchResult(message: Message): SearchResult {
    var title = message.senderName
    if (title.isEmpty() && message.participants.isNotEmpty()) {
        title = TextUtils.join(", ", message.participants.map { it.name })
    }

    val date = conversationListTimestamp(this, message.date * 1000L)

    return SearchResult(
        messageId = message.id,
        title = title,
        snippet = message.body,
        date = date,
        threadId = message.threadId,
        photoUri = message.senderPhotoUri,
        isMms = message.isMMS,
    )
}

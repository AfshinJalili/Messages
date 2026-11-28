package com.afsh.messages.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import android.text.TextUtils
import androidx.core.net.toUri
import com.afsh.commons.extensions.areDigitsOnly
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.extensions.getIntValue
import com.afsh.commons.extensions.getLongValue
import com.afsh.commons.extensions.getStringValue
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.extensions.isNumberBlocked
import com.afsh.commons.extensions.queryCursor
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getNameAndPhotoFromPhoneNumber
import com.afsh.messages.extensions.getPhoneNumberFromAddressId
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.queryCursorUnsafe
import com.afsh.messages.models.Conversation

/** One SMS row as the Spam screen needs it. */
data class TelephonySms(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
)

/**
 * The Telephony side of conversations: the threads table and the read, unread and archived flags of
 * SMS and MMS. [ConversationRepository] is its only caller; it pairs every write here with the
 * matching Room write. Every call is a blocking content-provider query.
 */
interface ConversationTelephony {
    /** Unread inbox SMS as `id to threadId`, spam included. */
    fun unreadSms(): List<Pair<Long, Long>>

    /** The thread id of each unread inbox MMS, once per message. */
    fun unreadMmsThreadIds(): List<Long>

    /**
     * Every thread with messages ([threadId] only, when given), newest first, or the newest [limit]
     * of them. Threads without participants or with a blocked one are left out (see [inboxShows]).
     * [unreadCounts] gives each row its unread count, and [draftDates] (milliseconds) dates a thread whose
     * draft is newer than its last message (see [threadDate]).
     */
    fun conversations(
        threadId: Long?,
        privateContacts: List<SimpleContact>,
        unreadCounts: Map<Long, Int>,
        draftDates: Map<Long, Long>,
        failOnError: Boolean,
        onProgress: ((done: Int, total: Int) -> Unit)?,
        limit: Int?,
    ): ArrayList<Conversation>

    /** How many threads [conversations] keeps when it reads them all; -1 when the provider can't be read. */
    fun inboxThreadCount(): Int

    /**
     * The thread of exactly [addresses], as Android files a message sent to them; 0 when the provider
     * can't say. Android creates an empty thread when there is none yet.
     */
    fun threadIdFor(addresses: Set<String>): Long

    /** The SMS among [ids] that still exist. */
    fun smsRows(ids: Collection<Long>): List<TelephonySms>

    /** The ids among [ids] that still exist, or null when the query failed. */
    fun existingSmsIds(ids: Collection<Long>): Set<Long>?

    /** The thread of SMS [messageId], or null. */
    fun smsThreadId(messageId: Long): Long?

    /** Deletes every SMS and MMS of [threadId]. */
    fun deleteThread(threadId: Long)

    /** Sets the thread's archived flag. False when this Telephony has no archive column. */
    fun setArchived(threadId: Long, archived: Boolean): Boolean

    /** Marks one message read and seen. False when no row changed. */
    fun markMessageRead(id: Long, isMms: Boolean): Boolean

    /** Marks every incoming SMS and MMS of [threadId] read and seen, except the SMS in [skipSmsIds]. */
    fun markThreadRead(threadId: Long, skipSmsIds: Collection<Long>)

    /** The newest incoming message of [threadId] as `id to isMms`, ignoring the SMS in [skipSmsIds]. */
    fun newestIncoming(threadId: Long, skipSmsIds: Collection<Long>): Pair<Long, Boolean>?

    /** Marks one message unread and unseen. */
    fun markMessageUnread(id: Long, isMms: Boolean)

    /** Makes Telephony recompute every thread's date and snippet. */
    fun touchThreads()
}

/** [ConversationTelephony] over the system Telephony provider. */
class TelephonyConversations(context: Context) : ConversationTelephony {
    private val context: Context = context.applicationContext
    private val resolver get() = context.contentResolver

    override fun unreadSms(): List<Pair<Long, Long>> {
        val result = ArrayList<Pair<Long, Long>>()
        context.queryCursor(
            uri = Sms.CONTENT_URI,
            projection = arrayOf(Sms._ID, Sms.THREAD_ID),
            selection = "${Sms.READ}=0 AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}",
            selectionArgs = null,
            showErrors = false
        ) { result += it.getLongValue(Sms._ID) to it.getLongValue(Sms.THREAD_ID) }
        return result
    }

    override fun unreadMmsThreadIds(): List<Long> {
        val result = ArrayList<Long>()
        context.queryCursor(
            uri = Mms.CONTENT_URI,
            projection = arrayOf(Mms.THREAD_ID),
            selection = "${Mms.READ}=0 AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
            selectionArgs = null,
            showErrors = false
        ) { result += it.getLongValue(Mms.THREAD_ID) }
        return result
    }

    override fun conversations(
        threadId: Long?,
        privateContacts: List<SimpleContact>,
        unreadCounts: Map<Long, Int>,
        draftDates: Map<Long, Long>,
        failOnError: Boolean,
        onProgress: ((done: Int, total: Int) -> Unit)?,
        limit: Int?,
    ): ArrayList<Conversation> {
        val config = context.config
        val archiveAvailable = config.isArchiveAvailable

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

        var selection = THREADS_WITH_MESSAGES
        var selectionArgs = arrayOf<String>()
        if (threadId != null) {
            selection += " AND ${Threads._ID} = ?"
            selectionArgs += threadId.toString()
        }

        val lookups = ThreadLookups(context, privateContacts, preload = threadId == null)
        val blocked = blockedTest()
        val telephony = TelephonyReader(context)
        fun read(sortOrder: String): ArrayList<Conversation> {
            val conversations = ArrayList<Conversation>()
            resolver.queryCursorUnsafe(
                THREADS_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            ) { cursor ->
                // Rows past the window are skipped unbuilt when the provider ignored or rejected LIMIT.
                if (limit != null && conversations.size >= limit) return@queryCursorUnsafe
                onProgress?.invoke(cursor.position, cursor.count)
                val id = cursor.getLongValue(Threads._ID)
                var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
                if (snippet.isEmpty()) {
                    snippet = telephony.getThreadSnippet(id)
                }

                val date = threadDate(cursor.getLongValue(Threads.DATE), draftDates[id])

                val recipientIds = recipientIds(cursor.getStringValue(Threads.RECIPIENT_IDS))
                if (!inboxShows(recipientIds, lookups::address, blocked)) return@queryCursorUnsafe
                val phoneNumbers = recipientIds.map(lookups::address)

                val names = phoneNumbers.map { lookups.name(it) }
                val title = TextUtils.join(", ", names.toTypedArray())
                val photoUri = if (phoneNumbers.size == 1) lookups.photoUri(phoneNumbers.first()) else ""
                val isGroupConversation = phoneNumbers.size > 1
                val unreadCount = unreadCounts[id] ?: 0
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
            return conversations
        }

        val conversations = try {
            ArrayList(newestThreadsFirst(limit, ::read))
        } catch (sqliteException: SQLiteException) {
            if (
                sqliteException.message?.contains("no such column: archived") == true
                && archiveAvailable
            ) {
                config.isArchiveAvailable = false
                return conversations(threadId, privateContacts, unreadCounts, draftDates, failOnError, onProgress = null, limit = limit)
            } else {
                if (failOnError) throw sqliteException
                context.showErrorToast(sqliteException)
                ArrayList()
            }
        } catch (e: Exception) {
            if (failOnError) throw e
            context.showErrorToast(e)
            ArrayList()
        }

        conversations.sortByDescending { it.date }
        return conversations
    }

    override fun inboxThreadCount(): Int {
        val lookups = ThreadLookups(context, privateContacts = emptyList(), preload = true)
        val blocked = blockedTest()
        var count = 0
        return try {
            resolver.queryCursorUnsafe(THREADS_URI, arrayOf(Threads.RECIPIENT_IDS), THREADS_WITH_MESSAGES, emptyArray(), null) { cursor ->
                if (inboxShows(recipientIds(cursor.getStringValue(Threads.RECIPIENT_IDS)), lookups::address, blocked)) count++
            }
            count
        } catch (_: Exception) {
            -1
        }
    }

    override fun threadIdFor(addresses: Set<String>): Long = context.getThreadId(addresses)

    override fun smsRows(ids: Collection<Long>): List<TelephonySms> {
        val rows = ArrayList<TelephonySms>()
        // Ids are inlined rather than bound, so there is no 999-argument limit.
        context.queryCursor(
            uri = Sms.CONTENT_URI,
            projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.READ),
            selection = "${Sms._ID} IN (${ids.joinToString()})",
            selectionArgs = null,
            showErrors = false
        ) {
            rows += TelephonySms(
                id = it.getLongValue(Sms._ID),
                threadId = it.getLongValue(Sms.THREAD_ID),
                address = it.getStringValue(Sms.ADDRESS).orEmpty(),
                body = it.getStringValue(Sms.BODY).orEmpty(),
                date = it.getLongValue(Sms.DATE),
                read = it.getIntValue(Sms.READ) == 1
            )
        }
        return rows
    }

    override fun existingSmsIds(ids: Collection<Long>): Set<Long>? =
        resolver.query(Sms.CONTENT_URI, arrayOf(Sms._ID), "${Sms._ID} IN (${ids.joinToString()})", null, null)
            ?.use { cursor -> HashSet<Long>().apply { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    override fun smsThreadId(messageId: Long): Long? = resolver.query(
        Sms.CONTENT_URI,
        arrayOf(Sms.THREAD_ID),
        "${Sms._ID} = ?",
        arrayOf(messageId.toString()),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLongValue(Sms.THREAD_ID).takeIf { it != 0L } else null
    }

    override fun deleteThread(threadId: Long) {
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        resolver.delete(Sms.CONTENT_URI, selection, selectionArgs)
        resolver.delete(Mms.CONTENT_URI, selection, selectionArgs)
    }

    override fun setArchived(threadId: Long, archived: Boolean): Boolean {
        val values = ContentValues().apply {
            put(Threads.ARCHIVED, archived)
        }
        val selection = "${Threads._ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        try {
            resolver.update(Threads.CONTENT_URI, values, selection, selectionArgs)
        } catch (sqliteException: SQLiteException) {
            val config = context.config
            if (
                sqliteException.message?.contains("no such column: archived") == true
                && config.isArchiveAvailable
            ) {
                config.isArchiveAvailable = false
                return false
            } else {
                throw sqliteException
            }
        }
        return true
    }

    override fun markMessageRead(id: Long, isMms: Boolean): Boolean {
        val uri = if (isMms) Mms.CONTENT_URI else Sms.CONTENT_URI
        val contentValues = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        return resolver.update(uri, contentValues, "${Sms._ID} = ?", arrayOf(id.toString())) != 0
    }

    override fun markThreadRead(threadId: Long, skipSmsIds: Collection<Long>) {
        val id = threadId.toString()

        val smsValues = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        val smsSelection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=? AND (${Sms.READ}=? OR ${Sms.SEEN}=?)${notIn(skipSmsIds)}"
        val smsArgs = arrayOf(id, Sms.MESSAGE_TYPE_INBOX.toString(), "0", "0")
        resolver.update(Sms.CONTENT_URI, smsValues, smsSelection, smsArgs)

        val mmsValues = ContentValues().apply {
            put(Mms.READ, 1)
            put(Mms.SEEN, 1)
        }
        val mmsSelection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=? AND (${Mms.READ}=? OR ${Mms.SEEN}=?)"
        val mmsArgs = arrayOf(id, Mms.MESSAGE_BOX_INBOX.toString(), "0", "0")
        resolver.update(Mms.CONTENT_URI, mmsValues, mmsSelection, mmsArgs)
    }

    override fun newestIncoming(threadId: Long, skipSmsIds: Collection<Long>): Pair<Long, Boolean>? {
        val id = threadId.toString()
        var newestSms: Pair<Long, Long>? = null // id to date in ms
        context.queryCursor(
            uri = Sms.CONTENT_URI,
            projection = arrayOf(Sms._ID, Sms.DATE),
            selection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}${notIn(skipSmsIds)}",
            selectionArgs = arrayOf(id),
            sortOrder = "${Sms.DATE} DESC LIMIT 1",
            showErrors = false,
        ) { newestSms = it.getLongValue(Sms._ID) to it.getLongValue(Sms.DATE) }

        var newestMms: Pair<Long, Long>? = null // id to date in ms
        context.queryCursor(
            uri = Mms.CONTENT_URI,
            projection = arrayOf(Mms._ID, Mms.DATE),
            selection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
            selectionArgs = arrayOf(id),
            sortOrder = "${Mms.DATE} DESC LIMIT 1",
            showErrors = false,
        ) { newestMms = it.getLongValue(Mms._ID) to it.getLongValue(Mms.DATE) * 1000 }

        val sms = newestSms
        val mms = newestMms
        return if (mms != null && (sms == null || mms.second > sms.second)) {
            mms.first to true
        } else {
            sms?.let { it.first to false }
        }
    }

    override fun markMessageUnread(id: Long, isMms: Boolean) {
        val uri = if (isMms) Mms.CONTENT_URI else Sms.CONTENT_URI
        val values = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        resolver.update(uri, values, "${Sms._ID}=?", arrayOf(id.toString()))
    }

    override fun touchThreads() {
        // Updates the date and the snippet of every thread, by triggering the following Android code
        // (which runs even if no messages are deleted):
        // https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android14-release/src/com/android/providers/telephony/MmsSmsProvider.java#1409
        // The always-false condition deletes nothing.
        resolver.delete(Threads.CONTENT_URI, "1 = 0", null)
    }

    private fun notIn(ids: Collection<Long>): String =
        if (ids.isEmpty()) "" else " AND ${Sms._ID} NOT IN (${ids.joinToString()})"

    /** Whether a number is blocked, or null when nothing is. Reads the block list once per call. */
    private fun blockedTest(): ((String) -> Boolean)? {
        val blockedNumbers = context.getBlockedNumbers()
        return if (blockedNumbers.isEmpty()) null else { number -> context.isNumberBlocked(number, blockedNumbers) }
    }

    private companion object {
        val THREADS_URI: Uri = "${Threads.CONTENT_URI}?simple=true".toUri()
        const val THREADS_WITH_MESSAGES = "${Threads.MESSAGE_COUNT} > 0"
    }
}

/**
 * A thread's date in seconds: Telephony's [providerDate] (seconds, or milliseconds on some providers), or
 * its draft's [draftMillis] when that is later. A fresh draft lifts the thread; an old one must not sink it
 * below its newest message.
 */
internal fun threadDate(providerDate: Long, draftMillis: Long?): Long {
    val date = if (providerDate.toString().length > 10) providerDate / 1000 else providerDate
    return if (draftMillis == null) date else maxOf(date, draftMillis / 1000)
}

/** The canonical address ids of a thread, from the threads table's space-separated list. */
internal fun recipientIds(raw: String?): List<Int> =
    raw.orEmpty().split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }

/**
 * Whether the inbox shows a thread with [recipientIds]: it needs one, and none may be [blocked]
 * ([address] resolves an id). With [blocked] null nothing is blocked, and no address is resolved.
 */
internal fun inboxShows(recipientIds: List<Int>, address: (Int) -> String, blocked: ((String) -> Boolean)?): Boolean =
    recipientIds.isNotEmpty() && (blocked == null || recipientIds.none { blocked(address(it)) })

/**
 * Per-call caches for reading the threads table ([TelephonyConversations.conversations],
 * [TelephonyConversations.inboxThreadCount]). Each thread used to cost one
 * canonical-address query per recipient plus two contact lookups, so a first import of a few hundred
 * threads took half a minute. Addresses now come from one query, and names and photos come from
 * [MessagingCache.namePhoto], which outlives the call, so a later dirty-check reconcile does not look
 * them up again.
 */
private class ThreadLookups(
    private val context: Context,
    private val privateContacts: List<SimpleContact>,
    preload: Boolean,
) {
    // Read on first use: counting threads with nothing blocked needs no address.
    private val addresses: Map<Int, String>? by lazy { if (preload) loadAddresses() else null }
    private val queried = HashMap<Int, String>()
    private val contacts = HashMap<String, Pair<String, String>>()
    private val canReadContacts = context.hasPermission(PERMISSION_READ_CONTACTS)

    fun address(id: Int): String = addresses?.get(id) ?: queried.getOrPut(id) { context.getPhoneNumberFromAddressId(id) }

    /** The system contact, then a private contact, then the number. */
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

/**
 * Runs [query] over the threads table newest first, keeping at most [limit] rows when given. The
 * provider has no limit argument, so LIMIT rides in the sort order; some OEM providers reject that,
 * and then the threads are read unlimited and cut. A failed unlimited read stays a failure.
 */
internal fun <T> newestThreadsFirst(limit: Int?, query: (sortOrder: String) -> List<T>): List<T> {
    val newestFirst = "${Threads.DATE} DESC"
    if (limit == null) return query(newestFirst)
    val rows = try {
        query("$newestFirst LIMIT $limit")
    } catch (_: Exception) {
        query(newestFirst)
    }
    return rows.take(limit)
}

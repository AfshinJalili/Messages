package com.afsh.messages.helpers

import android.content.Context
import android.provider.Telephony.Sms
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.insertNewSMS
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.models.SpamMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Moves the legacy `blocked_messages` copies onto Telephony SMS with spam markers, then drops the table.
 * Safe to rerun: an SMS that already exists is matched instead of inserted, markers ignore duplicates,
 * and each row is removed once it is handled. A failed row stays and is retried on the next launch.
 */
object SpamBackfill {
    private const val TABLE = "blocked_messages"
    private const val MATCH_WINDOW_MS = 60_000L

    // Two overlapping runs could both miss a match and insert the same SMS twice.
    private val running = AtomicBoolean(false)

    /** A legacy `blocked_messages` copy. */
    internal class Row(val id: Long, val address: String, val body: String, val date: Long, val reason: Int)

    /** A Telephony message a legacy copy was made from. */
    internal data class Stored(val id: Long, val isMms: Boolean)

    /** What moving a row needs from Android: settings, Telephony and the spam markers. */
    internal interface Platform {
        fun isAllowed(address: String): Boolean
        /** The Telephony thread for [address], or 0 when it can't be had. */
        fun threadId(address: String): Long
        /** The message [row] was copied from: same thread and body, received within a minute. */
        fun findStored(threadId: Long, row: Row): Stored?
        /** Inserts [row] as an incoming SMS; its id, or 0 when the insert failed. */
        fun insertSms(threadId: Long, row: Row): Long
        fun markSpam(smsId: Long, threadId: Long, reason: Int)
    }

    fun run(context: Context) {
        if (!running.compareAndSet(false, true)) return
        ensureBackgroundThread {
            try {
                backfill(context.applicationContext)
            } finally {
                running.set(false)
            }
        }
    }

    private fun backfill(app: Context) {
        val db = app.getMessagesDB().openHelper.writableDatabase
        val exists = db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$TABLE'").use { it.moveToFirst() }
        if (!exists) return

        val rows = db.query("SELECT id, address, body, date, reason FROM $TABLE").use { cursor ->
            generateSequence {
                if (cursor.moveToNext()) Row(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getInt(4)) else null
            }.toList()
        }
        val platform = AndroidPlatform(app)
        var failed = false
        rows.forEach { row ->
            val moved = try {
                move(row, platform)
            } catch (_: Exception) {
                false
            }
            if (moved) db.execSQL("DELETE FROM $TABLE WHERE id = ?", arrayOf(row.id)) else failed = true
        }
        if (!failed) db.execSQL("DROP TABLE IF EXISTS $TABLE")
        if (rows.isNotEmpty()) {
            val threads = rows.mapNotNull { row ->
                runCatching { app.getThreadId(row.address) }.getOrNull()?.takeIf { it != 0L }
            }.distinct()
            ConversationPreview.refresh(app, threads)
            InboxRepository.scheduleProviderReconcile(app, immediate = true)
        }
    }

    /** Puts [row] on Telephony with a spam marker. False leaves the row for the next launch. */
    internal fun move(row: Row, platform: Platform): Boolean {
        // The user already said this sender is not spam.
        if (platform.isAllowed(row.address)) return true
        val threadId = platform.threadId(row.address)
        if (threadId == 0L) return false
        val match = platform.findStored(threadId, row)
        // MMS copies came from MmsReceiver; the MMS itself is in Telephony and markers are SMS-only.
        if (match?.isMms == true) return true
        val smsId = match?.id ?: platform.insertSms(threadId, row)
        if (smsId == 0L) return false
        platform.markSpam(smsId, threadId, row.reason)
        return true
    }

    /** The Telephony message a legacy copy was made from: same thread and body, received within a minute. */
    internal fun findStored(context: Context, threadId: Long, body: String, date: Long): Stored? {
        val before = ((date + MATCH_WINDOW_MS) / 1000).toInt() + 1
        return TelephonyReader(context).getMessages(threadId, dateFrom = before, includeScheduledMessages = false)
            .firstOrNull { it.body == body && abs(it.millis() - date) < MATCH_WINDOW_MS }
            ?.let { Stored(it.id, it.isMMS) }
    }

    private class AndroidPlatform(private val app: Context) : Platform {
        override fun isAllowed(address: String) = app.config.allowedNumbers.containsNumber(address)
        override fun threadId(address: String) = app.getThreadId(address)
        override fun findStored(threadId: Long, row: Row) = findStored(app, threadId, row.body, row.date)
        override fun insertSms(threadId: Long, row: Row) = app.insertNewSMS(
            address = row.address,
            subject = "",
            body = row.body,
            date = row.date,
            read = 0,
            threadId = threadId,
            type = Sms.MESSAGE_TYPE_INBOX,
            subscriptionId = -1,
        )

        override fun markSpam(smsId: Long, threadId: Long, reason: Int) {
            app.messagesDB.insertSpamMarker(SpamMessage(id = smsId, threadId = threadId, reason = reason))
        }
    }
}

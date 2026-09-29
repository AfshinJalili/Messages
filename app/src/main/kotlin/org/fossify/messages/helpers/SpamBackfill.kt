package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Sms
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getMessagesDB
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.insertNewSMS
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.Message
import org.fossify.messages.models.SpamMessage
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

    private class Row(val id: Long, val address: String, val body: String, val date: Long, val reason: Int)

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
        var failed = false
        rows.forEach { row ->
            val moved = try {
                app.moveRow(row)
            } catch (_: Exception) {
                false
            }
            if (moved) db.execSQL("DELETE FROM $TABLE WHERE id = ?", arrayOf(row.id)) else failed = true
        }
        if (!failed) db.execSQL("DROP TABLE IF EXISTS $TABLE")
        if (rows.isNotEmpty()) InboxRepository.scheduleProviderReconcile(app, immediate = true)
    }

    private fun Context.moveRow(row: Row): Boolean {
        // The user already said this sender is not spam.
        if (config.allowedNumbers.containsNumber(row.address)) return true
        val threadId = getThreadId(row.address)
        if (threadId == 0L) return false
        val match = findStoredMessage(threadId, row.body, row.date)
        // MMS copies came from MmsReceiver; the MMS itself is in Telephony and markers are SMS-only.
        if (match?.isMMS == true) return true
        val smsId = match?.id ?: insertNewSMS(
            address = row.address,
            subject = "",
            body = row.body,
            date = row.date,
            read = 0,
            threadId = threadId,
            type = Sms.MESSAGE_TYPE_INBOX,
            subscriptionId = -1,
        )
        if (smsId == 0L) return false
        messagesDB.insertSpamMarker(SpamMessage(id = smsId, threadId = threadId, reason = row.reason))
        return true
    }

    /** The Telephony message a legacy copy was made from: same thread and body, received within a minute. */
    internal fun Context.findStoredMessage(threadId: Long, body: String, date: Long): Message? {
        val before = ((date + MATCH_WINDOW_MS) / 1000).toInt() + 1
        return getMessages(threadId, dateFrom = before, includeScheduledMessages = false)
            .firstOrNull { it.body == body && abs(it.millis() - date) < MATCH_WINDOW_MS }
    }
}

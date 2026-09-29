package org.fossify.messages

import android.content.Context
import android.provider.Telephony
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getSpamThreads
import org.fossify.messages.extensions.getUnreadCountsByThread
import org.fossify.messages.extensions.getUnreadSpamCounts
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.removeOrphanSpamMarkers
import org.fossify.messages.helpers.BLOCK_REASON_KEYWORD
import org.fossify.messages.helpers.SpamBackfill
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import org.fossify.messages.models.SpamMessage
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Room fixtures only, with ids far above real Telephony ids. Nothing is written to Telephony. */
@RunWith(AndroidJUnit4::class)
class SpamChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private fun conversation(threadId: Long, messageCount: Int, archived: Boolean = false) = Conversation(
        threadId, "provider snippet", 500, false, "Spam fixture", "", false, "5550100777",
        isArchived = archived, messageCount = messageCount
    )

    private fun message(id: Long, threadId: Long, body: String, date: Int, isMMS: Boolean = false) = Message(
        id, body, Telephony.Sms.MESSAGE_TYPE_INBOX, -1, ArrayList(), date, false, threadId, isMMS, null,
        "5550100777", "", "", -1
    )

    private fun markSpam(id: Long, threadId: Long) =
        context.messagesDB.insertSpamMarker(SpamMessage(id = id, threadId = threadId, reason = BLOCK_REASON_KEYWORD))

    private fun inbox(threadId: Long) = context.conversationsDB.getNonArchived().singleOrNull { it.threadId == threadId }

    private fun withThread(threadId: Long, block: () -> Unit) {
        try {
            block()
        } finally {
            context.messagesDB.deleteThreadMessages(threadId)
            context.conversationsDB.deleteThreadId(threadId)
        }
    }

    @Test
    fun spamOnlyThreadIsHiddenUntilMarkerIsRemoved() {
        val threadId = Long.MAX_VALUE - 301
        val spamId = Long.MAX_VALUE - 3011
        withThread(threadId) {
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 1))
            context.messagesDB.insertOrUpdate(message(spamId, threadId, "win a prize", 500))
            check(inbox(threadId) != null) { "An unmarked thread must be visible" }

            markSpam(spamId, threadId)
            check(inbox(threadId) == null) { "A thread whose only message is spam must be hidden" }

            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 1))
            check(inbox(threadId) == null) { "A reconciler overwrite must not reveal a hidden thread" }
            check(context.conversationsDB.getAll().any { it.threadId == threadId }) {
                "The reconciler must still see hidden rows"
            }

            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 1, archived = true))
            check(context.conversationsDB.getAllArchived().none { it.threadId == threadId }) {
                "Hidden spam threads must not show in the archive either"
            }
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 1))

            context.messagesDB.deleteSpamMarker(spamId)
            check(inbox(threadId) != null) { "Removing the marker must reveal the thread" }
        }
    }

    @Test
    fun mixedThreadStaysVisibleWithLatestRealMessage() {
        val threadId = Long.MAX_VALUE - 302
        val realId = Long.MAX_VALUE - 3021
        val spamId = Long.MAX_VALUE - 3022
        withThread(threadId) {
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 2))
            context.messagesDB.insertOrUpdate(message(realId, threadId, "real", 100))
            context.messagesDB.insertOrUpdate(message(spamId, threadId, "spam", 500))
            markSpam(spamId, threadId)

            val row = checkNotNull(inbox(threadId)) { "A thread with a real message must stay visible" }
            check(row.snippet == "real") { "Snippet must skip spam, was ${row.snippet}" }
            check(row.date == 100) { "Date must skip spam, was ${row.date}" }
        }
    }

    @Test
    fun uncertainCountsKeepThreadVisible() {
        val threadId = Long.MAX_VALUE - 303
        val spamId = Long.MAX_VALUE - 3031
        withThread(threadId) {
            markSpam(spamId, threadId)
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 0))
            check(inbox(threadId) != null) { "An unfilled message count must keep the thread visible" }

            // Telephony counts MMS too, so an MMS in a spam SMS thread leaves the count above the markers.
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 2))
            check(inbox(threadId) != null) { "An unmarked message must keep the thread visible" }
        }
    }

    @Test
    fun spamMarkerDoesNotApplyToMmsWithSameId() {
        val threadId = Long.MAX_VALUE - 304
        val sharedId = Long.MAX_VALUE - 3041
        withThread(threadId) {
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 2))
            context.messagesDB.insertOrUpdate(message(sharedId, threadId, "picture", 500, isMMS = true))
            markSpam(sharedId, threadId)
            check(inbox(threadId)?.snippet == "picture") { "An MMS must not be treated as spam by an SMS marker" }
        }
    }

    @Test
    fun deletingThreadMessagesClearsMarkers() {
        val threadId = Long.MAX_VALUE - 305
        val spamId = Long.MAX_VALUE - 3051
        withThread(threadId) {
            context.conversationsDB.insertOrUpdate(conversation(threadId, messageCount = 1))
            markSpam(spamId, threadId)
            check(inbox(threadId) == null)

            context.messagesDB.deleteThreadMessages(threadId)
            check(inbox(threadId) != null) { "Deleting a thread's messages must remove its spam markers" }
        }
    }

    @Test
    fun threadReadLeavesSpamUnread() {
        val threadId = Long.MAX_VALUE - 306
        val realId = Long.MAX_VALUE - 3061
        val spamId = Long.MAX_VALUE - 3062
        withThread(threadId) {
            context.messagesDB.insertOrUpdate(message(realId, threadId, "real", 100))
            context.messagesDB.insertOrUpdate(message(spamId, threadId, "spam", 500))
            markSpam(spamId, threadId)

            context.messagesDB.markThreadRead(threadId)
            val read = context.messagesDB.getThreadMessages(threadId).associate { it.id to it.read }
            check(read[realId] == true) { "Real messages must be marked read" }
            check(read[spamId] == false) { "Spam must stay unread for the Spam screen badge" }
        }
    }

    // Reads Telephony only: an existing unread SMS gets a Room marker for the duration of the test.
    @Test
    fun unreadSpamMovesFromThreadCountToSpamCount() {
        val spamIds = context.messagesDB.getSpamIds().toSet()
        val sms = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ}=0 AND ${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_INBOX}", null, null
        )?.use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getLong(0) to cursor.getLong(1) else null }
                .firstOrNull { it.first !in spamIds }
        }
        assumeTrue("Needs an unread, unmarked SMS on the device", sms != null)
        val (id, threadId) = sms!!

        val before = context.getUnreadCountsByThread()[threadId] ?: 0
        val spamBefore = context.getUnreadSpamCounts()[threadId] ?: 0
        try {
            markSpam(id, threadId)
            check((context.getUnreadCountsByThread()[threadId] ?: 0) == before - 1) { "Spam must not count as unread in the inbox" }
            check((context.getUnreadSpamCounts()[threadId] ?: 0) == spamBefore + 1) { "Spam must count as unread spam" }
        } finally {
            context.messagesDB.deleteSpamMarker(id)
        }
    }

    // Reads Telephony only: an existing SMS gets a Room marker for the duration of the test.
    @Test
    fun spamScreenListsMarkedSmsAndSkipsOrphans() {
        val spamIds = context.messagesDB.getSpamIds().toSet()
        val sms = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY), null, null, null
        )?.use { cursor ->
            generateSequence { if (cursor.moveToNext()) Triple(cursor.getLong(0), cursor.getLong(1), cursor.getString(2)) else null }
                .firstOrNull { it.first !in spamIds && context.messagesDB.getThreadSpamIds(it.second).isEmpty() }
        }
        assumeTrue("Needs an SMS in a thread without spam", sms != null)
        val (id, threadId, body) = sms!!
        val orphanId = Long.MAX_VALUE - 3071
        try {
            markSpam(id, threadId)
            markSpam(orphanId, Long.MAX_VALUE - 307)
            val threads = context.getSpamThreads()
            val row = checkNotNull(threads.singleOrNull { it.threadId == threadId }) { "A marked SMS must be listed" }
            check(row.messageIds == listOf(id) && row.snippet == body.orEmpty()) { "Row must show only the marked SMS, was $row" }
            check(threads.none { orphanId in it.messageIds }) { "A marker without an SMS must not be listed" }
        } finally {
            context.messagesDB.deleteSpamMarker(id)
            context.messagesDB.deleteSpamMarker(orphanId)
        }
    }

    // Reads Telephony only.
    @Test
    fun backfillMatchesTheStoredSmsInsteadOfInsertingACopy() {
        val sms = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} != ''", null, "${Telephony.Sms.DATE} DESC"
        )?.use { if (it.moveToFirst()) listOf(it.getLong(0), it.getLong(1), it.getString(2), it.getLong(3)) else null }
        assumeTrue("Needs an SMS on the device", sms != null)
        val (id, threadId, body, date) = sms!!
        // The legacy copy's timestamp was taken a moment after the SMS was stored.
        val match = with(SpamBackfill) { context.findStoredMessage(threadId as Long, body as String, (date as Long) + 30_000) }
        check(match?.id == id && match.isMMS == false) { "Expected SMS $id, found ${match?.id}" }
    }

    // Reads Telephony only: an existing SMS gets a Room marker for the duration of the test.
    @Test
    fun orphanMarkersAreRemovedAndRealOnesKept() {
        val spamIds = context.messagesDB.getSpamIds().toSet()
        val sms = context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID), null, null, null)
            ?.use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getLong(0) to cursor.getLong(1) else null }.firstOrNull { it.first !in spamIds } }
        assumeTrue("Needs an unmarked SMS on the device", sms != null)
        val (id, threadId) = sms!!
        val orphanId = Long.MAX_VALUE - 3081
        try {
            markSpam(id, threadId)
            markSpam(orphanId, Long.MAX_VALUE - 308)
            context.removeOrphanSpamMarkers()
            val left = context.messagesDB.getSpamIds().toSet()
            check(orphanId !in left) { "A marker whose SMS is gone must be removed" }
            check(id in left && left.containsAll(spamIds)) { "Markers of existing SMS must stay" }
        } finally {
            context.messagesDB.deleteSpamMarker(id)
            context.messagesDB.deleteSpamMarker(orphanId)
        }
    }

    @Test
    fun migration12to13() {
        val name = "spam-migration-check.db"
        context.deleteDatabase(name)
        try {
            val schema = instrumentation.context.assets.open("org.fossify.messages.databases.MessagesDatabase/12.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
                db.execSQL(
                    "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count) " +
                        "VALUES (7, 'kept', 1, 1, 'Kept', '', 0, '5550100', 0, 0, 0, 0)"
                )
                db.execSQL("INSERT INTO blocked_messages (address, body, date, reason) VALUES ('5550100', 'legacy spam', 1, 1)")
                db.version = 12
            }
            val migrations = listOf("MIGRATION_12_13", "MIGRATION_13_14").map {
                MessagesDatabase::class.java.getDeclaredField(it).apply { isAccessible = true }.get(null) as Migration
            }
            val room = Room.databaseBuilder(context, MessagesDatabase::class.java, name)
                .addMigrations(*migrations.toTypedArray()).build()
            try {
                val db = room.openHelper.writableDatabase // Opens and validates against the actual Room entity schema.
                db.query("SELECT body FROM blocked_messages").use {
                    check(it.moveToFirst() && it.getString(0) == "legacy spam") { "Legacy spam must survive until the backfill moves it" }
                }
                val kept = room.ConversationsDao().getAll().single()
                check(kept.snippet == "kept" && kept.messageCount == 0)
                room.MessagesDao().insertSpamMarker(SpamMessage(id = 1, threadId = 7, reason = BLOCK_REASON_KEYWORD))
                check(room.ConversationsDao().getNonArchived().single().threadId == 7L) {
                    "Migrated rows have no message count yet and must stay visible"
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

package com.afsh.messages.helpers

import com.afsh.commons.models.SimpleContact
import com.afsh.messages.interfaces.ConversationsDao
import com.afsh.messages.interfaces.DraftsDao
import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Draft
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessagePreview
import com.afsh.messages.models.SpamMessage
import com.afsh.messages.models.ThreadUnreadCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class ConversationRepositoryTest {

    private class FakeConversationsDao : ConversationsDao by unsupported() {
        val rows = LinkedHashMap<Long, Conversation>()

        override fun insertOrUpdate(conversation: Conversation): Long {
            rows[conversation.threadId] = conversation
            return conversation.threadId
        }
        override fun getAll(): List<Conversation> = rows.values.toList()
        override fun getCount(): Int = rows.size
        override fun getConversationWithThreadId(threadId: Long): Conversation? = rows[threadId]
        override fun markRead(threadId: Long) = update(threadId) { it.copy(read = true, unreadCount = 0) }
        override fun markUnread(threadId: Long) = update(threadId) { it.copy(read = false) }
        override fun updateUnreadCount(threadId: Long, count: Int) =
            update(threadId) { it.copy(read = count == 0, unreadCount = count) }
        override fun getUnreadCounts(): List<ThreadUnreadCount> =
            rows.values.filter { !it.isArchived }.map { ThreadUnreadCount(it.threadId, it.unreadCount) }
        override fun moveToArchive(threadId: Long) = update(threadId) { it.copy(isArchived = true) }
        override fun unarchive(threadId: Long) = update(threadId) { it.copy(isArchived = false) }
        override fun deleteThreadId(threadId: Long) {
            rows.remove(threadId)
        }
        override fun updatePreview(threadId: Long, snippet: String?, date: Int?, allSpam: Boolean) =
            update(threadId) { it.copy(previewSnippet = snippet, previewDate = date, allSpam = allSpam) }

        private fun update(threadId: Long, change: (Conversation) -> Conversation) {
            rows[threadId]?.let { rows[threadId] = change(it) }
        }
    }

    /** Room's messages keyed like Room, by id and transport: SMS and MMS ids overlap. */
    private class FakeMessagesDao : MessagesDao by unsupported() {
        val messages = LinkedHashMap<Pair<Long, Boolean>, Message>()
        val spam = LinkedHashMap<Long, SpamMessage>()

        override fun getSpamIds(): List<Long> = spam.keys.toList()
        override fun getSpamMarkers(): List<SpamMessage> = spam.values.toList()
        override fun getThreadSpamIds(threadId: Long): List<Long> =
            spam.values.filter { it.threadId == threadId }.map { it.id }
        override fun deleteSpamMarker(id: Long) {
            spam.remove(id)
        }
        override fun insertOrUpdate(message: Message) {
            messages[message.id to message.isMMS] = message
        }
        override fun getThreadIdForMessage(id: Long, isMms: Boolean): Long? = messages[id to isMms]?.threadId
        override fun markRead(id: Long, isMMS: Boolean) {
            messages[id to isMMS]?.let { messages[id to isMMS] = it.copy(read = true) }
        }
        override fun markUnread(id: Long, isMMS: Boolean) {
            messages[id to isMMS]?.let { messages[id to isMMS] = it.copy(read = false) }
        }
        override fun markThreadRead(threadId: Long) {
            messages.replaceAll { _, m -> if (m.threadId == threadId) m.copy(read = true) else m }
        }
        override fun deleteThreadMessages(threadId: Long) {
            messages.values.removeAll { it.threadId == threadId }
        }
        override fun delete(id: Long, isMms: Boolean) {
            messages.remove(id to isMms)
        }
        override fun getScheduledThreadMessages(threadId: Long): List<Message> =
            messages.values.filter { it.threadId == threadId && it.isScheduled }
        override fun getThreadSpamCount(threadId: Long): Int = spam.values.count { it.threadId == threadId }
        override fun getLatestVisiblePreview(threadId: Long): MessagePreview? =
            messages.values.filter { it.threadId == threadId && !(!it.isMMS && it.id in spam) }
                .maxByOrNull { it.date }?.let { MessagePreview(it.body, it.date) }
        override fun getThreadLatestDate(threadId: Long): Int? =
            messages.values.filter { it.threadId == threadId && !it.isScheduled }.maxOfOrNull { it.date }
        override fun hasMessageForSnippet(threadId: Long, date: Int, snippet: String): Boolean {
            val wanted = snippet.filterNot { it in WHITESPACE }
            return messages.values.any {
                it.threadId == threadId && !it.isScheduled && it.date == date &&
                    (it.isMMS || it.body.filterNot { c -> c in WHITESPACE }.startsWith(wanted))
            }
        }
    }

    private companion object {
        /** What the DAO query strips before comparing: providers may turn a newline into a space. */
        const val WHITESPACE = " \n\r\t"
        /** Telephony.Sms.MESSAGE_TYPE_FAILED. */
        const val FAILED_TYPE = 5
    }

    private class FakeDraftsDao : DraftsDao by unsupported() {
        val drafts = ArrayList<Draft>()
        override fun getAll(): List<Draft> = drafts
    }

    /** Telephony in memory: SMS and MMS rows, the archived flag, and the threads it knows. */
    private class FakeTelephony : ConversationTelephony {
        class Row(val id: Long, val threadId: Long, val isMms: Boolean, var read: Boolean, val incoming: Boolean, val date: Long, val address: String = "")

        val rows = ArrayList<Row>()
        val threads = LinkedHashMap<Long, Conversation>()
        val archived = HashSet<Long>()
        var archiveAvailable = true
        var touched = 0
        var digest = 0 to 0
        var lastUnreadCounts: Map<Long, Int>? = null
        var lastDraftDates: Map<Long, Long>? = null
        var lastLimit: Int? = null
        var lastFailOnError: Boolean? = null
        /** Every read of the threads table fails: an error with failOnError, otherwise no rows. */
        var unreadableThreads = false
        /** Reading these threads fails: an error with failOnError, otherwise no rows, as the provider does. */
        val unreadable = HashSet<Long>()
        /** Runs once the threads are read: what other writers do while a pass is running. */
        var afterRead: () -> Unit = {}

        override fun unreadSms() = rows.filter { !it.isMms && !it.read && it.incoming }.map { it.id to it.threadId }
        override fun unreadMmsThreadIds() = rows.filter { it.isMms && !it.read && it.incoming }.map { it.threadId }
        override fun conversations(
            threadId: Long?,
            privateContacts: List<SimpleContact>,
            unreadCounts: Map<Long, Int>,
            draftDates: Map<Long, Long>,
            failOnError: Boolean,
            onProgress: ((done: Int, total: Int) -> Unit)?,
            limit: Int?,
        ): ArrayList<Conversation> {
            lastUnreadCounts = unreadCounts
            lastDraftDates = draftDates
            lastLimit = limit
            lastFailOnError = failOnError
            if (unreadableThreads || threadId in unreadable) {
                if (failOnError) throw IllegalStateException("provider") else return ArrayList()
            }
            val newestFirst = threads.values.filter { threadId == null || it.threadId == threadId }.sortedByDescending { it.date }
            val result = ArrayList(if (limit == null) newestFirst else newestFirst.take(limit))
            afterRead().also { afterRead = {} }
            return result
        }
        override fun threadDigest() = digest
        /** Telephony's thread per recipient set; asking for a new set creates an empty thread, as Android does. */
        val threadsByRecipients = HashMap<Set<String>, Long>()
        private var nextThreadId = 1_000L
        override fun threadIdFor(addresses: Set<String>): Long = threadsByRecipients.getOrPut(addresses) { nextThreadId++ }
        override fun smsRows(ids: Collection<Long>) = rows.filter { !it.isMms && it.id in ids }
            .map { TelephonySms(it.id, it.threadId, it.address, "body ${it.id}", it.date, it.read) }
        override fun existingSmsIds(ids: Collection<Long>): Set<Long> = rows.filter { !it.isMms && it.id in ids }.mapTo(HashSet()) { it.id }
        override fun smsThreadId(messageId: Long) = rows.firstOrNull { !it.isMms && it.id == messageId }?.threadId
        override fun deleteThread(threadId: Long) {
            rows.removeAll { it.threadId == threadId }
            threads.remove(threadId)
        }
        override fun setArchived(threadId: Long, archived: Boolean): Boolean {
            if (!archiveAvailable) return false
            if (archived) this.archived += threadId else this.archived -= threadId
            return true
        }
        override fun markMessageRead(id: Long, isMms: Boolean): Boolean {
            val row = rows.firstOrNull { it.id == id && it.isMms == isMms } ?: return false
            row.read = true
            return true
        }
        override fun markThreadRead(threadId: Long, skipSmsIds: Collection<Long>) {
            rows.filter { it.threadId == threadId && it.incoming && !(it.isMms.not() && it.id in skipSmsIds) }.forEach { it.read = true }
        }
        override fun newestIncoming(threadId: Long, skipSmsIds: Collection<Long>): Pair<Long, Boolean>? =
            rows.filter { it.threadId == threadId && it.incoming && !(!it.isMms && it.id in skipSmsIds) }
                .maxByOrNull { it.date }?.let { it.id to it.isMms }
        override fun markMessageUnread(id: Long, isMms: Boolean) {
            rows.first { it.id == id && it.isMms == isMms }.read = false
        }
        override fun touchThreads() {
            touched++
        }
    }

    private class FakeEffects : ConversationEffects {
        val refreshed = ArrayList<Long>()
        val events = ArrayList<MessagingEvent>()
        val manuallyUnread = HashMap<Long, Pair<Long, Boolean>>()
        val forgotten = ArrayList<Long>()
        val shortcuts = ArrayList<Conversation>()
        val errors = ArrayList<Exception>()
        val contacts = HashMap<String, Pair<String, String>>()
        val photos = HashMap<String, String>()
        var transactions = 0

        override fun refreshPreview(threadIds: Collection<Long>) {
            refreshed += threadIds
        }
        override fun publish(event: MessagingEvent) {
            events += event
        }
        override fun setManuallyUnread(threadId: Long, messageId: Long, isMms: Boolean) {
            manuallyUnread[threadId] = messageId to isMms
        }
        override fun clearManuallyUnread(threadId: Long) {
            manuallyUnread.remove(threadId)
        }
        override fun forgetThread(threadId: Long) {
            forgotten += threadId
        }
        override fun updateShortcut(conversation: Conversation) {
            shortcuts += conversation
        }
        override fun sender(address: String): Pair<String, String> = contacts[address] ?: (address to "")
        override fun photoUri(address: String): String = photos[address].orEmpty()
        override fun onError(e: Exception) {
            errors += e
        }
        override fun runInTransaction(block: () -> Unit) {
            transactions++
            block()
        }
    }

    private val conversations = FakeConversationsDao()
    private val messages = FakeMessagesDao()
    private val drafts = FakeDraftsDao()
    private val telephony = FakeTelephony()
    private val effects = FakeEffects()
    private var now = 1_000_000_000L
    private val repository = ConversationRepository(
        conversations = conversations,
        messages = messages,
        drafts = drafts,
        telephony = telephony,
        effects = effects,
        clock = { now },
    )

    private fun conversation(threadId: Long, read: Boolean = true, unread: Int = 0, title: String = "T$threadId", date: Int = 100) =
        Conversation(
            threadId = threadId, snippet = "s", date = date, read = read, title = title, photoUri = "",
            isGroupConversation = false, phoneNumber = "+1$threadId", unreadCount = unread,
        )

    private fun sms(id: Long, threadId: Long, read: Boolean = false, incoming: Boolean = true, date: Long = id, address: String = "") =
        FakeTelephony.Row(id, threadId, isMms = false, read = read, incoming = incoming, date = date, address = address).also { telephony.rows += it }

    private fun mms(id: Long, threadId: Long, read: Boolean = false, date: Long = id) =
        FakeTelephony.Row(id, threadId, isMms = true, read = read, incoming = true, date = date).also { telephony.rows += it }

    private fun spam(id: Long, threadId: Long, reason: Int = 1) {
        messages.spam[id] = SpamMessage(id, threadId, reason, markedTS = 0)
    }

    private fun message(
        id: Long,
        threadId: Long,
        scheduled: Boolean = false,
        date: Int = 0,
        read: Boolean = false,
        body: String = "",
        isMms: Boolean = false,
        participants: List<String> = emptyList(),
    ) = Message(
        id = id, body = body, type = 1, status = 0, participants = participants.mapTo(ArrayList()) { contact(it, it) }, date = date, read = read,
        threadId = threadId, isMMS = isMms, attachment = null, senderPhoneNumber = "", senderName = "",
        senderPhotoUri = "", subscriptionId = 0, isScheduled = scheduled,
    ).also { messages.messages[id to isMms] = it }

    // region Unread counts

    @Test
    fun `unread counts add unread SMS and MMS per thread and leave spam out`() {
        sms(1, threadId = 10)
        sms(2, threadId = 10)
        sms(3, threadId = 10, read = true)
        sms(4, threadId = 20)
        mms(5, threadId = 20)
        sms(6, threadId = 30)
        spam(6, threadId = 30)

        assertEquals(mapOf(10L to 2, 20L to 2), repository.unreadCountsByThread())
    }

    @Test
    fun `unread spam is counted only by the spam counts`() {
        sms(1, threadId = 10)
        sms(2, threadId = 10)
        sms(3, threadId = 20)
        spam(2, threadId = 10)
        spam(9, threadId = 10)

        assertEquals(mapOf(10L to 1), repository.unreadSpamCounts())
    }

    @Test
    fun `syncing unread counts rewrites only the rows that drifted, in one transaction`() {
        conversations.insertOrUpdate(conversation(10, read = true))
        conversations.insertOrUpdate(conversation(20, read = false, unread = 5))
        conversations.insertOrUpdate(conversation(30, read = false, unread = 1))
        sms(1, threadId = 20)
        sms(2, threadId = 30)

        repository.syncUnreadCounts()

        assertEquals(1, effects.transactions)
        assertEquals(1, conversations.rows.getValue(20).unreadCount)
        assertEquals(1, conversations.rows.getValue(30).unreadCount)
        assertTrue(conversations.rows.getValue(10).read)
    }

    @Test
    fun `syncing unread counts for some threads leaves the others alone`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 3))
        conversations.insertOrUpdate(conversation(20, read = false, unread = 3))

        repository.syncUnreadCounts(listOf(20L))

        assertEquals(3, conversations.rows.getValue(10).unreadCount)
        assertEquals(0, conversations.rows.getValue(20).unreadCount)
        assertTrue(conversations.rows.getValue(20).read)
    }

    @Test
    fun `nothing is written when no count drifted`() {
        conversations.insertOrUpdate(conversation(10, read = true))

        repository.syncUnreadCounts()

        assertEquals(0, effects.transactions)
    }

    // endregion

    // region Reading conversations

    @Test
    fun `conversations from Telephony are given unread counts and draft dates`() {
        telephony.threads[10] = conversation(10)
        sms(1, threadId = 10)
        drafts.drafts += Draft(threadId = 10, body = "hi", date = 5_000L)

        val result = repository.conversations()

        assertEquals(listOf(10L), result.map { it.threadId })
        assertEquals(mapOf(10L to 1), telephony.lastUnreadCounts)
        assertEquals(mapOf(10L to 5_000L), telephony.lastDraftDates)
    }

    @Test
    fun `one conversation is read by thread id`() {
        telephony.threads[10] = conversation(10)
        telephony.threads[20] = conversation(20)

        assertEquals(20L, repository.conversation(20)?.threadId)
        assertNull(repository.conversation(30))
    }

    @Test
    fun `a full reconcile is needed when Room is empty or its thread count drifted from Telephony`() {
        assertTrue(repository.needsFullReconcile())

        conversations.insertOrUpdate(conversation(10, date = 100))
        telephony.digest = 1 to 100
        assertFalse(repository.needsFullReconcile())

        // A newer message in a thread Room has is the recent window's, not a full reconcile's.
        telephony.digest = 1 to 101
        assertFalse(repository.needsFullReconcile())

        telephony.digest = 2 to 101
        assertTrue(repository.needsFullReconcile())

        telephony.digest = -1 to -1
        assertTrue(repository.needsFullReconcile())
    }

    // region Provider passes (InboxReconciler's policy, run against this file's fakes)

    /** A full reconcile: Room is read, then Telephony, then the writes. */
    private fun fullPass(forced: Boolean = false) {
        val allCached = repository.cachedConversations()
        InboxReconciler.storeConversations(repository, allCached, repository.conversations(failOnError = true), forced)
    }

    @Test
    fun `a full pass inserts the threads Room lacks and drops those Telephony lost, in one transaction`() {
        conversations.insertOrUpdate(conversation(10))
        conversations.insertOrUpdate(conversation(20))
        telephony.threads[10] = conversation(10)
        telephony.threads[30] = conversation(30)

        fullPass()

        assertEquals(setOf(10L, 30L), conversations.rows.keys)
        assertEquals(1, effects.transactions)
    }

    @Test
    fun `a full pass rewrites a drifted row and keeps its custom title`() {
        conversations.insertOrUpdate(conversation(10, title = "Mom").copy(usesCustomTitle = true, snippet = "old"))
        telephony.threads[10] = conversation(10, date = 200).copy(snippet = "new")

        fullPass()

        val row = conversations.rows.getValue(10)
        assertEquals("new", row.snippet)
        assertEquals(200, row.date)
        assertEquals("Mom", row.title)
    }

    @Test
    fun `a full pass leaves archived rows alone`() {
        conversations.insertOrUpdate(conversation(10).copy(isArchived = true, snippet = "old"))
        telephony.threads[10] = conversation(10, date = 200).copy(snippet = "new")

        fullPass()

        assertEquals("old", conversations.rows.getValue(10).snippet)
    }

    /** Temporary scheduled thread [threadId] for [participants], with one message due in an hour. */
    private fun temporaryThread(threadId: Long, vararg participants: String): Conversation {
        val row = conversation(threadId, date = (now / 1000).toInt() + 3600).copy(
            isScheduled = true, snippet = "later", phoneNumber = participants.first(), isGroupConversation = participants.size > 1,
        )
        conversations.insertOrUpdate(row)
        message(threadId, threadId = threadId, scheduled = true, date = row.date, body = "later", participants = participants.toList())
        return row
    }

    @Test
    fun `a temporary thread folds into its real thread, which stays a plain conversation with its own date`() {
        // Room already matches Telephony, so only the fold writes the real thread.
        conversations.insertOrUpdate(conversation(10, date = 150).copy(snippet = "sent"))
        temporaryThread(77, "+110")
        telephony.threads[10] = conversation(10, date = 150).copy(snippet = "sent")
        telephony.threadsByRecipients[setOf("+110")] = 10

        fullPass()

        val real = conversations.rows.getValue(10)
        assertFalse("a real thread is never a scheduled one", real.isScheduled)
        assertEquals(150, real.date)
        assertEquals("sent", real.snippet)
        assertFalse(77L in conversations.rows)
        assertEquals(10L, messages.messages.getValue(77L to false).threadId)
    }

    @Test
    fun `a folded real thread is left alone by the next pass`() {
        temporaryThread(77, "+110")
        telephony.threads[10] = conversation(10, date = 150)
        telephony.threadsByRecipients[setOf("+110")] = 10
        fullPass()
        effects.refreshed.clear()

        fullPass()

        assertTrue(effects.refreshed.isEmpty())
    }

    @Test
    fun `a temporary group thread does not fold into a one-to-one thread with its first participant`() {
        temporaryThread(77, "+110", "+120")
        telephony.threads[10] = conversation(10, date = 150)
        telephony.threadsByRecipients[setOf("+110")] = 10

        fullPass()

        assertTrue(77L in conversations.rows)
        assertFalse(conversations.rows.getValue(10).isScheduled)
        assertEquals(77L, messages.messages.getValue(77L to false).threadId)
    }

    @Test
    fun `a temporary thread keeps a custom title when its real thread has none`() {
        conversations.insertOrUpdate(temporaryThread(77, "+110").copy(title = "Mom", usesCustomTitle = true))
        telephony.threads[10] = conversation(10, date = 150)
        telephony.threadsByRecipients[setOf("+110")] = 10

        fullPass()

        val real = conversations.rows.getValue(10)
        assertEquals("Mom", real.title)
        assertTrue(real.usesCustomTitle)
    }

    // A pass reads Room, then Telephony, then writes. Whatever lands in Room meanwhile is newer than its read.

    @Test
    fun `a full pass leaves a row written while it ran to that write`() {
        conversations.insertOrUpdate(conversation(10, date = 100).copy(snippet = "old", messageCount = 5))
        telephony.threads[10] = conversation(10, date = 150).copy(snippet = "mid", messageCount = 6)
        val incoming = conversation(10, read = false, unread = 1, date = 200).copy(snippet = "new", messageCount = 7)
        telephony.afterRead = { conversations.insertOrUpdate(incoming) }

        fullPass()

        assertEquals(incoming, conversations.rows.getValue(10))
    }

    @Test
    fun `a full pass does not undo a mark-read made while it ran`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 2))
        // A renamed contact: the pass has something to write.
        telephony.threads[10] = conversation(10, read = false, unread = 2, title = "Ali")
        telephony.afterRead = { repository.markThreadRead(10) }

        fullPass()

        assertTrue(conversations.rows.getValue(10).read)
        assertEquals(0, conversations.rows.getValue(10).unreadCount)
    }

    @Test
    fun `a full pass does not bring back a thread deleted while it ran`() {
        conversations.insertOrUpdate(conversation(10))
        telephony.threads[10] = conversation(10, date = 200)
        telephony.afterRead = { repository.delete(10) }

        fullPass()

        assertFalse(10L in conversations.rows)
    }

    @Test
    fun `a forced pass leaves a row written while it ran to that write`() {
        conversations.insertOrUpdate(conversation(10, date = 100))
        telephony.threads[10] = conversation(10, date = 150).copy(snippet = "mid")
        val incoming = conversation(10, read = false, unread = 1, date = 200).copy(snippet = "new")
        telephony.afterRead = { conversations.insertOrUpdate(incoming) }

        fullPass(forced = true)

        assertEquals(incoming, conversations.rows.getValue(10))
    }

    @Test
    fun `a full pass leaves a thread a receiver created while it ran to the receiver`() {
        telephony.threads[10] = conversation(10, date = 150).copy(snippet = "mid")
        val incoming = conversation(10, read = false, unread = 1, date = 200).copy(snippet = "new")
        telephony.afterRead = { conversations.insertOrUpdate(incoming) }

        fullPass()

        assertEquals(incoming, conversations.rows.getValue(10))
    }

    @Test
    fun `a full pass keeps a row a receiver wrote while it ran, though Telephony's read lacked the thread`() {
        // Thread 10 had no messages left when the pass read Telephony; a new one landed in it meanwhile.
        conversations.insertOrUpdate(conversation(10, date = 100))
        val incoming = conversation(10, read = false, unread = 1, date = 200).copy(snippet = "new")
        telephony.afterRead = { conversations.insertOrUpdate(incoming) }

        fullPass()

        assertEquals(incoming, conversations.rows[10L])
    }

    @Test
    fun `a full pass writes no row whose merge with Room changes nothing`() {
        // Room's preview columns and a custom title are app-owned and absent from Telephony's row.
        conversations.insertOrUpdate(conversation(10, title = "Mom").copy(usesCustomTitle = true, previewSnippet = "visible", previewDate = 90))
        telephony.threads[10] = conversation(10)

        fullPass()

        assertTrue(effects.refreshed.isEmpty())
    }

    @Test
    fun `a full pass keeps a temporary scheduled thread that has no real thread yet`() {
        conversations.insertOrUpdate(conversation(77).copy(isScheduled = true))
        message(1, threadId = 77, scheduled = true, date = (now / 1000).toInt() + 3600)

        fullPass()

        assertTrue(77L in conversations.rows)
    }

    // endregion

    // region Recent window (InboxReconciler's policy, run against this file's fakes)

    @Test
    fun `conversations pass their limit to Telephony`() {
        telephony.threads[10] = conversation(10, date = 300)
        telephony.threads[11] = conversation(11, date = 200)
        telephony.threads[12] = conversation(12, date = 100)

        assertEquals(listOf(10L, 11L), repository.conversations(limit = 2).map { it.threadId })
        assertEquals(2, telephony.lastLimit)

        repository.conversations()
        assertNull(telephony.lastLimit)
    }

    @Test
    fun `the recent window reads only the newest threads and writes what drifted, in one transaction`() {
        conversations.insertOrUpdate(conversation(10, date = 300).copy(snippet = "old"))
        conversations.insertOrUpdate(conversation(11, date = 200))
        conversations.insertOrUpdate(conversation(12, date = 150, read = true, unread = 0))
        conversations.insertOrUpdate(conversation(13, date = 50))
        telephony.threads[10] = conversation(10, date = 300).copy(snippet = "new")
        telephony.threads[11] = conversation(11, date = 200)
        telephony.threads[12] = conversation(12, date = 160, read = false, unread = 2)
        telephony.threads[14] = conversation(14, date = 120)
        // Outside the window: a drifted old thread is left for the full reconcile.
        telephony.threads[13] = conversation(13, date = 50).copy(snippet = "drifted")

        val written = InboxReconciler.syncRecentWindow(repository, limit = 4)

        assertEquals(3, written)
        assertEquals(4, telephony.lastLimit)
        assertEquals(true, telephony.lastFailOnError)
        assertEquals(1, effects.transactions)
        assertEquals("new", conversations.rows.getValue(10).snippet)
        assertEquals(160, conversations.rows.getValue(12).date)
        assertEquals(2, conversations.rows.getValue(12).unreadCount)
        assertFalse(conversations.rows.getValue(12).read)
        assertEquals(conversation(14, date = 120), conversations.rows.getValue(14))
        assertEquals("s", conversations.rows.getValue(13).snippet)
        assertEquals(listOf(10L, 12L, 14L), effects.refreshed.sorted())
    }

    @Test
    fun `the recent window writes nothing when Room already matches`() {
        // Room's preview columns are app-owned and absent from Telephony's row; they are no drift.
        conversations.insertOrUpdate(conversation(10).copy(previewSnippet = "visible", previewDate = 90))
        telephony.threads[10] = conversation(10)

        assertEquals(0, InboxReconciler.syncRecentWindow(repository, limit = 20))
        assertEquals(0, effects.transactions)
        assertTrue(effects.refreshed.isEmpty())
        assertEquals("visible", conversations.rows.getValue(10).previewSnippet)
    }

    @Test
    fun `the recent window keeps app-owned metadata of the rows it updates`() {
        conversations.insertOrUpdate(conversation(10, title = "Mom").copy(usesCustomTitle = true, snippet = "old"))
        telephony.threads[10] = conversation(10).copy(snippet = "new")

        assertEquals(1, InboxReconciler.syncRecentWindow(repository, limit = 20))
        val row = conversations.rows.getValue(10)
        assertEquals("new", row.snippet)
        assertEquals("Mom", row.title)
        assertTrue(row.usesCustomTitle)
    }

    @Test
    fun `the recent window leaves archived rows alone, as the full reconcile does`() {
        conversations.insertOrUpdate(conversation(10).copy(isArchived = true, snippet = "old"))
        telephony.threads[10] = conversation(10).copy(snippet = "new")

        assertEquals(0, InboxReconciler.syncRecentWindow(repository, limit = 20))
        assertEquals("old", conversations.rows.getValue(10).snippet)
    }

    @Test
    fun `the recent window leaves a row written while it ran to that write`() {
        conversations.insertOrUpdate(conversation(10, date = 100).copy(snippet = "old", messageCount = 5))
        telephony.threads[10] = conversation(10, date = 150).copy(snippet = "mid", messageCount = 6)
        val incoming = conversation(10, read = false, unread = 1, date = 200).copy(snippet = "new", messageCount = 7)
        telephony.afterRead = { conversations.insertOrUpdate(incoming) }

        assertEquals(0, InboxReconciler.syncRecentWindow(repository, limit = 20))
        assertEquals(incoming, conversations.rows.getValue(10))
    }

    @Test
    fun `the recent window does not bring back a thread deleted while it ran`() {
        conversations.insertOrUpdate(conversation(10))
        telephony.threads[10] = conversation(10, date = 200)
        telephony.afterRead = { repository.delete(10) }

        InboxReconciler.syncRecentWindow(repository, limit = 20)

        assertFalse(10L in conversations.rows)
    }

    @Test(expected = IllegalStateException::class)
    fun `a recent window that cannot read Telephony fails`() {
        telephony.unreadableThreads = true
        InboxReconciler.syncRecentWindow(repository, limit = 20)
    }

    // endregion

    @Test
    fun `an SMS thread id comes from Room first, then Telephony`() {
        message(5, threadId = 10)
        sms(6, threadId = 20, read = true)

        assertEquals(10L, repository.smsThreadId(5))
        assertEquals(20L, repository.smsThreadId(6))
        assertNull(repository.smsThreadId(0))
        assertNull(repository.smsThreadId(7))
    }

    @Test
    fun `an SMS thread id is never taken from an MMS that shares its id`() {
        message(5, threadId = 30, isMms = true)
        sms(5, threadId = 20, read = true)

        assertEquals(20L, repository.smsThreadId(5))
    }

    // endregion

    // region Writing conversations

    @Test
    fun `insert keeps a custom title from the cached row and refreshes the preview`() {
        conversations.insertOrUpdate(conversation(10, title = "Mum").copy(usesCustomTitle = true))

        repository.insertOrUpdate(conversation(10, title = "+110"))

        assertEquals("Mum", conversations.rows.getValue(10).title)
        assertEquals(listOf(10L), effects.refreshed)
    }

    @Test
    fun `rename stores a custom title and updates the shortcut`() {
        conversations.insertOrUpdate(conversation(10))

        val renamed = repository.rename(conversation(10), "Work")

        assertEquals("Work", renamed.title)
        assertTrue(renamed.usesCustomTitle)
        assertEquals(renamed, conversations.rows.getValue(10))
        assertEquals(listOf(renamed), effects.shortcuts)
    }

    @Test
    fun `archiving writes Telephony and Room`() {
        conversations.insertOrUpdate(conversation(10))

        repository.setArchived(10, archived = true)
        assertTrue(10L in telephony.archived)
        assertTrue(conversations.rows.getValue(10).isArchived)

        repository.setArchived(10, archived = false)
        assertFalse(10L in telephony.archived)
        assertFalse(conversations.rows.getValue(10).isArchived)
    }

    @Test
    fun `archiving leaves Room alone when Telephony has no archive`() {
        conversations.insertOrUpdate(conversation(10))
        telephony.archiveAvailable = false

        repository.setArchived(10, archived = true)

        assertFalse(conversations.rows.getValue(10).isArchived)
    }

    @Test
    fun `deleting a conversation removes it from Telephony, Room and the thread extras`() {
        conversations.insertOrUpdate(conversation(10))
        conversations.insertOrUpdate(conversation(20))
        telephony.threads[10] = conversation(10)
        sms(1, threadId = 10)
        message(1, threadId = 10)
        message(2, threadId = 20)

        repository.delete(10)

        assertEquals(setOf(20L), conversations.rows.keys)
        assertEquals(setOf(2L to false), messages.messages.keys)
        assertTrue(telephony.rows.isEmpty())
        assertEquals(listOf(10L), effects.forgotten)
    }

    @Test
    fun `refreshing threads touches Telephony then stores each fresh row`() {
        telephony.threads[10] = conversation(10).copy(snippet = "new")
        telephony.threads[20] = conversation(20).copy(snippet = "other")

        repository.refreshFromTelephony(listOf(10L, 20L))

        assertEquals(1, telephony.touched)
        assertEquals("new", conversations.rows.getValue(10).snippet)
        assertEquals("other", conversations.rows.getValue(20).snippet)
    }

    @Test
    fun `refreshing drops the row of a thread Telephony no longer has and goes on`() {
        conversations.insertOrUpdate(conversation(10))
        telephony.threads[20] = conversation(20).copy(snippet = "fresh")

        repository.refreshFromTelephony(listOf(10L, 20L))

        assertEquals(listOf(20L), conversations.rows.keys.toList())
        assertEquals("fresh", conversations.rows.getValue(20).snippet)
    }

    @Test
    fun `refreshing keeps a temporary scheduled thread Telephony does not have`() {
        conversations.insertOrUpdate(conversation(10).copy(isScheduled = true))

        repository.refreshFromTelephony(listOf(10L))

        assertTrue(10L in conversations.rows)
    }

    @Test
    fun `refreshing keeps the row of a thread it cannot read and goes on`() {
        conversations.insertOrUpdate(conversation(10).copy(snippet = "old"))
        telephony.threads[10] = conversation(10).copy(snippet = "new")
        telephony.unreadable += 10L
        telephony.threads[20] = conversation(20)

        repository.refreshFromTelephony(listOf(10L, 20L))

        assertEquals("old", conversations.rows.getValue(10).snippet)
        assertTrue(20L in conversations.rows)
    }

    @Test
    fun `an outgoing message bumps the row to now and marks it read`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 2, date = 5))

        repository.bumpAfterOutgoing(10, "sent")

        val row = conversations.rows.getValue(10)
        assertEquals("sent", row.snippet)
        assertEquals((now / 1000).toInt(), row.date)
        assertTrue(row.read)
        assertEquals(0, row.unreadCount)
        assertEquals(listOf(10L), effects.refreshed)
    }

    @Test
    fun `an outgoing message to a thread Room lacks takes the row from Telephony`() {
        telephony.threads[10] = conversation(10)

        repository.bumpAfterOutgoing(10, "sent")

        assertEquals("sent", conversations.rows.getValue(10).snippet)
    }

    @Test
    fun `a scheduled message bumps the row's snippet and date, not its read state`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 2, date = 5))

        repository.bumpAfterScheduling(10, "later")
        repository.bumpAfterScheduling(11, "nowhere")

        val row = conversations.rows.getValue(10)
        assertEquals("later", row.snippet)
        assertEquals((now / 1000).toInt(), row.date)
        assertFalse(row.read)
        assertEquals(2, row.unreadCount)
        assertNull(conversations.rows[11])
    }

    @Test
    fun `an edited scheduled message replaces only the snippet it set`() {
        conversations.insertOrUpdate(conversation(10).copy(snippet = "old"))
        conversations.insertOrUpdate(conversation(11).copy(snippet = "newer"))

        repository.replaceSnippet(10, old = "old", new = "new")
        repository.replaceSnippet(11, old = "old", new = "new")

        assertEquals("new", conversations.rows.getValue(10).snippet)
        assertEquals("newer", conversations.rows.getValue(11).snippet)
    }

    // endregion

    // region Read state

    @Test
    fun `marking a thread read leaves spam unread and clears the manual unread mark`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 2))
        sms(1, threadId = 10)
        sms(2, threadId = 10)
        spam(2, threadId = 10)
        mms(3, threadId = 10)
        message(1, threadId = 10)
        effects.manuallyUnread[10] = 1L to false

        repository.markThreadRead(10)

        assertEquals(listOf(false, true, false), telephony.rows.map { !it.read })
        assertTrue(conversations.rows.getValue(10).read)
        assertTrue(messages.messages.getValue(1L to false).read)
        assertNull(effects.manuallyUnread[10])
        assertEquals(listOf<MessagingEvent>(MessagingEvent.ReadStateChanged(10, read = true, unreadCount = 0)), effects.events)
    }

    @Test
    fun `marking a thread unread marks only its newest incoming message`() {
        conversations.insertOrUpdate(conversation(10))
        sms(1, threadId = 10, read = true, date = 1_000)
        mms(2, threadId = 10, read = true, date = 3_000)
        sms(3, threadId = 10, read = true, incoming = false, date = 5_000)
        message(2, threadId = 10, read = true)

        repository.markThreadUnread(10)

        assertEquals(listOf(true, false, true), telephony.rows.map { it.read })
        assertEquals(2L to true, effects.manuallyUnread[10])
        assertEquals(1, conversations.rows.getValue(10).unreadCount)
        assertFalse(conversations.rows.getValue(10).read)
        assertEquals(listOf<MessagingEvent>(MessagingEvent.ReadStateChanged(10, read = false, unreadCount = 1)), effects.events)
    }

    @Test
    fun `marking unread a thread with only spam still shows it unread`() {
        conversations.insertOrUpdate(conversation(10))
        sms(1, threadId = 10, read = true)
        spam(1, threadId = 10)

        repository.markThreadUnread(10)

        assertTrue(telephony.rows.single().read)
        assertFalse(conversations.rows.getValue(10).read)
        assertNull(effects.manuallyUnread[10])
        assertEquals(listOf<MessagingEvent>(MessagingEvent.ReadStateChanged(10, read = false, unreadCount = null)), effects.events)
    }

    @Test
    fun `marking visible messages read reports the ones done and the count left`() {
        conversations.insertOrUpdate(conversation(10, read = false, unread = 3))
        sms(1, threadId = 10)
        sms(2, threadId = 10)
        sms(3, threadId = 10)
        val visible = listOf(message(1, threadId = 10), message(2, threadId = 10), message(9, threadId = 20))

        val done = repository.markVisibleMessagesRead(10, visible)

        assertEquals(setOf(visible[0].getStableId(), visible[1].getStableId()), done)
        assertEquals(1, conversations.rows.getValue(10).unreadCount)
        assertEquals(listOf<MessagingEvent>(MessagingEvent.ReadStateChanged(10, read = false, unreadCount = 1)), effects.events)
    }

    @Test
    fun `marking a message read that Telephony lacks changes nothing in Room`() {
        message(1, threadId = 10)

        assertFalse(repository.markMessageRead(1, isMms = false))
        assertFalse(messages.messages.getValue(1L to false).read)
    }

    // endregion

    // region Spam

    @Test
    fun `spam threads group spam SMS by thread, newest first, with the cached title`() {
        conversations.insertOrUpdate(conversation(10, title = "Bank"))
        sms(1, threadId = 10, date = 100, read = true, address = "+110")
        sms(2, threadId = 10, date = 300, address = "+110")
        sms(3, threadId = 20, date = 200, address = "+220")
        sms(4, threadId = 20, date = 50, address = "+220")
        spam(1, threadId = 10)
        spam(2, threadId = 10, reason = 2)
        spam(3, threadId = 20)
        effects.contacts["+220"] = "Shop" to "photo"

        val threads = repository.spamThreads()

        assertEquals(listOf(10L, 20L), threads.map { it.threadId })
        assertEquals(listOf("Bank", "Shop"), threads.map { it.title })
        assertEquals("photo", threads[1].photoUri)
        assertEquals(listOf(2, 1), threads.map { it.count })
        assertEquals(listOf(1, 1), threads.map { it.unreadCount })
        assertEquals(2, threads[0].reason)
        assertEquals(listOf(2L, 1L), threads[0].messageIds)
    }

    @Test
    fun `orphan spam markers are removed and their threads refreshed`() {
        sms(1, threadId = 10)
        spam(1, threadId = 10)
        spam(2, threadId = 20)

        repository.removeOrphanSpamMarkers()

        assertEquals(setOf(1L), messages.spam.keys)
        assertEquals(listOf(20L), effects.refreshed)
    }

    // endregion

    // region Scheduled conversations

    @Test
    fun `a scheduled message gets a temporary conversation that keeps a custom title`() {
        val scheduled = message(5, threadId = 77, scheduled = true, date = 900, body = "later")
            .copy(participants = arrayListOf(SimpleContact(0, 0, "Ali", "", arrayListOf(com.afsh.commons.models.PhoneNumber("+1", 0, "", "+1")), ArrayList(), ArrayList())))
        effects.photos["+1"] = "p"

        repository.createScheduledConversation(scheduled, 77, cached = conversation(77, title = "Mum").copy(usesCustomTitle = true))

        val row = conversations.rows.getValue(77)
        assertEquals("Mum", row.title)
        assertTrue(row.usesCustomTitle)
        assertTrue(row.isScheduled)
        assertEquals("later", row.snippet)
        assertEquals("p", row.photoUri)
        assertEquals(900, row.date)
    }

    @Test
    fun `expired scheduled messages go, and an emptied temporary thread goes with them`() {
        conversations.insertOrUpdate(conversation(77).copy(isScheduled = true))
        val past = ((now - 120_000) / 1000).toInt()
        message(5, threadId = 77, scheduled = true, date = past)

        repository.clearExpiredScheduledMessages(77)

        assertTrue(messages.messages.isEmpty())
        assertTrue(conversations.rows.isEmpty())
    }

    @Test
    fun `a scheduled message whose send failed stays past its time, and so does its temporary thread`() {
        conversations.insertOrUpdate(conversation(77).copy(isScheduled = true))
        val past = ((now - 120_000) / 1000).toInt()
        messages.messages[5L to false] = message(5, threadId = 77, scheduled = true, date = past).copy(type = FAILED_TYPE)

        repository.clearExpiredScheduledMessages(77)

        assertEquals(setOf(5L to false), messages.messages.keys)
        assertEquals(setOf(77L), conversations.rows.keys)
    }

    @Test
    fun `a temporary thread with a future scheduled message stays`() {
        conversations.insertOrUpdate(conversation(77).copy(isScheduled = true))
        val future = ((now + 600_000) / 1000).toInt()
        message(5, threadId = 77, scheduled = true, date = future)

        repository.clearExpiredScheduledMessages(77)

        assertEquals(setOf(5L to false), messages.messages.keys)
        assertEquals(setOf(77L), conversations.rows.keys)
    }

    // endregion

    // region Preview

    @Test
    fun `the preview shows the latest message that is not spam, dated only when the thread has spam`() {
        conversations.insertOrUpdate(conversation(8, date = 20).copy(messageCount = 3, snippet = "buy now"))
        message(1, threadId = 8, date = 10, body = "hello")
        message(2, threadId = 8, date = 20, body = "buy now")
        spam(2, threadId = 8)

        repository.refreshPreview(8)

        val row = conversations.rows.getValue(8)
        assertEquals("hello", row.previewSnippet)
        assertEquals(10, row.previewDate)
        assertFalse(row.allSpam)
    }

    @Test
    fun `a thread of only spam is all spam, and one without spam keeps its own date`() {
        conversations.insertOrUpdate(conversation(8, date = 10).copy(messageCount = 1, snippet = "buy now"))
        message(1, threadId = 8, date = 10, body = "buy now")
        spam(1, threadId = 8)
        conversations.insertOrUpdate(conversation(9, date = 30).copy(messageCount = 1, snippet = "hi"))
        message(3, threadId = 9, date = 30, body = "hi")

        repository.refreshPreview(8)
        repository.refreshPreview(9)

        assertTrue(conversations.rows.getValue(8).allSpam)
        assertNull(conversations.rows.getValue(8).previewSnippet)
        assertEquals("hi", conversations.rows.getValue(9).previewSnippet)
        assertNull(conversations.rows.getValue(9).previewDate)
    }

    @Test
    fun `a thread date newer than every cached message keeps the conversation's own snippet`() {
        conversations.insertOrUpdate(conversation(8, date = 50).copy(messageCount = 3, previewSnippet = "old"))
        message(1, threadId = 8, date = 10, body = "hello")
        message(2, threadId = 8, date = 20, body = "buy now")
        spam(2, threadId = 8)

        repository.refreshPreview(8)

        val row = conversations.rows.getValue(8)
        assertNull(row.previewSnippet)
        assertNull(row.previewDate)
    }

    @Test
    fun `a newer cached spam message does not make Room look stale`() {
        conversations.insertOrUpdate(conversation(8, date = 20).copy(messageCount = 2, snippet = "buy now"))
        message(1, threadId = 8, date = 10, body = "hello")
        message(2, threadId = 8, date = 20, body = "buy now")
        spam(2, threadId = 8)

        repository.refreshPreview(8)

        assertEquals("hello", conversations.rows.getValue(8).previewSnippet)
    }

    @Test
    fun `a same-second Telephony message Room has not cached keeps the conversation's own snippet`() {
        conversations.insertOrUpdate(conversation(8, date = 20).copy(messageCount = 2, snippet = "reply", previewSnippet = "old"))
        message(1, threadId = 8, date = 20, body = "sent")

        repository.refreshPreview(8)

        assertNull(conversations.rows.getValue(8).previewSnippet)
    }

    @Test
    fun `a truncated Telephony snippet still names the cached message`() {
        conversations.insertOrUpdate(conversation(8, date = 20).copy(messageCount = 2, snippet = "a long"))
        message(1, threadId = 8, date = 10, body = "hello")
        message(2, threadId = 8, date = 20, body = "a long message")
        spam(2, threadId = 8)

        repository.refreshPreview(8)

        assertEquals("hello", conversations.rows.getValue(8).previewSnippet)
    }

    @Test
    fun `Room holds the newest message only when it has the one Telephony names`() {
        message(1, threadId = 8, date = 20, body = "sent")
        message(2, threadId = 9, date = 30, isMms = true)

        assertFalse(repository.holdsNewestMessage(conversation(7, date = 20)))
        assertFalse(repository.holdsNewestMessage(conversation(8, date = 21).copy(snippet = "sent")))
        assertFalse(repository.holdsNewestMessage(conversation(8, date = 20).copy(snippet = "reply")))
        assertTrue(repository.holdsNewestMessage(conversation(8, date = 20).copy(snippet = "sent")))
        assertTrue(repository.holdsNewestMessage(conversation(8, date = 19).copy(snippet = "older")))
        // An MMS snippet is its subject or text part, so any MMS at that second counts.
        assertTrue(repository.holdsNewestMessage(conversation(9, date = 30).copy(snippet = "photo")))
    }

    @Test
    fun `a scheduled message is not a cached Telephony message`() {
        message(1, threadId = 8, date = 20, body = "old")
        message(2, threadId = 8, scheduled = true, date = 900, body = "tomorrow")
        message(3, threadId = 9, scheduled = true, date = 900, body = "tomorrow")

        assertFalse(repository.holdsNewestMessage(conversation(8, date = 25).copy(snippet = "arrived elsewhere")))
        assertFalse(repository.holdsNewestMessage(conversation(9, date = 25).copy(snippet = "arrived elsewhere")))
    }

    @Test
    fun `a snippet whose line breaks the provider turned into spaces still names the cached message`() {
        message(1, threadId = 8, date = 20, body = "Win\nnow")

        assertTrue(repository.holdsNewestMessage(conversation(8, date = 20).copy(snippet = "Win now")))
    }

    @Test
    fun `no preview is written for a thread Room lacks`() {
        message(1, threadId = 8, date = 10, body = "hello")
        repository.refreshPreview(8)
        assertTrue(conversations.rows.isEmpty())
    }

    // endregion
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T> unsupported(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T

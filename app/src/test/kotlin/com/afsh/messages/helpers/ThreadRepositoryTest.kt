package com.afsh.messages.helpers

import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test

private const val INBOX = 1
private const val SENT = 2
private const val NOW = 1_000_000_000L

/** Room and Telephony in memory, for one or more threads. */
private class FakeThreadPlatform : ThreadRepository.Platform {
    override var useRecycleBin = false
    /** Room's copy of each message. */
    val room = LinkedHashMap<Long, Message>()
    val recycleBin = LinkedHashSet<Long>()
    /** Telephony's messages, any thread. */
    var provider = ArrayList<Message>()
    val spamReasons = HashMap<Long, Int>()
    var threadIdForAddresses = 0L
    var nextId = 900L
    var failRoom = false
    val providerQueries = ArrayList<Int>()
    val cleared = ArrayList<Long>()
    val readThreads = ArrayList<Long>()
    val readMessages = ArrayList<Long>()
    val refreshed = ArrayList<Long>()
    val deleted = ArrayList<Long>()
    val droppedScheduled = ArrayList<Long>()
    val restored = ArrayList<Long>()
    /** Each bin move and restore with its transport: SMS and MMS ids overlap. */
    val binnedTransports = ArrayList<Pair<Long, Boolean>>()
    val restoredTransports = ArrayList<Pair<Long, Boolean>>()
    val temporaryThreads = ArrayList<Pair<Message, Long>>()
    val inboxRefreshes = ArrayList<Long>()

    override fun isManuallyUnread(threadId: Long, message: Message) = false
    override fun isHidden(threadId: Long, message: Message) = false

    override fun cachedMessages(threadId: Long, recycleBin: Boolean): List<Message> {
        if (failRoom) error("Room failed")
        return room.values.filter { it.threadId == threadId && (it.id in this.recycleBin) == recycleBin }
    }
    override fun recycledMessages(threadId: Long) = room.values.filter { it.threadId == threadId && it.id in recycleBin }
    override fun scheduledMessages(threadId: Long) = room.values.filter { it.threadId == threadId && it.isScheduled }
    override fun clearExpiredScheduled(threadId: Long, messages: List<Message>) {
        cleared += threadId
    }
    override fun cache(messages: List<Message>) = messages.forEach { room[it.id] = it }
    override fun cacheIfMissing(message: Message) {
        room.putIfAbsent(message.id, message)
    }
    override fun moveScheduled(messages: List<Message>, threadId: Long) = messages.forEach { room[it.id] = it.copy(threadId = threadId) }
    override fun spamReasons(threadId: Long) = spamReasons
    override fun search(query: String) = room.values.filter { it.body.contains(query) }

    override fun providerMessages(threadId: Long, before: Int, includeScheduled: Boolean, includeAllUnread: Boolean, oldestLoadedDate: Int?): List<Message> {
        providerQueries += before
        val thread = provider.filter { it.threadId == threadId && (before < 0 || it.date < before) }.sortedBy { it.date }
        return ArrayList(if (before < 0) thread.takeLast(MESSAGES_LIMIT) else thread.takeLast(PAGE))
    }
    override fun threadIdFor(addresses: Set<String>) = threadIdForAddresses

    override fun markThreadRead(threadId: Long) {
        readThreads += threadId
    }
    override fun markMessageRead(id: Long) {
        readMessages += id
    }
    override fun forgetSpam(ids: List<Long>, threadId: Long) {
        ids.forEach(spamReasons::remove)
        inboxRefreshes += threadId
    }

    override fun dropScheduled(messageId: Long) {
        droppedScheduled += messageId
    }
    override fun moveToRecycleBin(messageId: Long, isMms: Boolean) {
        recycleBin += messageId
        binnedTransports += messageId to isMms
    }
    override fun restore(messageId: Long, isMms: Boolean) {
        restored += messageId
        restoredTransports += messageId to isMms
    }
    override fun delete(messageId: Long, isMms: Boolean) {
        deleted += messageId
    }
    override fun refreshConversation(threadId: Long) {
        refreshed += threadId
    }
    override fun createScheduledConversation(message: Message, threadId: Long, cached: Conversation?) {
        temporaryThreads += message to threadId
    }
    override fun newThreadId() = nextId++

    companion object {
        const val PAGE = 3
    }
}

/** Background work waits until [run]; the main thread runs at once. */
private class Steps {
    val pending = ArrayDeque<() -> Unit>()
    fun run() {
        while (pending.isNotEmpty()) pending.removeFirst()()
    }
}

private fun msg(
    id: Long,
    date: Int = id.toInt(),
    threadId: Long = 1,
    type: Int = INBOX,
    read: Boolean = true,
    scheduled: Boolean = false,
    body: String = "m$id",
    isMms: Boolean = false,
) = Message(
    id = id, body = body, type = type, status = -1, participants = ArrayList(), date = date, read = read, threadId = threadId,
    isMMS = isMms, attachment = null, senderPhoneNumber = "", senderName = "", senderPhotoUri = "", subscriptionId = 1,
    isScheduled = scheduled,
)

class ThreadRepositoryTest {
    private val platform = FakeThreadPlatform()
    private val steps = Steps()

    private fun repository(threadId: Long = 1, recycleBin: Boolean = false) = ThreadRepository(
        platform = platform,
        threadId = threadId,
        isRecycleBin = recycleBin,
        clock = { NOW },
        background = { steps.pending += it },
        main = { it() },
    )

    private fun ThreadRepository.ids() = messages.list.map { it.id }

    // region Opening

    @Test
    fun `the cached thread opens with its newest page, oldest first, without expired schedules`() {
        (1L..60L).forEach { platform.room[it] = msg(it) }
        platform.room[70] = msg(70, date = (NOW / 1000).toInt() - 10, scheduled = true)
        platform.room[71] = msg(71, date = (NOW / 1000).toInt() + 10, scheduled = true)
        val repository = repository()

        repository.loadCached()

        assertEquals(MESSAGES_LIMIT, repository.messages.list.size)
        assertEquals(12L, repository.ids().first())
        assertEquals(71L, repository.ids().last())
        assertEquals(listOf(1L), platform.cleared)
    }

    @Test
    fun `the recycle bin opens with the thread's recycled messages, and a failed read opens empty`() {
        platform.room[1] = msg(1)
        platform.room[2] = msg(2)
        platform.recycleBin += 2
        val bin = repository(recycleBin = true)
        bin.loadCached()
        assertEquals(listOf(2L), bin.ids())

        platform.failRoom = true
        val failed = repository()
        failed.loadCached()
        assertTrue(failed.messages.isEmpty)
    }

    @Test
    fun `the provider's messages replace the cached ones, without recycled ones, and say whether anything changed`() {
        platform.useRecycleBin = true
        platform.room[1] = msg(1)
        platform.room[2] = msg(2)
        platform.recycleBin += 2
        platform.provider = arrayListOf(msg(1), msg(2), msg(3))
        val repository = repository()
        repository.loadCached()

        assertFalse(repository.loadFromProvider())
        assertEquals(listOf(1L, 3L), repository.ids())

        assertTrue(repository.loadFromProvider())
    }

    @Test
    fun `the recycle bin keeps its cached messages`() {
        platform.room[2] = msg(2)
        platform.recycleBin += 2
        platform.provider = arrayListOf(msg(1))
        val repository = repository(recycleBin = true)
        repository.loadCached()

        assertTrue(repository.loadFromProvider())
        assertEquals(listOf(2L), repository.ids())
        assertTrue(platform.providerQueries.isEmpty())
    }

    @Test
    fun `the provider's messages are cached in Room`() {
        platform.provider = arrayListOf(msg(1), msg(2))
        val repository = repository()
        repository.loadFromProvider()
        repository.cacheLoaded()
        assertEquals(setOf(1L, 2L), platform.room.keys)
    }

    // endregion

    // region Older pages

    @Test
    fun `an older page goes in front, and an empty one ends paging`() {
        platform.provider = ArrayList((1L..8L).map { msg(it) })
        platform.room.putAll((6L..8L).associateWith { msg(it) })
        val repository = repository()
        repository.loadCached()
        val shown = ArrayList<List<ThreadItem>>()

        repository.loadOlder { shown += it }
        steps.run()
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L, 8L), repository.ids())
        assertEquals(1, shown.size)
        assertEquals(6, shown.single().filterIsInstance<Message>().size)

        repository.loadOlder { shown += it }
        steps.run()
        repository.loadOlder { shown += it }
        steps.run()
        assertTrue(repository.messages.allFetched)
        assertEquals(3, shown.size)

        repository.loadOlder { shown += it }
        assertTrue(steps.pending.isEmpty())
    }

    @Test
    fun `an older page waits for the one in flight`() {
        platform.provider = ArrayList((1L..8L).map { msg(it) })
        platform.room.putAll((6L..8L).associateWith { msg(it) })
        val repository = repository()
        repository.loadCached()

        repository.loadOlder {}
        repository.loadOlder {}
        assertEquals(1, steps.pending.size)
        steps.run()
        assertFalse(repository.messages.loadingOlder)
        assertNull(repository.messages.pageLoadCompletion)
    }

    @Test
    fun `an empty thread loads no older page`() {
        repository().loadOlder {}
        assertTrue(steps.pending.isEmpty())
    }

    // endregion

    // region Jumps

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun pagedThread(): ThreadRepository {
        platform.provider = ArrayList((1L..8L).map { msg(it) })
        platform.room.putAll((6L..8L).associateWith { msg(it) })
        return repository().also { it.loadCached() }
    }

    @Test
    fun `a jump to a loaded message loads nothing`() {
        val repository = pagedThread()
        var loaded: List<ThreadItem>? = emptyList()
        repository.loadUntil(scope, 7, isMms = false, onLoaded = { loaded = it }, onFailed = { error("failed") })
        assertNull(loaded)
        assertTrue(steps.pending.isEmpty())
    }

    @Test
    fun `a jump loads older pages until its message is in`() {
        val repository = pagedThread()
        var loaded: List<ThreadItem>? = null
        repository.loadUntil(scope, 2, isMms = null, onLoaded = { loaded = it }, onFailed = { error("failed") })
        steps.run()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), repository.ids())
        assertEquals(8, loaded!!.filterIsInstance<Message>().size)
        assertFalse(repository.messages.loadingOlder)
    }

    @Test
    fun `a jump to a message that is not there stops at the start of the thread`() {
        val repository = pagedThread()
        var loaded: List<ThreadItem>? = null
        repository.loadUntil(scope, 42, isMms = null, onLoaded = { loaded = it }, onFailed = { error("failed") })
        steps.run()
        assertEquals(8, repository.messages.list.size)
        assertTrue(loaded != null)
    }

    @Test
    fun `a jump waits for the older page in flight`() {
        val repository = pagedThread()
        repository.loadOlder {}
        var loaded: List<ThreadItem>? = null
        repository.loadUntil(scope, 1, isMms = null, onLoaded = { loaded = it }, onFailed = { error("failed") })
        assertEquals(1, steps.pending.size)
        steps.run()
        assertEquals(1L, repository.ids().first())
        assertTrue(loaded != null)
    }

    @Test
    fun `a jump whose page fails frees page loads again`() {
        val repository = pagedThread()
        platform.provider = ArrayList()
        val failing = object : ThreadRepository.Platform by platform {
            override fun providerMessages(threadId: Long, before: Int, includeScheduled: Boolean, includeAllUnread: Boolean, oldestLoadedDate: Int?) =
                error("Telephony failed")
        }
        val broken = ThreadRepository(failing, 1, isRecycleBin = false, clock = { NOW }, background = { steps.pending += it }, main = { it() })
        broken.messages.list = ArrayList(repository.messages.list)
        var failed = false
        broken.loadUntil(scope, 1, isMms = null, onLoaded = { error("loaded") }, onFailed = { failed = true })
        steps.run()
        assertTrue(failed)
        assertFalse(broken.messages.loadingOlder)
        assertNull(broken.messages.pageLoadCompletion)
    }

    // endregion

    // region Read state

    private fun unreadThread(): ThreadRepository {
        platform.provider = arrayListOf(msg(1), msg(2, read = false), msg(3, type = SENT))
        return repository().also {
            it.loadFromProvider()
            it.messages.providerReady = true
        }
    }

    @Test
    fun `reading at the bottom marks the thread read, here and in the provider`() {
        val repository = unreadThread()
        var marked = 0
        repository.markReadIfSeen(seen = true) { marked++ }
        steps.run()
        assertEquals(listOf(1L), platform.readThreads)
        assertFalse(repository.messages.hasUnread())
        assertEquals(1, marked)
    }

    @Test
    fun `nothing is marked away from the bottom, before the provider loads, or in the recycle bin`() {
        val repository = unreadThread()
        repository.markReadIfSeen(seen = false) { error("marked") }
        repository.messages.providerReady = false
        repository.markReadIfSeen(seen = true) { error("marked") }

        val bin = repository(recycleBin = true)
        bin.messages.list = ArrayList(repository.messages.list)
        bin.messages.providerReady = true
        bin.markReadIfSeen(seen = true) { error("marked") }
        bin.flushReadState(seen = true)

        assertTrue(steps.pending.isEmpty())
        assertTrue(platform.readThreads.isEmpty())
    }

    @Test
    fun `one thread-read write runs at a time, and a read thread writes nothing`() {
        val repository = unreadThread()
        repository.markReadIfSeen(seen = true) {}
        repository.markReadIfSeen(seen = true) {}
        assertEquals(1, steps.pending.size)
        steps.run()
        repository.markReadIfSeen(seen = true) { error("marked") }
        assertTrue(steps.pending.isEmpty())
    }

    @Test
    fun `leaving the thread flushes its read state without touching the loaded messages`() {
        val repository = unreadThread()
        repository.flushReadState(seen = true)
        steps.run()
        assertEquals(listOf(1L), platform.readThreads)
        assertTrue(repository.messages.hasUnread())
    }

    @Test
    fun `opening spam marks only its unread spam read`() {
        platform.provider = arrayListOf(msg(1, read = false), msg(2, read = false), msg(3))
        platform.spamReasons[1] = 4
        platform.spamReasons[3] = 4
        val repository = repository()
        repository.loadFromProvider()
        repository.loadSpamReasons()

        repository.markSpamRead(listOf(1, 2, 3))
        steps.run()

        assertEquals(listOf(1L), platform.readMessages)
        assertTrue(repository.messages.list.first { it.id == 1L }.read)
        assertFalse(repository.messages.list.first { it.id == 2L }.read)
    }

    // endregion

    // region Reloads

    @Test
    fun `a reload takes the provider's messages and Room's scheduled ones, and caches new outgoing ones`() {
        platform.useRecycleBin = true
        platform.threadIdForAddresses = 1
        platform.provider = arrayListOf(msg(1), msg(2), msg(3, type = SENT), msg(4))
        platform.room[2] = msg(2)
        platform.recycleBin += 2
        platform.room[50] = msg(50, date = (NOW / 1000).toInt() + 60, scheduled = true)
        platform.room[51] = msg(51, date = (NOW / 1000).toInt() - 60, scheduled = true)
        val repository = repository()
        repository.messages.list = arrayListOf(msg(1))
        repository.messages.allFetched = true

        assertFalse(repository.reload(listOf("+1")))

        assertEquals(listOf(1L, 3L, 4L, 50L), repository.ids().sorted())
        assertFalse(repository.messages.allFetched)
        assertTrue(3L in platform.room)
        assertFalse(4L in platform.room)
    }

    @Test
    fun `a scheduled-only thread moves to the real thread once the provider has one`() {
        platform.threadIdForAddresses = 5
        platform.provider = arrayListOf(msg(1, threadId = 5))
        val scheduled = msg(50, threadId = 900, date = (NOW / 1000).toInt() + 60, scheduled = true)
        platform.room[50] = scheduled
        val repository = repository(threadId = 900)
        repository.messages.list = arrayListOf(scheduled)

        assertTrue(repository.reload(listOf("+1")))

        assertEquals(5L, repository.threadId)
        assertEquals(5L, platform.room.getValue(50).threadId)
        assertEquals(listOf(1L, 50L), repository.ids().sorted())
    }

    // endregion

    // region Deleting

    @Test
    fun `deleting drops schedules, and moves to the bin, restores or deletes the rest`() {
        val scheduled = msg(50, date = (NOW / 1000).toInt() + 60, scheduled = true)
        val repository = repository()
        repository.messages.list = arrayListOf(msg(1), msg(2), msg(3), msg(4), scheduled)

        repository.delete(listOf(msg(1), scheduled), toRecycleBin = true, fromRecycleBin = false, conversation = null)
        repository.delete(listOf(msg(2)), toRecycleBin = false, fromRecycleBin = true, conversation = null)
        repository.delete(listOf(msg(3)), toRecycleBin = false, fromRecycleBin = false, conversation = null)

        assertEquals(listOf(50L), platform.droppedScheduled)
        assertEquals(setOf(1L), platform.recycleBin)
        assertEquals(listOf(2L), platform.restored)
        assertEquals(listOf(3L), platform.deleted)
        assertEquals(listOf(4L), repository.ids())
        assertEquals(listOf(1L, 1L, 1L), platform.refreshed)
    }

    @Test
    fun `the bin moves and restores each message of its own transport`() {
        val sms = msg(42)
        val mms = msg(42, isMms = true)
        val repository = repository()
        repository.messages.list = arrayListOf(sms, mms, msg(43))

        repository.delete(listOf(mms), toRecycleBin = true, fromRecycleBin = false, conversation = null)
        repository.delete(listOf(sms), toRecycleBin = false, fromRecycleBin = true, conversation = null)

        assertEquals(listOf(42L to true), platform.binnedTransports)
        assertEquals(listOf(42L to false), platform.restoredTransports)
        assertEquals(listOf(43L), repository.ids())
    }

    @Test
    fun `deleting the last real message moves the scheduled ones to a temporary thread`() {
        val scheduled = msg(50, date = (NOW / 1000).toInt() + 60, scheduled = true)
        val repository = repository()
        repository.messages.list = arrayListOf(msg(1), scheduled)

        assertTrue(repository.delete(listOf(msg(1)), toRecycleBin = false, fromRecycleBin = false, conversation = null))

        assertEquals(900L, repository.threadId)
        assertEquals(listOf(scheduled to 900L), platform.temporaryThreads)
        assertEquals(900L, platform.room.getValue(50).threadId)
    }

    // endregion

    // region Spam and search

    @Test
    fun `not spam drops the markers and refreshes the thread`() {
        platform.spamReasons[1] = 4
        platform.spamReasons[2] = 4
        val repository = repository()
        repository.loadSpamReasons()

        repository.forgetSpam(listOf(msg(1)))

        assertEquals(setOf(2L), repository.messages.spamReasons.keys)
        assertEquals(listOf(1L), platform.inboxRefreshes)
    }

    @Test
    fun `search finds loaded and older cached messages of this thread, newest first`() {
        platform.room[1] = msg(1, body = "see you")
        platform.room[2] = msg(2, body = "see me", threadId = 2)
        val repository = repository()
        repository.messages.list = arrayListOf(msg(3, body = "SEE it"), msg(4, body = "no"))

        assertEquals(listOf(3L, 1L), repository.search("see").map { it.id })
    }

    // endregion
}

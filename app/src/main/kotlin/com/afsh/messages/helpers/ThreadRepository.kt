package com.afsh.messages.helpers

import android.app.Activity
import android.content.Context
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.filterNotInByKey
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.updateScheduledMessagesThreadId
import com.afsh.messages.messaging.cancelScheduleSendPendingIntent
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.SpamMessage
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.thread.ThreadMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the messages of one conversation visit: the cached and provider loads, older pages, jumps to a
 * message, reloads, and the thread's read state.
 *
 * The loaded messages and what the timeline derives from them live in [messages]. Room, Telephony and the
 * settings go through [platform]. Methods marked blocking run off the main thread; the others start on the
 * main thread, do their I/O on [background] and call back on [main].
 */
class ThreadRepository(
    private val platform: Platform,
    threadId: Long,
    val isRecycleBin: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val background: (() -> Unit) -> Unit = { ensureBackgroundThread(it) },
    private val main: (() -> Unit) -> Unit,
    /** The screen is closing: the timeline is built empty. */
    private val isClosing: () -> Boolean = { false },
) {

    /** Room, Telephony and settings, as one conversation needs them. Blocking. */
    interface Platform {
        val useRecycleBin: Boolean
        /** Marked unread by hand: counts as unread but gets no "New messages" divider. */
        fun isManuallyUnread(threadId: Long, message: Message): Boolean
        /** Waiting for a deletion's Undo. */
        fun isHidden(threadId: Long, message: Message): Boolean

        /** Room's messages of [threadId]: the recycled ones in the [recycleBin], else the rest (or all, with the bin off). */
        fun cachedMessages(threadId: Long, recycleBin: Boolean): List<Message>
        fun recycledMessages(threadId: Long): List<Message>
        fun scheduledMessages(threadId: Long): List<Message>
        /** Drops the expired scheduled ones among [messages] (see [ConversationRepository.clearExpiredScheduledMessages]). */
        fun clearExpiredScheduled(threadId: Long, messages: List<Message>)
        fun cache(messages: List<Message>)
        fun cacheIfMissing(message: Message)
        /** Stores [messages] under [threadId]. */
        fun moveScheduled(messages: List<Message>, threadId: Long)
        /** Spam reason per spam SMS id of [threadId]. */
        fun spamReasons(threadId: Long): Map<Long, Int>
        /** Room's messages of any thread whose text contains [query], recycled ones left out. */
        fun search(query: String): List<Message>

        /**
         * Telephony's newest page of [threadId], older than [before] when not -1; with [includeAllUnread] it reaches
         * back to the oldest unread that is not spam, and with [oldestLoadedDate] to that date.
         */
        fun providerMessages(
            threadId: Long,
            before: Int = -1,
            includeScheduled: Boolean = true,
            includeAllUnread: Boolean = false,
            oldestLoadedDate: Int? = null,
        ): List<Message>
        /** Telephony's thread for [addresses], created when missing. */
        fun threadIdFor(addresses: Set<String>): Long

        /** Marks [threadId] read and refreshes the inbox's unread counts. */
        fun markThreadRead(threadId: Long)
        fun markMessageRead(id: Long)
        /** Drops the spam markers of [ids] and refreshes [threadId]'s preview and unread count. */
        fun forgetSpam(ids: List<Long>, threadId: Long)
        /** Marks [reasons]' message ids as spam again, each with its reason, and refreshes the preview and counts. */
        fun restoreSpam(reasons: Map<Long, Int>, threadId: Long)

        /** Deletes a scheduled message and cancels its alarm. */
        fun dropScheduled(messageId: Long)
        fun moveToRecycleBin(messageId: Long)
        fun restore(messageId: Long)
        fun delete(messageId: Long, isMms: Boolean)
        /** Stores [threadId]'s conversation afresh from Telephony. */
        fun refreshConversation(threadId: Long)
        fun createScheduledConversation(message: Message, threadId: Long, cached: Conversation?)
        fun newThreadId(): Long
    }

    /** The thread shown; it moves when a scheduled-only thread gets a real one, or the other way round. */
    @Volatile
    var threadId: Long = threadId
        private set

    val messages = ThreadMessages(
        isManuallyUnread = { platform.isManuallyUnread(this.threadId, it) },
        isHidden = { platform.isHidden(this.threadId, it) },
    )

    /** The conversation moved to [threadId], as a first scheduled message makes a temporary thread. */
    fun moveTo(threadId: Long) {
        this.threadId = threadId
    }

    /** A thread-read write is in flight. Main thread. */
    private var markReadInProgress = false

    /** The timeline for the loaded messages. */
    fun items(): ArrayList<ThreadItem> = if (isClosing()) ArrayList() else messages.items()

    // region Loading

    /** Blocking. Room's copy of the thread: the newest [MESSAGES_LIMIT], without expired schedules. */
    fun loadCached() {
        var loaded = try {
            ArrayList(platform.cachedMessages(threadId, recycleBin = isRecycleBin))
        } catch (_: Exception) {
            ArrayList()
        }
        platform.clearExpiredScheduled(threadId, loaded)
        loaded.removeAll { it.isScheduled && it.millis() < clock() }
        loaded.sortBy { it.date }
        if (loaded.size > MESSAGES_LIMIT) loaded = ArrayList(loaded.takeLast(MESSAGES_LIMIT))
        messages.list = loaded
    }

    /**
     * Blocking. Replaces the cached messages with the provider's, recycled ones left out; the Recycle Bin keeps
     * its own. True when that changed nothing.
     */
    fun loadFromProvider(): Boolean {
        val cachedCode = ArrayList(messages.list).hashCode()
        if (!isRecycleBin) {
            var loaded = platform.providerMessages(threadId, includeAllUnread = true)
            if (platform.useRecycleBin) {
                loaded = loaded.filterNotInByKey(platform.recycledMessages(threadId)) { it.getStableId() }
            }
            messages.list = ArrayList(loaded)
        }
        return try {
            messages.list.hashCode() == cachedCode
        } catch (_: Exception) {
            false
        }
    }

    /** Blocking. Stores the provider's messages in Room, outside the Recycle Bin. */
    fun cacheLoaded() {
        if (isRecycleBin) return
        messages.list.chunked(30).forEach(platform::cache)
    }

    /** Blocking. Reads the thread's spam reasons again. */
    fun loadSpamReasons() {
        messages.spamReasons = platform.spamReasons(threadId)
    }

    // endregion

    // region Older pages and jumps

    /** Loads the next older page, unless one is loading or none is left; [onLoaded] gets the new timeline. */
    fun loadOlder(onLoaded: (ArrayList<ThreadItem>) -> Unit) {
        if (messages.isEmpty || messages.allFetched || messages.loadingOlder) return
        val completion = claimPageLoad()
        val cutoff = messages.list.first().date
        background {
            fetchOlder(cutoff)
            val items = items()
            main {
                releasePageLoad(completion)
                onLoaded(items)
            }
        }
    }

    /**
     * For a jump: loads older pages until [messageId] is in, or the thread's start. [onLoaded] gets the new timeline,
     * or null at once when the message is already loaded. It first waits in [scope] for the page load in flight, so
     * a page load cannot win the race and drop the jump. [onFailed] runs when a page could not load.
     */
    fun loadUntil(
        scope: CoroutineScope,
        messageId: Long,
        isMms: Boolean?,
        onLoaded: (ArrayList<ThreadItem>?) -> Unit,
        onFailed: () -> Unit,
    ) {
        if (messages.contains(messageId, isMms)) {
            onLoaded(null)
            return
        }
        scope.launch {
            while (messages.pageLoadCompletion != null) {
                messages.pageLoadCompletion?.await()
            }
            // Claimed on the main thread, so no other page load can start in between.
            val completion = claimPageLoad()
            background {
                val items = try {
                    var cutoff = messages.oldestDate() ?: Int.MAX_VALUE
                    var found = messages.contains(messageId, isMms)
                    // Bounded, in case the message never turns up.
                    var loops = 0
                    while (!found && !messages.allFetched && loops < MAX_JUMP_LOOPS) {
                        if (fetchOlder(cutoff).isEmpty()) break
                        cutoff = messages.list.first().date
                        found = messages.contains(messageId, isMms)
                        loops++
                    }
                    items()
                } catch (_: Exception) {
                    // Without this, page loads stay blocked for the rest of the visit.
                    main {
                        releasePageLoad(completion)
                        onFailed()
                    }
                    return@background
                }
                main {
                    releasePageLoad(completion)
                    onLoaded(items)
                }
            }
        }
    }

    private fun claimPageLoad(): CompletableDeferred<Unit> {
        messages.loadingOlder = true
        return CompletableDeferred<Unit>().also { messages.pageLoadCompletion = it }
    }

    private fun releasePageLoad(completion: CompletableDeferred<Unit>) {
        messages.loadingOlder = false
        messages.pageLoadCompletion = null
        completion.complete(Unit)
    }

    private fun fetchOlder(cutoff: Int): List<Message> =
        messages.prependOlder(platform.providerMessages(threadId, before = cutoff).filterNotInByKey(messages.list) { it.getStableId() })

    // endregion

    // region Reloads, deleting and spam

    /**
     * Blocking. Loads the thread again from the provider, back to the oldest loaded message, with Room's scheduled
     * messages, and caches new outgoing ones. A scheduled-only thread for [addresses] moves to the real thread once
     * the provider has one; true when it did.
     */
    fun reload(addresses: Collection<String>): Boolean {
        messages.allFetched = false
        val current = messages.list
        val lastMaxId = current.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = platform.threadIdFor(addresses.toSet())
        val loaded = ArrayList(
            platform.providerMessages(
                newThreadId,
                includeScheduled = false,
                includeAllUnread = true,
                oldestLoadedDate = current.firstOrNull { !it.isScheduled }?.date,
            ),
        )
        var moved = false
        if (current.isNotEmpty() && current.all { it.isScheduled } && loaded.isNotEmpty()) {
            threadId = newThreadId
            moved = true
            platform.moveScheduled(current.filter { it.threadId != threadId }, threadId)
        }

        loaded.addAll(platform.scheduledMessages(threadId).filterNot { it.isScheduled && it.millis() < clock() })
        if (platform.useRecycleBin) loaded.removeAll(platform.recycledMessages(threadId).toSet())
        messages.list = loaded

        loaded.filter { !it.isScheduled && !it.isReceivedMessage() && it.id > lastMaxId }.forEach(platform::cacheIfMissing)
        return moved
    }

    /**
     * Blocking. Deletes [toRemove]: scheduled ones for good, the rest [toRecycleBin], restored [fromRecycleBin], or
     * for good. When only scheduled messages are left they move to a temporary thread for the [conversation];
     * true when they did.
     */
    fun delete(toRemove: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean, conversation: Conversation?): Boolean {
        messages.list.removeAll(toRemove.toSet())
        toRemove.forEach { message ->
            when {
                message.isScheduled -> platform.dropScheduled(message.id)
                toRecycleBin -> platform.moveToRecycleBin(message.id)
                fromRecycleBin -> platform.restore(message.id)
                else -> platform.delete(message.id, message.isMMS)
            }
        }
        platform.refreshConversation(threadId)

        val left = messages.list
        if (left.isEmpty() || !left.all { it.isScheduled }) return false
        val temporaryThreadId = platform.newThreadId()
        platform.createScheduledConversation(left.last(), temporaryThreadId, conversation)
        platform.moveScheduled(left, temporaryThreadId)
        threadId = temporaryThreadId
        return true
    }

    /**
     * Blocking. [selected] is not spam: its markers go and the thread's preview and counts follow. Returns the
     * reasons it took away, for [restoreSpam].
     */
    fun forgetSpam(selected: List<Message>): Map<Long, Int> {
        val reasons = messages.spamReasons
        val forgotten = selected.mapNotNull { message -> reasons[message.id]?.let { message.id to it } }.toMap()
        platform.forgetSpam(selected.map { it.id }, threadId)
        loadSpamReasons()
        return forgotten
    }

    /** Blocking. Undoes [forgetSpam]: [reasons] are spam again, for the same reasons. */
    fun restoreSpam(reasons: Map<Long, Int>) {
        if (reasons.isEmpty()) return
        platform.restoreSpam(reasons, threadId)
        loadSpamReasons()
    }

    /** Blocking. Loaded and older cached messages of this thread containing [query], newest first. */
    fun search(query: String): List<Message> {
        val loaded = messages.list.filter { it.body.contains(query, ignoreCase = true) }
        val cached = platform.search(query).filter { it.threadId == threadId }
        return (loaded + cached).distinctBy { it.getStableId() }.sortedByDescending { it.date }
    }

    // endregion

    // region Read state

    /**
     * Marks the thread read once the reader has [seen] the newest message (it is on screen; the list need not be
     * at its last pixel) with unread messages loaded, then mirrors it here and calls [onMarked] while the thread
     * is still the same. One write at a time. Main thread.
     */
    fun markReadIfSeen(seen: Boolean, onMarked: () -> Unit) {
        if (!messages.providerReady || isRecycleBin || markReadInProgress || !seen || !messages.hasUnread()) return
        markReadInProgress = true
        val visibleThreadId = threadId
        background {
            try {
                platform.markThreadRead(visibleThreadId)
            } finally {
                main {
                    markReadInProgress = false
                    if (threadId == visibleThreadId) {
                        messages.markReadLocally()
                        onMarked()
                    }
                }
            }
        }
    }

    /**
     * Leaving the thread: marks it read when the reader had [seen] the newest message, even mid-scroll. Until the
     * provider's messages are in, "seen" is only where the list happens to start.
     */
    fun flushReadState(seen: Boolean) {
        if (isRecycleBin || !messages.providerReady) return
        if (seen && messages.hasUnread()) {
            val visibleThreadId = threadId
            background { platform.markThreadRead(visibleThreadId) }
        }
    }

    /** Opening the thread from the Spam screen is what marks its spam read: the unread spam among [ids]. */
    fun markSpamRead(ids: Collection<Long>) {
        val unreadIds = messages.markSpamReadLocally(ids)
        if (unreadIds.isEmpty()) return
        background { unreadIds.forEach(platform::markMessageRead) }
    }

    // endregion

    companion object {
        private const val MAX_JUMP_LOOPS = 1000

        /** [threadId]'s visit on the [activity], which says when it is closing; [main] posts to its main thread. */
        fun from(activity: Activity, threadId: Long, isRecycleBin: Boolean) = ThreadRepository(
            platform = AndroidPlatform(activity.applicationContext),
            threadId = threadId,
            isRecycleBin = isRecycleBin,
            main = { activity.runOnUiThread(it) },
            isClosing = activity::isFinishing,
        )
    }

    private class AndroidPlatform(private val app: Context) : Platform {
        // Built on first use, off the main thread.
        private val room by lazy { app.messagesDB }
        private val telephony by lazy { TelephonyReader(app) }
        private val conversations by lazy { ConversationRepository.from(app) }
        private val recycleBin by lazy { RecycleBinRepository.from(app) }

        override val useRecycleBin get() = app.config.useRecycleBin
        override fun isManuallyUnread(threadId: Long, message: Message) = app.config.isManuallyUnread(threadId, message.id, message.isMMS)
        override fun isHidden(threadId: Long, message: Message) =
            message.getStableId() in UndoDeletion.messages || threadId in UndoDeletion.threads

        override fun cachedMessages(threadId: Long, recycleBin: Boolean): List<Message> = when {
            recycleBin -> room.getThreadMessagesFromRecycleBin(threadId)
            useRecycleBin -> room.getNonRecycledThreadMessages(threadId)
            else -> room.getThreadMessages(threadId)
        }

        override fun recycledMessages(threadId: Long) = room.getThreadMessagesFromRecycleBin(threadId)
        override fun scheduledMessages(threadId: Long) = room.getScheduledThreadMessages(threadId)
        override fun clearExpiredScheduled(threadId: Long, messages: List<Message>) =
            conversations.clearExpiredScheduledMessages(threadId, messages)
        override fun cache(messages: List<Message>) = room.insertMessages(*messages.toTypedArray())
        override fun cacheIfMissing(message: Message) {
            room.insertOrIgnore(message)
        }
        override fun moveScheduled(messages: List<Message>, threadId: Long) = app.updateScheduledMessagesThreadId(messages, threadId)
        override fun spamReasons(threadId: Long) = room.getThreadSpamMarkers(threadId).associate { it.id to it.reason }
        override fun search(query: String): List<Message> = room.getMessagesWithText("%$query%")

        override fun providerMessages(
            threadId: Long,
            before: Int,
            includeScheduled: Boolean,
            includeAllUnread: Boolean,
            oldestLoadedDate: Int?,
        ): List<Message> = telephony.getMessages(
            threadId,
            dateFrom = before,
            includeScheduledMessages = includeScheduled,
            includeAllUnread = includeAllUnread,
            oldestLoadedDate = oldestLoadedDate,
            // Unread spam stays unread on purpose; it must not stretch the window on every open.
            skipUnreadSmsIds = if (includeAllUnread) room.getThreadSpamIds(threadId) else emptyList(),
        )

        override fun threadIdFor(addresses: Set<String>) = app.getThreadId(addresses)

        override fun markThreadRead(threadId: Long) {
            conversations.markThreadRead(threadId)
            InboxRepository.refreshUnreadCounts(app, listOf(threadId))
        }

        override fun markMessageRead(id: Long) {
            conversations.markMessageRead(id, isMms = false)
        }

        override fun forgetSpam(ids: List<Long>, threadId: Long) {
            ids.forEach(room::deleteSpamMarker)
            conversations.refreshPreview(threadId)
            InboxRepository.refreshUnreadCounts(app, listOf(threadId))
        }

        override fun restoreSpam(reasons: Map<Long, Int>, threadId: Long) {
            reasons.forEach { (id, reason) -> room.insertSpamMarker(SpamMessage(id, threadId, reason)) }
            conversations.refreshPreview(threadId)
            InboxRepository.refreshUnreadCounts(app, listOf(threadId))
        }

        override fun dropScheduled(messageId: Long) {
            app.deleteScheduledMessage(messageId)
            app.cancelScheduleSendPendingIntent(messageId)
        }

        override fun moveToRecycleBin(messageId: Long) = recycleBin.moveToRecycleBin(messageId)
        override fun restore(messageId: Long) = recycleBin.restore(messageId)
        override fun delete(messageId: Long, isMms: Boolean) = app.deleteMessage(messageId, isMms)
        override fun refreshConversation(threadId: Long) = conversations.refreshFromTelephony(listOf(threadId))
        override fun createScheduledConversation(message: Message, threadId: Long, cached: Conversation?) =
            conversations.createScheduledConversation(message, threadId, cached)
        override fun newThreadId() = generateRandomId()
    }
}

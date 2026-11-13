package com.afsh.messages.ui.thread

import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.models.buildThreadItems

/**
 * The messages loaded for one conversation visit and what the timeline derives from them: unread and the
 * "New messages" divider, spam, paging, and local edits that run ahead of the provider. No I/O: the host loads
 * and writes, and hands the results here.
 *
 * Like the activity before it, this is read and replaced from both the main thread and background loads.
 */
class ThreadMessages(
    /** Marked unread by hand: counts as unread but gets no "New messages" divider. */
    private val isManuallyUnread: (Message) -> Boolean,
    /** Waiting for a deletion's Undo. */
    private val isHidden: (Message) -> Boolean,
) {
    /** Oldest first once [items] has run. */
    @Volatile
    var list: ArrayList<Message> = ArrayList()

    @Volatile
    var spamReasons: Map<Long, Int> = emptyMap()

    /** Set once the provider's messages are in; the cached rows before that can be stale about read state. */
    @Volatile
    var providerReady = false

    /** Every older page has been loaded. */
    var allFetched = false

    @Volatile
    var loadingOlder = false

    /**
     * Stable ids of the messages that were unread when this visit opened. They keep the "New messages"
     * divider in place after they are read, until the conversation is closed and opened again.
     */
    @Volatile
    var newOnOpen: Set<Long>? = null

    @Volatile
    private var manuallyUnreadOnOpen: Set<Long>? = null

    val isEmpty get() = list.isEmpty()

    fun isSpam(message: Message) = !message.isMMS && message.id in spamReasons

    // Spam read state belongs to the Spam screen, so thread-wide read state leaves it out.
    private fun countsAsUnread(message: Message) = !message.read && message.isReceivedMessage() && !message.isScheduled && !isSpam(message)

    fun hasUnread() = list.any { countsAsUnread(it) }

    /** The provider has marked the thread read; mirror it without a reload. */
    fun markReadLocally() {
        list = ArrayList(list.map { if (countsAsUnread(it)) it.copy(read = true) else it })
    }

    /**
     * Marks the unread spam among [ids] read here and returns their ids for the provider.
     * Opening the thread from the Spam screen is what marks its spam read.
     */
    fun markSpamReadLocally(ids: Collection<Long>): Set<Long> {
        val unread = list.filter { it.id in ids && isSpam(it) && !it.read }.mapTo(HashSet()) { it.id }
        if (unread.isNotEmpty()) list = ArrayList(list.map { if (!it.isMMS && it.id in unread) it.copy(read = true) else it })
        return unread
    }

    /** The timeline: messages sorted oldest first, with day headers, statuses and the "New messages" divider. */
    fun items(): ArrayList<ThreadItem> {
        val messages = list
        messages.sortBy { it.date }
        // Taken from the first provider load, before this visit marks anything read.
        val manual = manuallyUnreadOnOpen ?: messages.filter(isManuallyUnread)
            .mapTo(HashSet()) { it.getStableId() }
            .also { if (providerReady) manuallyUnreadOnOpen = it }
        if (newOnOpen == null && providerReady) {
            newOnOpen = messages.filter { !it.read && it.getStableId() !in manual }.mapTo(HashSet()) { it.getStableId() }
        }
        val pinned = newOnOpen.orEmpty()
        return buildThreadItems(
            messages = messages,
            isSpam = ::isSpam,
            isHidden = isHidden,
            isNew = { (!it.read && it.getStableId() !in manual) || it.getStableId() in pinned },
        )
    }

    /** Replaces the message with [message]'s id, or adds it. */
    fun upsert(message: Message) {
        val index = list.indexOfFirst { it.id == message.id }
        if (index >= 0) list[index] = message else list.add(message)
    }

    /** Puts an older page in front; an empty page means everything is loaded. Returns the page. */
    fun prependOlder(older: List<Message>): List<Message> {
        if (older.isEmpty()) {
            allFetched = true
        } else {
            list.addAll(0, older)
        }
        return older
    }

    /** The date before which the next older page starts. */
    fun oldestDate(): Int? = list.firstOrNull()?.date

    fun contains(messageId: Long, isMms: Boolean?) = list.any { it.id == messageId && (isMms == null || it.isMMS == isMms) }
}

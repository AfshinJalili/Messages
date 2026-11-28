package com.afsh.messages.helpers

import android.content.Context
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.extensions.getNameAndPhotoFromPhoneNumber
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.extensions.shortcutHelper
import com.afsh.messages.interfaces.ConversationsDao
import com.afsh.messages.interfaces.DraftsDao
import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.models.BlockedMessagesThread
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.SpamReviewMessage
import kotlin.time.Duration.Companion.minutes

/** What a conversation change does outside Room and Telephony. */
interface ConversationEffects {
    /** Recomputes the inbox preview columns of [threadIds]. */
    fun refreshPreview(threadIds: Collection<Long>)
    fun publish(event: MessagingEvent)
    fun setManuallyUnread(threadId: Long, messageId: Long, isMms: Boolean)
    fun clearManuallyUnread(threadId: Long)

    /** Drops what the app keeps per thread: cached participants, its notification channel, its shortcut. */
    fun forgetThread(threadId: Long)

    /** Creates or updates the launcher shortcut of [conversation], off the calling thread. */
    fun updateShortcut(conversation: Conversation)

    /** The contact name and photo of [address]; the address itself and "" when there is no contact. */
    fun sender(address: String): Pair<String, String>

    /** The contact photo of [address], or "". */
    fun photoUri(address: String): String
    fun onError(e: Exception)
    fun runInTransaction(block: () -> Unit)
}

/**
 * Owns conversations: their Room rows, the matching Telephony thread state (read, unread, archived,
 * deleted) and their unread counts. Every write lands in Telephony first and Room second, and
 * publishes what the open screens need to hear, so no caller touches the conversations table or a
 * Telephony thread directly.
 *
 * Unread counts come from Telephony. Spam SMS are counted only by [unreadSpamCounts]; the inbox
 * counts in [unreadCountsByThread] leave them out. Every call blocks; call it off the main thread.
 */
class ConversationRepository(
    private val conversations: ConversationsDao,
    private val messages: MessagesDao,
    private val drafts: DraftsDao,
    private val telephony: ConversationTelephony,
    private val effects: ConversationEffects,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // region Reads

    /**
     * Telephony's conversations ([threadId] only, when given; the newest [limit] only, when given),
     * with unread counts and draft dates.
     */
    fun conversations(
        threadId: Long? = null,
        privateContacts: List<SimpleContact> = emptyList(),
        failOnError: Boolean = false,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
        limit: Int? = null,
    ): ArrayList<Conversation> = telephony.conversations(
        threadId = threadId,
        privateContacts = privateContacts,
        unreadCounts = unreadCountsByThread(),
        draftDates = drafts.getAll().associate { it.threadId to it.date },
        failOnError = failOnError,
        onProgress = onProgress,
        limit = limit,
    )

    /** Telephony's row for [threadId], or null when the thread is gone or blocked. */
    fun conversation(threadId: Long): Conversation? = conversations(threadId).firstOrNull()

    /** Room's row for [threadId], or null when Room has none. */
    fun cachedConversation(threadId: Long): Conversation? = conversations.getConversationWithThreadId(threadId)

    /** Every Room row: archived ones, those the inbox hides as spam, and temporary scheduled ones too. */
    fun cachedConversations(): List<Conversation> = conversations.getAll()

    /** Unread incoming messages per thread, spam left out. */
    fun unreadCountsByThread(): Map<Long, Int> {
        val result = HashMap<Long, Int>(128)
        val spamIds = messages.getSpamIds().toHashSet()
        telephony.unreadSms().forEach { (id, threadId) ->
            if (id !in spamIds) result[threadId] = (result[threadId] ?: 0) + 1
        }
        telephony.unreadMmsThreadIds().forEach { threadId -> result[threadId] = (result[threadId] ?: 0) + 1 }
        return result
    }

    /** Unread spam SMS per thread. */
    fun unreadSpamCounts(): Map<Long, Int> {
        val result = HashMap<Long, Int>()
        val spamIds = messages.getSpamIds().toHashSet()
        if (spamIds.isEmpty()) return result
        telephony.unreadSms().forEach { (id, threadId) ->
            if (id in spamIds) result[threadId] = (result[threadId] ?: 0) + 1
        }
        return result
    }

    /**
     * True when Room has no Telephony thread (first import), Telephony can't be counted, or the thread
     * counts disagree. Both sides count only what a reconcile keeps: Room's temporary scheduled rows and
     * the Telephony threads the inbox leaves out (see [ConversationTelephony.inboxThreadCount]) would
     * otherwise run the full pass on every launch, resume and send. A newer message in a thread Room
     * already has is not drift: the recent-window pass ([InboxReconciler.syncRecentWindow]) picks it up
     * without walking every thread.
     */
    fun needsFullReconcile(): Boolean {
        val roomCount = conversations.getTelephonyThreadCount()
        if (roomCount == 0) return true
        val telephonyCount = telephony.inboxThreadCount()
        return telephonyCount < 0 || telephonyCount != roomCount
    }

    /**
     * True when Room has cached the newest message of [conversation], a Telephony-dated row. Room is
     * behind when the thread date is newer than every message it holds, or when another message
     * landed in the same second as Room's newest: then Room has none the thread snippet names. A Room
     * message newer than the thread date (deleted by another app) counts as cached. Scheduled messages
     * are not Telephony's and never count. Spam and recycled messages do: against visible ones only,
     * a newer spam message would look uncached.
     */
    fun holdsNewestMessage(conversation: Conversation): Boolean {
        val latest = messages.getThreadLatestDate(conversation.threadId) ?: return false
        return when {
            conversation.date > latest -> false
            conversation.date < latest -> true
            else -> messages.hasMessageForSnippet(conversation.threadId, latest, conversation.snippet)
        }
    }

    /** The thread of SMS [messageId]: Room's when it knows, otherwise Telephony's. Null for 0 or unknown. */
    fun smsThreadId(messageId: Long): Long? {
        if (messageId == 0L) return null
        messages.getThreadIdForMessage(messageId, isMms = false)?.takeIf { it != 0L }?.let { return it }
        return telephony.smsThreadId(messageId)
    }

    /**
     * The spam SMS, newest first, read from Telephony: a marker whose SMS was deleted elsewhere shows
     * nothing. Each sender is resolved once.
     */
    fun spamMessages(): List<SpamReviewMessage> {
        val markers = messages.getSpamMarkers().associateBy { it.id }
        if (markers.isEmpty()) return emptyList()
        val rows = telephony.smsRows(markers.keys)
        val senders = HashMap<Long, Pair<String, String>>()
        return rows.map { row ->
            val (title, photo) = senders.getOrPut(row.threadId) {
                val conversation = conversations.getConversationWithThreadId(row.threadId)
                if (conversation != null) conversation.title to conversation.photoUri else effects.sender(row.address)
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
    fun spamThreads(): List<BlockedMessagesThread> =
        spamMessages().groupBy { it.threadId }.map { (threadId, messages) ->
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

    // endregion

    // region Writes

    /**
     * Stores a Telephony snapshot of a conversation, merged with Room's row of its thread (see
     * [mergeConversationWithCache]), and refreshes its preview in the same transaction: a new row is
     * written with all_spam unset, and the inbox must not see a spam-only thread before its preview
     * hides it.
     */
    fun insertOrUpdate(conversation: Conversation) = effects.runInTransaction {
        val updated = mergeConversationWithCache(conversation, conversations.getConversationWithThreadId(conversation.threadId))
        conversations.insertOrUpdate(updated)
        effects.refreshPreview(listOf(updated.threadId))
    }

    /**
     * A provider pass's write: stores Telephony [snapshot] (see [reconciledRow]) only when Room's row of its
     * thread is still [readBefore], the row the pass read before it read Telephony. A row deleted since stays
     * deleted, and a row written since (an incoming message, a read, a send) keeps that write: Telephony's
     * snapshot may predate it. The next pass reads both again. Call it inside the pass's transaction. True
     * when it wrote.
     */
    fun storeIfUnchanged(snapshot: Conversation, readBefore: Conversation?, trustTelephony: Boolean = false): Boolean {
        if (conversations.getConversationWithThreadId(snapshot.threadId) != readBefore) return false
        val row = reconciledRow(snapshot, readBefore, trustTelephony) ?: return false
        conversations.insertOrUpdate(row)
        effects.refreshPreview(listOf(row.threadId))
        return true
    }

    /**
     * A provider pass's removal of [readBefore], a row whose thread Telephony no longer has: dropped only
     * when Room's row is still the one the pass read before it read Telephony (see [storeIfUnchanged]).
     */
    fun removeIfUnchanged(readBefore: Conversation) {
        if (conversations.getConversationWithThreadId(readBefore.threadId) == readBefore) removeFromCache(readBefore.threadId)
    }

    /** Runs [block]'s writes in one transaction, so the inbox re-queries once for all of them. */
    fun runInTransaction(block: () -> Unit) = effects.runInTransaction(block)

    /** Gives [conversation] a custom title. Returns the renamed row. */
    fun rename(conversation: Conversation, newTitle: String): Conversation {
        val updated = conversation.copy(title = newTitle, usesCustomTitle = true)
        try {
            conversations.insertOrUpdate(updated)
            effects.updateShortcut(updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return updated
    }

    /** Archives or unarchives [threadId]. Room is left alone when Telephony has no archive. */
    fun setArchived(threadId: Long, archived: Boolean) {
        if (!telephony.setArchived(threadId, archived)) return
        if (archived) conversations.moveToArchive(threadId) else conversations.unarchive(threadId)
    }

    /** Deletes [threadId] for good, from Telephony and Room; the recycle bin is not used. */
    fun delete(threadId: Long) {
        telephony.deleteThread(threadId)
        conversations.deleteThreadId(threadId)
        messages.deleteThreadMessages(threadId)
        effects.forgetThread(threadId)
    }

    /** Drops the Room row of [threadId] only, for a thread Telephony no longer has or a temporary one. */
    fun removeFromCache(threadId: Long) {
        conversations.deleteThreadId(threadId)
    }

    /**
     * Makes Telephony recompute thread dates and snippets, then stores each of [threadIds] afresh. A thread
     * Telephony no longer has loses its Room row, unless it is a temporary scheduled one. A thread that
     * can't be read keeps its row; the others are still refreshed.
     */
    fun refreshFromTelephony(threadIds: Iterable<Long>) {
        try {
            telephony.touchThreads()
        } catch (_: Exception) {
        }
        for (threadId in threadIds) {
            try {
                val fresh = conversations(threadId, failOnError = true).firstOrNull()
                when {
                    fresh != null -> insertOrUpdate(fresh)
                    conversations.getConversationWithThreadId(threadId)?.isScheduled != true -> removeFromCache(threadId)
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Shows a just-sent [snippet] on [threadId] at once, read, without waiting for Telephony. */
    fun bumpAfterOutgoing(threadId: Long, snippet: String) {
        val now = (clock() / 1000).toInt()
        val row = conversations.getConversationWithThreadId(threadId) ?: conversation(threadId) ?: return
        conversations.insertOrUpdate(row.copy(snippet = snippet, date = now, read = true, unreadCount = 0))
        effects.refreshPreview(listOf(threadId))
    }

    /** Shows a just-scheduled [snippet] on [threadId], dated now. Nothing when Room has no row. */
    fun bumpAfterScheduling(threadId: Long, snippet: String) {
        val row = conversations.getConversationWithThreadId(threadId) ?: return
        conversations.insertOrUpdate(row.copy(date = (clock() / 1000).toInt(), snippet = snippet))
    }

    /** An edited scheduled message: [threadId]'s snippet becomes [new] only while it still shows [old]. */
    fun replaceSnippet(threadId: Long, old: String, new: String) {
        val row = conversations.getConversationWithThreadId(threadId) ?: return
        if (row.snippet == old) conversations.insertOrUpdate(row.copy(snippet = new))
    }

    /**
     * Recomputes [threadId]'s denormalized inbox preview from Room: the latest message that is neither
     * spam nor in the recycle bin, its date only when the thread has spam, and whether it is all spam.
     * Nothing when Room has no row. When Room has not cached the newest message yet (see
     * [holdsNewestMessage]), no override is written and the conversation's own snippet and date show.
     */
    fun refreshPreview(threadId: Long) {
        val conversation = conversations.getConversationWithThreadId(threadId) ?: return
        val latest = if (holdsNewestMessage(conversation)) messages.getLatestVisiblePreview(threadId) else null
        val spamCount = messages.getThreadSpamCount(threadId)
        conversations.updatePreview(
            threadId = threadId,
            snippet = latest?.body,
            date = if (spamCount > 0) latest?.date else null,
            allSpam = conversation.messageCount > 0 && spamCount >= conversation.messageCount,
        )
    }

    /**
     * Copies Telephony's unread counts into Room ([threadIds] only, when given). Only counts that
     * changed are written, in one transaction: every UPDATE fires Room's invalidation even with the
     * same value, and each one re-ran the inbox query and rebuilt the list.
     */
    fun syncUnreadCounts(threadIds: Collection<Long> = emptyList()) {
        val counts = unreadCountsByThread()
        val wanted = threadIds.toHashSet()
        val changed = conversations.getUnreadCounts()
            .filter { (wanted.isEmpty() || it.threadId in wanted) && (counts[it.threadId] ?: 0) != it.unreadCount }
        if (changed.isEmpty()) return
        effects.runInTransaction {
            changed.forEach { conversations.updateUnreadCount(it.threadId, counts[it.threadId] ?: 0) }
        }
    }

    // endregion

    // region Read state

    /** Marks one message read in Telephony and Room. False when Telephony had no such message. */
    fun markMessageRead(id: Long, isMms: Boolean): Boolean {
        if (!telephony.markMessageRead(id, isMms)) return false
        messages.markRead(id, isMms)
        return true
    }

    /**
     * Marks the unread incoming [visibleMessages] of [threadId] read, then stores the thread's new
     * unread count. Returns the stable ids of the messages marked.
     */
    fun markVisibleMessagesRead(threadId: Long, visibleMessages: List<Message>): Set<Long> {
        val completed = HashSet<Long>()
        visibleMessages.filter { it.threadId == threadId && it.isReceivedMessage() && !it.isScheduled && !it.read }.forEach { message ->
            try {
                if (markMessageRead(message.id, message.isMMS)) completed.add(message.getStableId())
            } catch (e: Exception) {
                effects.onError(e)
            }
        }
        val count = unreadCountsByThread()[threadId] ?: 0
        conversations.updateUnreadCount(threadId, count)
        effects.publish(MessagingEvent.ReadStateChanged(threadId, count == 0, count))
        return completed
    }

    /**
     * Marks every incoming message of [threadId] read. Spam keeps its read state: that belongs to the
     * Spam screen.
     */
    fun markThreadRead(threadId: Long) {
        telephony.markThreadRead(threadId, skipSmsIds = messages.getThreadSpamIds(threadId))
        messages.markThreadRead(threadId)
        conversations.markRead(threadId)
        effects.clearManuallyUnread(threadId)
        effects.publish(MessagingEvent.ReadStateChanged(threadId, read = true, unreadCount = 0))
    }

    /**
     * Marks only the newest received message unread, as Google Messages does, so the inbox shows a dot
     * rather than the size of the whole thread. The message is remembered as manually unread, so the
     * thread opens without a "new messages" divider above it. Spam is never the one marked.
     */
    fun markThreadUnread(threadId: Long) {
        val target = telephony.newestIncoming(threadId, skipSmsIds = messages.getThreadSpamIds(threadId))
        if (target != null) {
            val (messageId, isMms) = target
            telephony.markMessageUnread(messageId, isMms)
            messages.markUnread(messageId, isMms)
            effects.setManuallyUnread(threadId, messageId, isMms)
        }
        val count = unreadCountsByThread()[threadId] ?: 0
        if (count > 0) conversations.updateUnreadCount(threadId, count) else conversations.markUnread(threadId)
        effects.publish(MessagingEvent.ReadStateChanged(threadId, read = false, unreadCount = count.takeIf { it > 0 }))
    }

    // endregion

    // region Spam markers

    /**
     * Drops spam markers whose SMS is gone from Telephony (deleted by another app): extra markers can
     * hide a thread that has real messages. A failed query removes nothing.
     */
    fun removeOrphanSpamMarkers() {
        val markers = messages.getSpamMarkers()
        if (markers.isEmpty()) return
        val existing = telephony.existingSmsIds(markers.map { it.id }) ?: return
        val removed = markers.filter { it.id !in existing }
        removed.forEach { messages.deleteSpamMarker(it.id) }
        if (removed.isNotEmpty()) effects.refreshPreview(removed.map { it.threadId })
    }

    // endregion

    // region Scheduled conversations

    /**
     * Stores a temporary conversation [threadId] for a scheduled [message] in a thread Telephony does
     * not have yet. A custom title of the [cached] row is kept.
     */
    fun createScheduledConversation(message: Message, threadId: Long, cached: Conversation?) {
        val addresses = message.participants.getAddresses()
        val photoUri = if (addresses.size == 1) effects.photoUri(addresses.first()) else ""
        val title = if (cached != null && cached.usesCustomTitle) cached.title else message.participants.getThreadTitle()
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
            usesCustomTitle = cached?.usesCustomTitle == true,
            isArchived = false,
            unreadCount = 0,
        )
        try {
            conversations.insertOrUpdate(conversation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Telephony's thread for the participants of temporary scheduled [conversation]: the one its messages
     * land in once sent. Null when no scheduled message is left to name them, or Telephony can't say.
     * Android creates the thread, empty, when there is none yet, as opening the conversation does.
     */
    fun telephonyThreadFor(conversation: Conversation): Long? {
        val participants = messages.getScheduledThreadMessages(conversation.threadId).firstOrNull()?.participants ?: return null
        val addresses = participants.getAddresses().toSet().takeIf { it.isNotEmpty() } ?: return null
        return telephony.threadIdFor(addresses).takeIf { it != 0L }
    }

    /**
     * Temporary scheduled conversation [temporaryThreadId] has a real Telephony thread now, [threadId]: its
     * scheduled messages move there and its row goes. The real thread's row stays a Telephony row, which the
     * provider pass stores; only a custom title carries over, when that row has none.
     */
    fun foldScheduled(temporaryThreadId: Long, threadId: Long) {
        val temporary = conversations.getConversationWithThreadId(temporaryThreadId)
        messages.getScheduledThreadMessages(temporaryThreadId).forEach { message ->
            messages.insertOrUpdate(message.copy(threadId = threadId))
        }
        removeFromCache(temporaryThreadId)
        val real = conversations.getConversationWithThreadId(threadId)
        if (temporary != null && temporary.usesCustomTitle && real != null && !real.usesCustomTitle) {
            conversations.insertOrUpdate(real.copy(title = temporary.title, usesCustomTitle = true))
        }
        effects.refreshPreview(listOf(threadId))
    }

    /**
     * Deletes the scheduled messages of [threadId] ([messagesToDelete], when given) that are more than
     * a minute overdue. A temporary conversation left with none is deleted too.
     */
    fun clearExpiredScheduledMessages(threadId: Long, messagesToDelete: List<Message>? = null) {
        val messages = messagesToDelete ?: this.messages.getScheduledThreadMessages(threadId)
        val cutoff = clock() - 1.minutes.inWholeMilliseconds
        try {
            messages.filter { it.isDueBefore(cutoff) }.forEach { this.messages.delete(it.id, it.isMMS) }
            if (messages.filterNot { it.isDueBefore(cutoff) }.isEmpty()) {
                val conversation = conversations.getConversationWithThreadId(threadId)
                if (conversation != null && conversation.isScheduled) {
                    conversations.deleteThreadId(threadId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // endregion

    companion object {
        fun from(context: Context): ConversationRepository {
            val app = context.applicationContext
            val database = app.getMessagesDB()
            return ConversationRepository(
                conversations = database.ConversationsDao(),
                messages = database.MessagesDao(),
                drafts = database.DraftsDao(),
                telephony = TelephonyConversations(app),
                effects = AndroidConversationEffects(app),
            )
        }
    }
}

/**
 * Merges a Telephony [incoming] conversation snapshot with the cached Room row while preserving
 * app-owned metadata, optimistic local send bumps, and protection against stale reconcile reads.
 *
 * A stale provider snapshot (no newer date and no higher message count) must not undo a local
 * mark-read, whereas a newly arrived message must immediately surface its unread state and count.
 *
 * [trustTelephony] is for the forced pass after this app becomes the default SMS app again. Another
 * app may have marked a thread unread or deleted its newest message meanwhile, and Room may hold a
 * stale preview, so Telephony's date, snippet and read state win. Custom titles and the preview
 * columns are still kept: Telephony has no say over them.
 *
 * [incoming] is a Telephony row, so the result is never a temporary scheduled thread, and [cached]
 * must be the same thread's row. A temporary thread lends a real one nothing but a custom title (see
 * [ConversationRepository.foldScheduled]); its flag and its date would make the real thread look
 * scheduled.
 */
internal fun mergeConversationWithCache(
    incoming: Conversation,
    cached: Conversation?,
    trustTelephony: Boolean = false,
): Conversation {
    if (cached == null) return incoming
    require(cached.threadId == incoming.threadId) { "thread ${incoming.threadId} merged with thread ${cached.threadId}" }
    var updated = incoming
    if (cached.usesCustomTitle) {
        updated = updated.copy(
            title = cached.title,
            usesCustomTitle = true,
        )
    }
    // A Telephony row is never a temporary scheduled thread. A cached row flagged so took a temporary
    // thread's flag, date and snippet in an old fold, so none of them stands as a newer local state.
    val trustLocal = !trustTelephony && !cached.isScheduled
    // Keep a newer local preview (e.g. optimistic send bump) until Telephony catches up.
    if (trustLocal && cached.date > updated.date) {
        updated = updated.copy(
            date = cached.date,
            snippet = cached.snippet,
        )
    }
    val hasNewerMessage = incoming.date > cached.date || incoming.messageCount > cached.messageCount
    if (trustLocal && cached.read && cached.unreadCount == 0 && !updated.read && !hasNewerMessage) {
        updated = updated.copy(read = true, unreadCount = 0)
    }
    // REPLACE would clear denormalized preview columns; keep them until refresh below.
    return updated.copy(
        previewSnippet = cached.previewSnippet,
        previewDate = cached.previewDate,
        allSpam = cached.allSpam,
    )
}

/**
 * The row a provider pass stores for Telephony [snapshot] over Room's [cached] row (see
 * [mergeConversationWithCache]), or null when it would change nothing, since every write re-runs the
 * inbox query. Archived rows are left alone.
 */
internal fun reconciledRow(snapshot: Conversation, cached: Conversation?, trustTelephony: Boolean = false): Conversation? {
    if (cached?.isArchived == true) return null
    val merged = mergeConversationWithCache(snapshot, cached, trustTelephony)
    return merged.takeUnless { cached != null && Conversation.areContentsTheSame(old = cached, new = it) }
}

private class AndroidConversationEffects(private val app: Context) : ConversationEffects {
    override fun refreshPreview(threadIds: Collection<Long>) = ConversationPreview.refresh(app, threadIds)
    override fun publish(event: MessagingEvent) {
        MessagingEvents.publish(event)
    }
    override fun setManuallyUnread(threadId: Long, messageId: Long, isMms: Boolean) =
        app.config.setManuallyUnread(threadId, messageId, isMms)
    override fun clearManuallyUnread(threadId: Long) = app.config.clearManuallyUnread(threadId)

    override fun forgetThread(threadId: Long) {
        MessagingCache.participantsCache.remove(threadId)
        val config = app.config
        if (config.customNotifications.contains(threadId.toString())) {
            config.removeCustomNotificationsByThreadId(threadId)
            app.notificationManager.deleteNotificationChannel(threadId.toString())
        }
        val shortcuts = app.shortcutHelper
        if (shortcuts.getShortcut(threadId) != null) {
            shortcuts.removeShortcutForThread(threadId)
        }
    }

    override fun updateShortcut(conversation: Conversation) {
        ensureBackgroundThread { app.shortcutHelper.createOrUpdateShortcut(conversation) }
    }

    override fun sender(address: String): Pair<String, String> =
        app.getNameAndPhotoFromPhoneNumber(address).let { it.name to it.photoUri.orEmpty() }

    override fun photoUri(address: String): String = SimpleContactsHelper(app).getPhotoUriFromPhoneNumber(address)
    override fun onError(e: Exception) = app.showErrorToast(e)
    override fun runInTransaction(block: () -> Unit) = app.getMessagesDB().runInTransaction(block)
}

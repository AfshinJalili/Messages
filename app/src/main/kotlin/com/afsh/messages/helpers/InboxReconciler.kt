package com.afsh.messages.helpers

import android.content.Context
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.MyContactsContentProvider
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.models.Conversation

/**
 * Pulls conversation metadata from the Telephony provider into Room. Runs debounced in the
 * background; the inbox UI observes Room directly instead of waiting for this to finish.
 */
object InboxReconciler {

    /**
     * @param forced the pass [InboxRepository.requestForceReconcile] asked for after this app became
     * the default SMS app again: Telephony's date, snippet and read state win over Room's (see
     * [mergeConversationWithCache]), and the inbox shows progress as on the first import, since the
     * message import that follows may copy many threads.
     */
    fun reconcile(
        context: Context,
        forced: Boolean = false,
        onFinished: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        ensureBackgroundThread {
            try {
                val app = context.applicationContext
                val repository = ConversationRepository.from(app)
                // Raw rows, including threads the inbox hides as spam. Otherwise hidden threads would be
                // re-inserted on every run and never removed when Telephony drops them.
                val allCached = try {
                    repository.cachedConversations()
                } catch (_: Exception) {
                    emptyList()
                }

                val privateContacts = app.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use { cursor ->
                    MyContactsContentProvider.getSimpleContacts(app, cursor)
                }
                // An empty cache means the first import: the inbox shows how far it has got.
                val showProgress = forced || allCached.isEmpty()
                val conversations = repository.conversations(
                    privateContacts = privateContacts,
                    failOnError = true,
                    onProgress = if (showProgress) InboxImport::reportConversations else null,
                )
                // Hold the bar through Room writes; message indexing continues the same percent.
                if (showProgress) InboxImport.conversationsDone()
                storeConversations(repository, allCached, conversations, forced)

                try {
                    repository.removeOrphanSpamMarkers()
                } catch (_: Exception) {
                    // Retried on the next reconcile, so it must not fail the rest of it.
                }

                onFinished()
            } catch (e: Exception) {
                InboxImport.abort()
                appContext(context).showErrorToast(e)
                onFailed()
            }
        }
    }

    /**
     * The full pass's writes: Telephony's [conversations] against [allCached], Room's rows as the pass read
     * them. One transaction for every write, so the inbox re-queries once per reconcile instead of once per
     * changed row.
     */
    internal fun storeConversations(
        repository: ConversationRepository,
        allCached: List<Conversation>,
        conversations: List<Conversation>,
        forced: Boolean,
    ) {
        val cachedConversations = allCached.filterTo(ArrayList()) { !it.isArchived }
        val cachedIds = allCached.mapTo(HashSet()) { it.threadId }
        val conversationsById = conversations.associateBy { it.threadId }
        // A temporary thread folds into Telephony's thread for exactly its participants once that one has
        // messages; a shared first number is not enough. The lookup is a provider call, so it runs before
        // the transaction.
        val folds = allCached.filter { it.isScheduled }.mapNotNull { temporary ->
            repository.telephonyThreadFor(temporary)
                ?.takeIf { it != temporary.threadId && it in conversationsById }
                ?.let { temporary.threadId to it }
        }

        repository.runInTransaction {
            conversations.forEach { clonedConversation ->
                if (cachedIds.add(clonedConversation.threadId)) {
                    repository.insertOrUpdate(clonedConversation, cached = null)
                    if (!clonedConversation.isArchived) {
                        cachedConversations.add(clonedConversation)
                    }
                }
            }

            allCached.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId
                if (!cachedConversation.isScheduled && !conversationsById.containsKey(threadId)) {
                    repository.removeFromCache(threadId)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversationsById[cachedConv.threadId]?.takeIf {
                    !Conversation.areContentsTheSame(old = cachedConv, new = it)
                }
                if (conv != null) {
                    repository.insertOrUpdate(conv, cachedConv, trustTelephony = forced)
                }
            }

            // After the real threads are stored, so a carried custom title stays.
            folds.forEach { (temporaryThreadId, threadId) -> repository.foldScheduled(temporaryThreadId, threadId) }

            // Only temporary scheduled threads can expire; querying every inbox row
            // on each reconcile was a free CPU tax after every resume and SMS.
            allCached.filter { it.isScheduled }.forEach { conversation ->
                repository.clearExpiredScheduledMessages(conversation.threadId)
            }
        }
    }

    /**
     * The warm-resume pass when Room's thread count already matches Telephony: reads only the newest
     * [limit] threads, so a new message, a read or a delete made elsewhere refreshes without walking
     * every thread. Missing rows are inserted; rows the merge with Room (see
     * [mergeConversationWithCache]) would change are rewritten, in one transaction. Rows that match
     * are not written, since every write re-runs the inbox query. Archived rows are left alone, as
     * [reconcile] leaves them. Threads outside the window, and deleted threads, are [reconcile]'s.
     * Blocks; throws when Telephony can't be read. Returns the number of rows written.
     */
    fun syncRecentWindow(context: Context, limit: Int = RECENT_WINDOW): Int =
        syncRecentWindow(ConversationRepository.from(appContext(context)), limit)

    internal fun syncRecentWindow(repository: ConversationRepository, limit: Int): Int {
        val drifted = repository.conversations(failOnError = true, limit = limit).mapNotNull { incoming ->
            val cached = repository.cachedConversation(incoming.threadId)
            when {
                cached == null -> incoming to null
                cached.isArchived -> null
                Conversation.areContentsTheSame(old = cached, new = mergeConversationWithCache(incoming, cached)) -> null
                else -> incoming to cached
            }
        }
        if (drifted.isEmpty()) return 0
        repository.runInTransaction {
            drifted.forEach { (incoming, cached) -> repository.insertOrUpdate(incoming, cached) }
        }
        return drifted.size
    }

    private fun appContext(context: Context) = context.applicationContext

    /** Threads the warm-resume pass reads: more than one screen of the inbox. */
    private const val RECENT_WINDOW = 20
}

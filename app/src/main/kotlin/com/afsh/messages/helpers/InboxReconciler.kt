package com.afsh.messages.helpers

import com.afsh.messages.extensions.getMessagesDB
import android.content.Context
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.helpers.MyContactsContentProvider
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.models.Conversation

/**
 * Pulls conversation metadata from the Telephony provider into Room. Runs debounced in the
 * background; the inbox UI observes Room directly instead of waiting for this to finish.
 */
object InboxReconciler {

    fun reconcile(
        context: Context,
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
                    ArrayList(app.conversationsDB.getAll())
                } catch (_: Exception) {
                    ArrayList()
                }
                val cachedConversations = allCached.filterTo(ArrayList()) { !it.isArchived }

                val privateContacts = app.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use { cursor ->
                    MyContactsContentProvider.getSimpleContacts(app, cursor)
                }
                // An empty cache means the first import: the inbox shows how far it has got.
                val firstImport = allCached.isEmpty()
                val conversations = repository.conversations(
                    privateContacts = privateContacts,
                    failOnError = true,
                    onProgress = if (firstImport) InboxImport::reportConversations else null,
                )
                // Hold the bar through Room writes; message indexing continues the same percent.
                if (firstImport) InboxImport.conversationsDone()
                val cachedIds = allCached.mapTo(HashSet()) { it.threadId }
                val conversationsById = conversations.associateBy { it.threadId }
                val conversationsByNumber = conversations.asReversed().associateBy { it.phoneNumber }

                // One transaction for every write below, so the inbox re-queries once per reconcile
                // instead of once per changed row.
                app.getMessagesDB().runInTransaction {
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
                        val isTemporaryThread = cachedConversation.isScheduled
                        val isConversationDeleted = !conversationsById.containsKey(threadId)
                        if (isConversationDeleted && !isTemporaryThread) {
                            repository.removeFromCache(threadId)
                        }

                        val newConversation = conversationsByNumber[cachedConversation.phoneNumber]
                        if (isTemporaryThread && newConversation != null) {
                            repository.removeFromCache(threadId)
                            app.messagesDB.getScheduledThreadMessages(threadId).forEach { message ->
                                app.messagesDB.insertOrUpdate(message.copy(threadId = newConversation.threadId))
                            }
                            repository.insertOrUpdate(newConversation, cachedConversation)
                        }
                    }

                    cachedConversations.forEach { cachedConv ->
                        val conv = conversationsById[cachedConv.threadId]?.takeIf {
                            !Conversation.areContentsTheSame(old = cachedConv, new = it)
                        }
                        if (conv != null) {
                            repository.insertOrUpdate(conv, cachedConv)
                        }
                    }

                    // Only temporary scheduled threads can expire; querying every inbox row
                    // on each reconcile was a free CPU tax after every resume and SMS.
                    allCached.filter { it.isScheduled }.forEach { conversation ->
                        repository.clearExpiredScheduledMessages(conversation.threadId)
                    }
                }

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
     * The warm-resume pass when Room's thread count and newest date already match Telephony: reads
     * only the newest [limit] threads, so a thread read, deleted from or updated elsewhere refreshes
     * without walking every thread. Blocks; throws when Telephony can't be read. Returns the number
     * of rows written.
     */
    fun syncRecentWindow(context: Context, limit: Int = RECENT_WINDOW): Int =
        ConversationRepository.from(appContext(context)).syncRecentConversations(limit)

    private fun appContext(context: Context) = context.applicationContext

    /** Threads the warm-resume pass reads: more than one screen of the inbox. */
    private const val RECENT_WINDOW = 20
}

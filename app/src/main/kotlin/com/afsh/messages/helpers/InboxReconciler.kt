package com.afsh.messages.helpers

import com.afsh.messages.extensions.getMessagesDB
import android.content.Context
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.clearExpiredScheduledMessages
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getConversations
import com.afsh.messages.extensions.insertOrUpdateConversation
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.removeOrphanSpamMarkers
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
                val conversations = try {
                    app.getConversations(
                        privateContacts = privateContacts,
                        failOnError = true,
                        onProgress = if (firstImport) InboxImport::reportConversations else null,
                    )
                } finally {
                    if (firstImport) InboxImport.conversationsDone()
                }
                val cachedIds = allCached.mapTo(HashSet()) { it.threadId }
                val conversationsById = conversations.associateBy { it.threadId }
                val conversationsByNumber = conversations.asReversed().associateBy { it.phoneNumber }

                // One transaction for every write below, so the inbox re-queries once per reconcile
                // instead of once per changed row.
                app.getMessagesDB().runInTransaction {
                    conversations.forEach { clonedConversation ->
                        if (cachedIds.add(clonedConversation.threadId)) {
                            app.conversationsDB.insertOrUpdate(clonedConversation)
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
                            app.conversationsDB.deleteThreadId(threadId)
                        }

                        val newConversation = conversationsByNumber[cachedConversation.phoneNumber]
                        if (isTemporaryThread && newConversation != null) {
                            app.conversationsDB.deleteThreadId(threadId)
                            app.messagesDB.getScheduledThreadMessages(threadId).forEach { message ->
                                app.messagesDB.insertOrUpdate(message.copy(threadId = newConversation.threadId))
                            }
                            app.insertOrUpdateConversation(newConversation, cachedConversation)
                        }
                    }

                    cachedConversations.forEach { cachedConv ->
                        val conv = conversationsById[cachedConv.threadId]?.takeIf {
                            !Conversation.areContentsTheSame(old = cachedConv, new = it)
                        }
                        if (conv != null) {
                            app.insertOrUpdateConversation(conv, cachedConv)
                        }
                    }

                    cachedConversations.forEach { conversation ->
                        app.clearExpiredScheduledMessages(conversation.threadId)
                    }
                }

                try {
                    app.removeOrphanSpamMarkers()
                } catch (_: Exception) {
                    // Retried on the next reconcile, so it must not fail the rest of it.
                }

                onFinished()
            } catch (e: Exception) {
                appContext(context).showErrorToast(e)
                onFailed()
            }
        }
    }

    private fun appContext(context: Context) = context.applicationContext
}

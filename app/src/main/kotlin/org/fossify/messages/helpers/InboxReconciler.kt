package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.removeOrphanSpamMarkers
import org.fossify.messages.models.Conversation

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
                val conversations = app.getConversations(privateContacts = privateContacts, failOnError = true)
                val cachedIds = allCached.mapTo(HashSet()) { it.threadId }
                val conversationsById = conversations.associateBy { it.threadId }
                val conversationsByNumber = conversations.asReversed().associateBy { it.phoneNumber }

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

                try {
                    app.removeOrphanSpamMarkers()
                } catch (_: Exception) {
                    // Retried on the next reconcile, so it must not fail the rest of it.
                }

                if (app.config.appRunCount == 1) {
                    conversations.map { it.threadId }.forEach { threadId ->
                        val messages = app.getMessages(threadId, includeScheduledMessages = false)
                        messages.chunked(30).forEach { currentMessages ->
                            app.messagesDB.insertMessages(*currentMessages.toTypedArray())
                        }
                    }
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

package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.messagesDB

/**
 * Keeps denormalized inbox preview columns on [com.afsh.messages.models.Conversation] in sync
 * with messages, spam markers and the recycle bin. The inbox list reads those columns directly
 * instead of running correlated subqueries per row.
 */
object ConversationPreview {

    fun refresh(context: Context, threadId: Long) {
        val app = context.applicationContext
        val conversation = app.conversationsDB.getConversationWithThreadId(threadId) ?: return
        val latest = app.messagesDB.getLatestVisiblePreview(threadId)
        val spamCount = app.messagesDB.getThreadSpamCount(threadId)
        val allSpam = conversation.messageCount > 0 && spamCount >= conversation.messageCount
        app.conversationsDB.updatePreview(
            threadId = threadId,
            snippet = latest?.body,
            date = if (spamCount > 0) latest?.date else null,
            allSpam = allSpam,
        )
    }

    fun refresh(context: Context, threadIds: Collection<Long>) {
        threadIds.distinct().forEach { refresh(context, it) }
    }

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        app.conversationsDB.getAll().forEach { refresh(app, it.threadId) }
    }
}

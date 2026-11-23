package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.conversationsDB

/**
 * Keeps denormalized inbox preview columns on [com.afsh.messages.models.Conversation] in sync
 * with messages, spam markers and the recycle bin. The inbox list reads those columns directly
 * instead of running correlated subqueries per row. [ConversationRepository.refreshPreview] does the write.
 */
object ConversationPreview {

    fun refresh(context: Context, threadId: Long) {
        ConversationRepository.from(context).refreshPreview(threadId)
    }

    fun refresh(context: Context, threadIds: Collection<Long>) {
        val repository = ConversationRepository.from(context)
        threadIds.distinct().forEach(repository::refreshPreview)
    }

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        val repository = ConversationRepository.from(app)
        app.conversationsDB.getAll().forEach { repository.refreshPreview(it.threadId) }
    }
}

package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.messagesDB

/**
 * Runs the Spam folder through today's patterns. One-time codes caught by a phrase or a rule, and
 * rule matches the patterns no longer block, go back to their conversations. Messages caught by a
 * blocked sender stay. Returns how many moved.
 */
object SpamRecheck {
    fun run(context: Context): Int {
        val patterns = context.config.spamPatterns
        val released = ConversationRepository.from(context).spamMessages().filter { message ->
            when (message.reason) {
                BLOCK_REASON_KEYWORD -> isCode(patterns, message.body)
                BLOCK_REASON_RULE -> RuleBasedFilter.explain(patterns, message.body).verdict != FilterVerdict.BLOCK
                else -> false
            }
        }
        if (released.isEmpty()) return 0
        released.forEach { context.messagesDB.deleteSpamMarker(it.id) }
        val threads = released.map { it.threadId }.distinct()
        ConversationPreview.refresh(context, threads)
        InboxRepository.refreshUnreadCounts(context, threads)
        InboxRepository.scheduleProviderReconcile(context, immediate = true)
        return released.size
    }

    private fun isCode(patterns: SpamPatterns, body: String) =
        RuleBasedFilter.explain(patterns, body).rule == FilterRule.ONE_TIME_CODE
}

package com.afsh.messages.activities

import com.afsh.messages.extensions.markMessageRead
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.getSpamMessages
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.helpers.OPEN_SPAM
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.models.SpamReviewMessage
import com.afsh.messages.models.spamWhy
import com.afsh.messages.ui.spam.SpamEvent
import com.afsh.messages.ui.spam.SpamScreen
import com.afsh.messages.ui.spam.SpamUiState

/**
 * 20 · Spam review. Not spam restores the sender's spam to their conversation; Block sender adds
 * them to the app's block list (their messages keep arriving here). Delete all is confirmed on
 * the page and still offers Undo.
 */
class BlockedMessagesActivity : ComposePageActivity() {
    private var state by mutableStateOf(SpamUiState())
    // True from each visit until its first load, which fixes where "New messages" goes.
    private var freshVisit = true
    // Threads whose Not spam is waiting out its Undo; their markers are still in place.
    private val restoring = HashSet<Long>()

    @Composable
    override fun Page() {
        SpamScreen(state, libraryNavigation(), snackbarHost, ::onEvent)
    }

    override fun onResume() {
        super.onResume()
        freshVisit = true
        reload()
    }

    override fun onPause() {
        super.onPause()
        freshVisit = true
    }

    override fun reload() {
        val skip = restoring.toSet()
        ensureBackgroundThread {
            val messages = runCatching { getSpamMessages() }.getOrDefault(emptyList())
                .filter { it.id !in UndoDeletion.spam && it.threadId !in skip }
                .map { it.copy(why = spamWhy(it.reason, it.address, it.body)) }
            runOnUiThread {
                val newIds = if (freshVisit) messages.filterNot { it.read }.mapTo(HashSet()) { it.id } else state.newIds
                freshVisit = false
                // Read on screen in this visit stays read even if Telephony hasn't caught up yet.
                val seen = state.messages.filter { it.read }.mapTo(HashSet()) { it.id }
                state = SpamUiState(
                    loading = false,
                    messages = messages.map { if (it.id in seen) it.copy(read = true) else it },
                    newIds = newIds,
                )
            }
        }
    }

    private fun markSeen(ids: Set<Long>) {
        state = state.copy(messages = state.messages.map { if (it.id in ids) it.copy(read = true) else it })
        // Telephony is what the Spam badge counts, so this is what clears it.
        ensureBackgroundThread { ids.forEach { markMessageRead(it, isMMS = false) } }
    }

    private fun onEvent(event: SpamEvent) {
        when (event) {
            SpamEvent.Back -> finish()
            SpamEvent.OpenRules -> startActivity(Intent(this, BlockRulesActivity::class.java))
            is SpamEvent.Open -> openThread(event.message)
            is SpamEvent.NotSpam -> restore(event.message)
            is SpamEvent.BlockSender -> ensureBackgroundThread { config.addSpamNumber(event.message.address) }
            SpamEvent.DeleteAll -> deleteWithUndo(state.messages)
            is SpamEvent.Seen -> markSeen(event.ids)
        }
    }

    private fun openThread(message: SpamReviewMessage) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, message.threadId)
            putExtra(THREAD_TITLE, message.sender)
            putExtra(OPEN_SPAM, true)
            startActivity(this)
        }
    }

    /**
     * Not spam moves the sender's spam back to their conversation, with Undo. A sender on the app's
     * block list is unblocked too; otherwise their next message would land here again.
     */
    private fun restore(message: SpamReviewMessage) {
        val threadId = message.threadId
        val moved = state.messages.filter { it.threadId == threadId }
        restoring += threadId
        state = state.copy(messages = state.messages - moved.toSet())
        offerUndo(
            resources.getQuantityString(R.plurals.spam_restored, moved.size, moved.size),
            onUndo = {
                restoring -= threadId
                reload()
            },
            onTimeout = {
                ensureBackgroundThread {
                    messagesDB.deleteThreadSpamMarkers(threadId)
                    moved.map { it.address }.filter { it.isNotEmpty() && config.spamNumbers.containsNumber(it) }
                        .distinct().forEach { config.removeSpamNumber(it) }
                    InboxRepository.refreshUnreadCounts(this, listOf(threadId))
                    runOnUiThread {
                        restoring -= threadId
                        reload()
                    }
                }
            },
        )
    }

    /** Deletes only the spam SMS; real messages in the same threads stay. */
    private fun deleteWithUndo(messages: List<SpamReviewMessage>) {
        val ids = messages.map { it.id }.toSet()
        UndoDeletion.spam.addAll(ids)
        reload()
        UndoDeletion.offer(
            this,
            undo = {
                UndoDeletion.spam.removeAll(ids)
                reload()
            },
            commit = {
                ids.forEach { deleteMessage(it, isMMS = false) }
                // Drops the rows of threads that are now empty and refreshes the rest.
                InboxRepository.scheduleProviderReconcile(this, immediate = true)
            },
            completed = {
                UndoDeletion.spam.removeAll(ids)
                reload()
            },
        )
    }
}

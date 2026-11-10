package com.afsh.messages.activities

import com.afsh.messages.extensions.markMessageRead
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.getSpamMessages
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.OPEN_SPAM
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.models.SpamReviewMessage
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
        ensureBackgroundThread {
            val messages = runCatching { getSpamMessages() }.getOrDefault(emptyList())
                .filter { it.id !in UndoDeletion.spam }
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

    private fun restore(message: SpamReviewMessage) {
        state = state.copy(messages = state.messages.filter { it.threadId != message.threadId })
        ensureBackgroundThread {
            messagesDB.deleteThreadSpamMarkers(message.threadId)
            InboxRepository.refreshUnreadCounts(this, listOf(message.threadId))
            runOnUiThread { reload() }
        }
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

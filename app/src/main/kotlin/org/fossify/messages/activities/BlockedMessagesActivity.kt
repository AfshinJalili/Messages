package org.fossify.messages.activities

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.getSpamMessages
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.InboxRepository
import org.fossify.messages.helpers.OPEN_SPAM
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.models.SpamReviewMessage
import org.fossify.messages.ui.spam.SpamEvent
import org.fossify.messages.ui.spam.SpamScreen
import org.fossify.messages.ui.spam.SpamUiState

/**
 * 20 · Spam review. Not spam restores the sender's spam to their conversation; Block sender adds
 * them to the app's block list (their messages keep arriving here). Delete all is confirmed on
 * the page and still offers Undo.
 */
class BlockedMessagesActivity : ComposePageActivity() {
    private var state by mutableStateOf(SpamUiState())

    @Composable
    override fun Page() {
        SpamScreen(state, libraryNavigation(), snackbarHost, ::onEvent)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        ensureBackgroundThread {
            val messages = runCatching { getSpamMessages() }.getOrDefault(emptyList())
                .filter { it.id !in UndoDeletion.spam }
            runOnUiThread { state = SpamUiState(loading = false, messages = messages) }
        }
    }

    private fun onEvent(event: SpamEvent) {
        when (event) {
            SpamEvent.Back -> finish()
            SpamEvent.OpenRules -> startActivity(Intent(this, BlockRulesActivity::class.java))
            is SpamEvent.Open -> openThread(event.message)
            is SpamEvent.NotSpam -> restore(event.message)
            is SpamEvent.BlockSender -> ensureBackgroundThread { config.addSpamNumber(event.message.address) }
            SpamEvent.DeleteAll -> deleteWithUndo(state.messages)
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

package com.afsh.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.emptyMessagesRecycleBinForConversation
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.restoreAllMessagesFromRecycleBinForConversation
import com.afsh.messages.helpers.IS_RECYCLE_BIN
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.affectsConversationList
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.library.RecycleBinEvent
import com.afsh.messages.ui.library.RecycleBinScreen
import com.afsh.messages.ui.library.RecycleBinUiState
import com.afsh.messages.ui.library.RecycledConversation

/**
 * 22 · Recycle bin. Restore is immediate. Delete forever and Empty recycle bin are confirmed on
 * the page and then still go through [UndoDeletion] before anything is erased.
 */
class RecycleBinConversationsActivity : ComposePageActivity() {
    private var state by mutableStateOf(RecycleBinUiState())

    @Composable
    override fun Page() {
        RecycleBinScreen(state, libraryNavigation(), snackbarHost, ::onEvent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadOnMessagingEvents { it.affectsConversationList() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        ensureBackgroundThread {
            val recycled = runCatching {
                val summaries = messagesDB.getRecycleBinSummaries().associateBy { it.threadId }
                conversationsDB.getAllWithMessagesInRecycleBin()
                    .filter { it.threadId !in UndoDeletion.threads }
                    .mapNotNull { conversation ->
                        summaries[conversation.threadId]?.let {
                            RecycledConversation(conversation, it.messageCount, it.deletedTs)
                        }
                    }
                    .sortedByDescending { it.deletedMillis }
            }.getOrDefault(emptyList())
            runOnUiThread { state = RecycleBinUiState(loading = false, conversations = recycled) }
        }
    }

    private fun onEvent(event: RecycleBinEvent) {
        when (event) {
            RecycleBinEvent.Back -> finish()
            is RecycleBinEvent.Open -> openConversation(event.conversation)
            is RecycleBinEvent.Restore -> restore(event.conversation)
            is RecycleBinEvent.DeleteForever -> erase(listOf(event.conversation.threadId))
            RecycleBinEvent.EmptyBin -> erase(state.conversations.map { it.conversation.threadId })
        }
    }

    private fun restore(conversation: Conversation) {
        val remaining = state.conversations.filter { it.conversation.threadId != conversation.threadId }
        state = state.copy(conversations = remaining)
        ensureBackgroundThread {
            restoreAllMessagesFromRecycleBinForConversation(conversation.threadId)
            runOnUiThread {
                reload()
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
            }
        }
    }

    private fun erase(threadIds: List<Long>) {
        deleteConversationsWithUndo(threadIds) { threadId ->
            emptyMessagesRecycleBinForConversation(threadId)
            notificationManager.cancel(threadId.hashCode())
        }
    }

    private fun openConversation(conversation: Conversation) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(IS_RECYCLE_BIN, true)
            startActivity(this)
        }
    }
}

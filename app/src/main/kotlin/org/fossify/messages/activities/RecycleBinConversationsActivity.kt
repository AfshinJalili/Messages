package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.emptyMessagesRecycleBinForConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.extensions.restoreAllMessagesFromRecycleBinForConversation
import org.fossify.messages.helpers.IS_RECYCLE_BIN
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.ui.library.RecycleBinEvent
import org.fossify.messages.ui.library.RecycleBinScreen
import org.fossify.messages.ui.library.RecycleBinUiState
import org.fossify.messages.ui.library.RecycledConversation
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
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
                refreshConversations()
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConversationsChanged(@Suppress("unused") event: Events.RefreshConversations) = reload()
}

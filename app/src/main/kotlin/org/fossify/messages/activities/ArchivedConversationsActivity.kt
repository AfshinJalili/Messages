package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.sortedForInbox
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.ui.components.ConfirmationDialog
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.library.ArchiveEvent
import org.fossify.messages.ui.library.ArchiveScreen
import org.fossify.messages.ui.library.ArchiveUiState
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 19 · Archive. Unarchiving hides the rows at once and writes the provider only when its Undo
 * window closes, because that write cannot be reversed. Deleting goes through [UndoDeletion].
 */
class ArchivedConversationsActivity : ComposePageActivity() {
    private var state by mutableStateOf(ArchiveUiState())
    private var archived: List<Conversation> = emptyList()
    private val unarchiving = mutableSetOf<Long>()
    private var confirm by mutableStateOf<DeleteRequest?>(null)

    /** A deletion waiting for its confirmation dialog. */
    private class DeleteRequest(val conversations: List<Conversation>, val all: Boolean)

    @Composable
    override fun Page() {
        ArchiveScreen(state, libraryNavigation(), snackbarHost, ::onEvent)
        confirm?.let { request ->
            val count = request.conversations.size
            ConfirmationDialog(
                title = if (request.all) {
                    stringResource(R.string.empty_archive)
                } else {
                    pluralStringResource(R.plurals.delete_conversations, count, count)
                },
                explanation = stringResource(
                    if (request.all) R.string.empty_archive_confirmation else R.string.delete_forever_conversation_text,
                ),
                confirmLabel = stringResource(org.fossify.commons.R.string.delete),
                dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
                onConfirm = {
                    confirm = null
                    delete(request.conversations)
                },
                onDismiss = { confirm = null },
                destructive = true,
            )
        }
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
            val conversations = runCatching { conversationsDB.getAllArchived() }.getOrDefault(emptyList())
            runOnUiThread {
                archived = conversations
                publish()
            }
        }
    }

    private fun publish() {
        val hidden = UndoDeletion.threads + unarchiving
        val pinned = config.pinnedConversations
        val rows = ArrayList(archived.filter { it.threadId !in hidden }).sortedForInbox(pinned).map {
            InboxRow(
                conversation = it,
                draft = null,
                pinned = it.threadId.toString() in pinned,
                muted = config.isConversationMuted(it.threadId),
            )
        }
        val visible = rows.map { it.threadId }.toSet()
        state = ArchiveUiState(loading = false, rows = rows, selected = state.selected intersect visible)
    }

    private fun onEvent(event: ArchiveEvent) {
        when (event) {
            ArchiveEvent.Back -> finish()
            is ArchiveEvent.Open -> openConversation(event.row.conversation)
            is ArchiveEvent.ToggleSelection -> toggle(event.row.threadId)
            ArchiveEvent.SelectAll -> state = state.copy(selected = state.rows.map { it.threadId }.toSet())
            ArchiveEvent.ClearSelection -> state = state.copy(selected = emptySet())
            is ArchiveEvent.Unarchive -> unarchive(event.conversations)
            is ArchiveEvent.Delete -> confirm = DeleteRequest(event.conversations, all = false)
            ArchiveEvent.DeleteAll -> confirm = DeleteRequest(state.rows.map { it.conversation }, all = true)
        }
    }

    override fun onBackPressedCompat(): Boolean {
        if (state.selecting) {
            state = state.copy(selected = emptySet())
            return true
        }
        return super.onBackPressedCompat()
    }

    private fun toggle(threadId: Long) {
        val selected = state.selected
        state = state.copy(selected = if (threadId in selected) selected - threadId else selected + threadId)
    }

    private fun unarchive(conversations: List<Conversation>) {
        val ids = conversations.map { it.threadId }.toSet() - unarchiving
        if (ids.isEmpty()) return
        unarchiving += ids
        state = state.copy(selected = emptySet())
        publish()
        offerUndo(
            message = resources.getQuantityString(R.plurals.conversations_unarchived, ids.size, ids.size),
            onUndo = {
                unarchiving -= ids
                publish()
            },
            onTimeout = {
                ensureBackgroundThread {
                    ids.forEach { updateConversationArchivedStatus(it, false) }
                    runOnUiThread {
                        unarchiving -= ids
                        reload()
                        refreshConversations()
                    }
                }
            },
        )
    }

    private fun delete(conversations: List<Conversation>) {
        state = state.copy(selected = emptySet())
        deleteConversationsWithUndo(conversations.map { it.threadId }) { threadId ->
            deleteConversation(threadId)
            notificationManager.cancel(threadId.hashCode())
        }
    }

    private fun openConversation(conversation: Conversation) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReadStateChanged(@Suppress("unused") event: Events.ConversationReadStateChanged) = reload()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConversationsChanged(@Suppress("unused") event: Events.RefreshConversations) = reload()
}

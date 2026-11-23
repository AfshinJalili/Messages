package com.afsh.messages.activities

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.lifecycleScope
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.RecycleBinRepository
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.models.Message
import com.afsh.messages.ui.components.confirm
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Deleting and restoring messages and the whole conversation. Deletions wait for their Undo in [UndoDeletion],
 * which commits them; the timeline hides them meanwhile.
 */
internal class ThreadDeletion(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val holder get() = visit.holder
    private val recycleBin by lazy { RecycleBinRepository.from(activity) }
    private val conversations by lazy { ConversationRepository.from(activity) }

    /**
     * Deletion Undo from [UndoDeletion], shown in the timeline's snackbar host above the composer.
     * UndoDeletion's own timer commits; this only offers the Undo.
     */
    fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        val job = activity.lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs.toLong()) {
                activity.snackbarHost.showSnackbar(
                    message = activity.getString(message),
                    actionLabel = activity.getString(com.afsh.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
        return { job.cancel() }
    }

    /** A deletion committed: the Recycle Bin shows what is left. */
    fun refreshAfterDeletion() {
        if (visit.isRecycleBin) visit.loader.loadCached {}
    }

    /** Deletes [messages] after the Undo window, or restores them [fromRecycleBin] at once. */
    fun deleteMessages(messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        if (fromRecycleBin) {
            delete(messages, toRecycleBin, fromRecycleBin = true)
            activity.runOnUiThread {
                holder.clearSelection()
                afterDeletion()
            }
            return
        }
        val ids = messages.map { it.getStableId() }.toSet()
        activity.runOnUiThread {
            UndoDeletion.messages.addAll(ids)
            visit.threadItems = visit.repository.items()
            holder.clearSelection()
            visit.publish()
            UndoDeletion.offer(
                activity,
                undo = {
                    UndoDeletion.messages.removeAll(ids)
                    visit.refreshTimeline()
                },
                commit = { delete(messages, toRecycleBin, fromRecycleBin = false) },
                completed = {
                    UndoDeletion.messages.removeAll(ids)
                    if (!activity.isDestroyed && !activity.isFinishing) afterDeletion()
                },
            )
        }
    }

    private fun afterDeletion() {
        if (visit.data.isEmpty) activity.finish() else visit.loader.loadFromProvider {}
    }

    /** Blocking. */
    private fun delete(messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        if (visit.repository.delete(messages, toRecycleBin, fromRecycleBin, visit.conversation)) {
            visit.loader.syncActiveThread()
        }
    }

    /** Deletes the whole conversation for good (deleteConversation does not use the bin), after its Undo. */
    fun askDeleteConversation() {
        if (visit.threadId in UndoDeletion.threads) return
        activity.confirm(
            title = activity.resources.getQuantityString(R.plurals.delete_conversations_title, 1, 1),
            explanation = activity.resources.getQuantityString(R.plurals.delete_conversations_forever_text, 1),
            confirmLabel = activity.getString(R.string.delete_forever),
            destructive = true,
        ) {
            val deletedThread = visit.threadId
            UndoDeletion.threads.add(deletedThread)
            visit.refreshTimeline()
            visit.showFacts()
            UndoDeletion.offer(
                activity,
                undo = {
                    UndoDeletion.threads.remove(deletedThread)
                    visit.refreshTimeline()
                    visit.showFacts()
                },
                commit = {
                    if (visit.isRecycleBin) recycleBin.emptyConversation(deletedThread) else conversations.delete(deletedThread)
                },
                completed = {
                    UndoDeletion.threads.remove(deletedThread)
                    MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                    if (!activity.isDestroyed) activity.finish()
                },
            )
            MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
        }
    }

    fun askRestoreConversation() {
        activity.confirm(
            title = activity.getString(R.string.restore_conversation_title),
            explanation = activity.getString(R.string.restore_conversation_text),
            confirmLabel = activity.getString(R.string.restore),
        ) {
            ensureBackgroundThread {
                recycleBin.restoreConversation(visit.threadId)
                activity.runOnUiThread {
                    MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                    activity.finish()
                }
            }
        }
    }
}

package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getMessages
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.extensions.messagesDB
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fossify.commons.helpers.ensureBackgroundThread
import java.util.concurrent.atomic.AtomicBoolean

/** How far the first import has got. The inbox and search show it until it finishes. */
data class ImportProgress(val phase: Phase, val done: Int, val total: Int) {
    enum class Phase { CONVERSATIONS, MESSAGES }

    val fraction get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * The first import of the phone's messages. Conversations come from the reconcile; this then copies
 * each thread's recent messages into Room, because search reads only Room. It used to run only on the
 * app's very first launch, so a launch that was interrupted or recreated left search empty for every
 * thread the person had not opened yet. Now it runs until it has finished once, and resumes where it
 * stopped, since threads already in Room are skipped.
 */
object InboxImport {
    private val state = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = state.asStateFlow()

    private val running = AtomicBoolean(false)

    /** [done] is the zero-based position of the thread being read. */
    fun reportConversations(done: Int, total: Int) {
        if (done % PROGRESS_STEP == 0 || done == total - 1) {
            state.value = ImportProgress(ImportProgress.Phase.CONVERSATIONS, done + 1, total)
        }
    }

    fun conversationsDone() {
        if (state.value?.phase == ImportProgress.Phase.CONVERSATIONS) state.value = null
    }

    fun importMessagesIfNeeded(context: Context) {
        val app = context.applicationContext
        if (app.config.messagesImported || !running.compareAndSet(false, true)) return
        ensureBackgroundThread {
            try {
                val threadIds = app.conversationsDB.getAll().filter { !it.isScheduled }.map { it.threadId }
                state.value = ImportProgress(ImportProgress.Phase.MESSAGES, 0, threadIds.size)
                // Every write re-runs the inbox query, which reads messages, so write in batches.
                threadIds.chunked(BATCH_THREADS).forEachIndexed { batch, ids ->
                    val fetched = ids
                        .filter { !app.messagesDB.hasThreadMessages(it) }
                        .flatMap { app.getMessages(it, includeScheduledMessages = false) }
                    if (fetched.isNotEmpty()) {
                        app.getMessagesDB().runInTransaction {
                            fetched.chunked(INSERT_CHUNK).forEach { app.messagesDB.insertMessages(*it.toTypedArray()) }
                        }
                    }
                    val done = minOf((batch + 1) * BATCH_THREADS, threadIds.size)
                    state.value = ImportProgress(ImportProgress.Phase.MESSAGES, done, threadIds.size)
                }
                app.config.messagesImported = true
            } catch (_: Exception) {
                // Resumed after the next successful reconcile.
            } finally {
                state.value = null
                running.set(false)
            }
        }
    }

    private const val PROGRESS_STEP = 10
    private const val BATCH_THREADS = 20
    private const val INSERT_CHUNK = 200
}

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

/**
 * How far the first-run setup has got, as one 0–100 percent.
 * Conversation reconcile and message/search indexing share this value; it only rises.
 */
data class ImportProgress(val percent: Int) {
    val fraction get() = percent.coerceIn(0, 100) / 100f
}

/**
 * Maps the two first-run stages onto one bar. Conversation metadata is the lighter share;
 * copying each thread's messages into Room for search is the heavier rest. Percents are
 * integers so the UI can show a stable "42%" without layout jump.
 */
internal object ImportProgressMath {
    /** Share of the bar reserved for reading conversation metadata. */
    const val CONVERSATIONS_SHARE = 35

    fun conversationsPercent(completed: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((completed.coerceIn(0, total)) * CONVERSATIONS_SHARE) / total
    }

    fun messagesPercent(completed: Int, total: Int, afterConversations: Boolean): Int {
        val base = if (afterConversations) CONVERSATIONS_SHARE else 0
        val span = 100 - base
        if (total <= 0) return 100
        return base + ((completed.coerceIn(0, total)) * span) / total
    }

    fun hold(peak: Int, next: Int): Int = maxOf(peak, next.coerceIn(0, 100))
}

/**
 * The first import of the phone's messages. Conversations come from the reconcile; this then copies
 * each thread's recent messages into Room, because search reads only Room. Both stages publish into
 * one [ImportProgress] so the inbox shows a single card until setup finishes. It runs until it has
 * finished once, and resumes where it stopped, since threads already in Room are skipped.
 */
object InboxImport {
    private val state = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = state.asStateFlow()

    private val running = AtomicBoolean(false)
    private var peakPercent = 0
    private var conversationsShareDone = false

    /** [done] is the zero-based position of the thread being read. */
    fun reportConversations(done: Int, total: Int) {
        if (done % PROGRESS_STEP != 0 && done != total - 1) return
        publish(ImportProgressMath.conversationsPercent(done + 1, total))
    }

    /**
     * Conversation metadata is in memory; Room writes and message indexing still follow.
     * Keep the card up at the end of the conversation share — never clear here, or a second
     * progress UI appears when message indexing starts.
     */
    fun conversationsDone() {
        conversationsShareDone = true
        publish(ImportProgressMath.CONVERSATIONS_SHARE)
    }

    /** Reconcile failed before message indexing; drop a held bar so it does not stick. */
    fun abort() {
        if (!running.get()) clear()
    }

    fun importMessagesIfNeeded(context: Context) {
        val app = context.applicationContext
        if (app.config.messagesImported) {
            // Conversations may have held the bar; drop it once messages are already done.
            clear()
            return
        }
        if (!running.compareAndSet(false, true)) return
        ensureBackgroundThread {
            try {
                val threadIds = app.conversationsDB.getAll().filter { !it.isScheduled }.map { it.threadId }
                val afterConversations = conversationsShareDone
                if (threadIds.isEmpty()) {
                    publish(100)
                    app.config.messagesImported = true
                    return@ensureBackgroundThread
                }
                publish(ImportProgressMath.messagesPercent(0, threadIds.size, afterConversations))
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
                    publish(ImportProgressMath.messagesPercent(done, threadIds.size, afterConversations))
                }
                app.config.messagesImported = true
            } catch (_: Exception) {
                // Resumed after the next successful reconcile.
            } finally {
                clear()
                running.set(false)
            }
        }
    }

    private fun publish(percent: Int) {
        val next = ImportProgressMath.hold(peakPercent, percent)
        peakPercent = next
        state.value = ImportProgress(next)
    }

    private fun clear() {
        state.value = null
        peakPercent = 0
        conversationsShareDone = false
    }

    private const val PROGRESS_STEP = 10
    private const val BATCH_THREADS = 20
    private const val INSERT_CHUNK = 200
}

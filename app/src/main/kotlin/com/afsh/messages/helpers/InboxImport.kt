package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getMessages
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.models.Message
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
 * Tracks message-import progress per thread across reconciles.
 *
 * - [failures]: how many times [getMessages] threw (`"threadId:attempts"` prefs). After
 *   [MAX_ATTEMPTS] the thread is abandoned so it cannot restart the whole import forever.
 * - [handled]: thread ids that were read successfully (including empty). Empty threads never
 *   get Room rows, so without this the import would never finish.
 */
internal object ImportFailureLedger {
    const val MAX_ATTEMPTS = 3

    fun parseFailures(stored: Set<String>): Map<Long, Int> = stored.mapNotNull { entry ->
        val parts = entry.split(':', limit = 2)
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val attempts = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
        id to attempts
    }.toMap()

    fun serializeFailures(failures: Map<Long, Int>): Set<String> =
        failures.map { (id, attempts) -> "$id:$attempts" }.toSet()

    fun parseHandled(stored: Set<String>): Set<Long> =
        stored.mapNotNull { it.toLongOrNull() }.toSet()

    fun serializeHandled(handled: Set<Long>): Set<String> =
        handled.map { it.toString() }.toSet()

    fun record(failures: Map<Long, Int>, threadId: Long): Map<Long, Int> {
        val next = (failures[threadId] ?: 0) + 1
        return failures + (threadId to next.coerceAtMost(MAX_ATTEMPTS))
    }

    fun clearFailure(failures: Map<Long, Int>, threadId: Long): Map<Long, Int> =
        if (threadId in failures) failures - threadId else failures

    fun isAbandoned(failures: Map<Long, Int>, threadId: Long): Boolean =
        (failures[threadId] ?: 0) >= MAX_ATTEMPTS

    /** Threads still missing Room messages, not abandoned, and not already read successfully. */
    fun pending(
        threadIds: Collection<Long>,
        hasMessages: (Long) -> Boolean,
        failures: Map<Long, Int>,
        handled: Set<Long>,
    ): List<Long> = threadIds.filter {
        !hasMessages(it) && !isAbandoned(failures, it) && it !in handled
    }

    /** True when every thread has messages, was read successfully, or was abandoned. */
    fun finished(
        threadIds: Collection<Long>,
        hasMessages: (Long) -> Boolean,
        failures: Map<Long, Int>,
        handled: Set<Long>,
    ): Boolean = threadIds.all {
        hasMessages(it) || isAbandoned(failures, it) || it in handled
    }
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
                    app.config.importThreadFailures = emptySet()
                    app.config.importHandledThreads = emptySet()
                    return@ensureBackgroundThread
                }
                var failures = ImportFailureLedger.parseFailures(app.config.importThreadFailures)
                var handled = ImportFailureLedger.parseHandled(app.config.importHandledThreads)
                val pending = ImportFailureLedger.pending(
                    threadIds = threadIds,
                    hasMessages = { app.messagesDB.hasThreadMessages(it) },
                    failures = failures,
                    handled = handled,
                )
                publish(ImportProgressMath.messagesPercent(0, pending.size.coerceAtLeast(1), afterConversations))
                // One bad thread must not abort the rest, and a permanently failing thread must
                // not restart the whole import after every reconcile: only [pending] is visited,
                // failures are counted, and abandoned threads are skipped forever.
                pending.chunked(BATCH_THREADS).forEachIndexed { batch, ids ->
                    val fetched = ArrayList<Message>()
                    ids.forEach { threadId ->
                        try {
                            val messages = app.getMessages(threadId, includeScheduledMessages = false)
                            fetched += messages
                            failures = ImportFailureLedger.clearFailure(failures, threadId)
                            handled = handled + threadId
                        } catch (_: Exception) {
                            failures = ImportFailureLedger.record(failures, threadId)
                        }
                    }
                    if (fetched.isNotEmpty()) {
                        try {
                            app.getMessagesDB().runInTransaction {
                                fetched.chunked(INSERT_CHUNK).forEach { app.messagesDB.insertMessages(*it.toTypedArray()) }
                            }
                            ConversationPreview.refresh(app, fetched.map { it.threadId })
                        } catch (_: Exception) {
                            // Rows were not written; drop those ids from handled so the next run retries.
                            val failedIds = fetched.map { it.threadId }.toSet()
                            handled = handled - failedIds
                        }
                    }
                    val done = minOf((batch + 1) * BATCH_THREADS, pending.size)
                    publish(ImportProgressMath.messagesPercent(done, pending.size, afterConversations))
                }
                app.config.importThreadFailures = ImportFailureLedger.serializeFailures(failures)
                app.config.importHandledThreads = ImportFailureLedger.serializeHandled(handled)
                if (
                    ImportFailureLedger.finished(
                        threadIds = threadIds,
                        hasMessages = { app.messagesDB.hasThreadMessages(it) },
                        failures = failures,
                        handled = handled,
                    )
                ) {
                    app.config.messagesImported = true
                    app.config.importThreadFailures = emptySet()
                    app.config.importHandledThreads = emptySet()
                    publish(100)
                }
            } catch (_: Exception) {
                // Listing threads failed; resume after the next successful reconcile.
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

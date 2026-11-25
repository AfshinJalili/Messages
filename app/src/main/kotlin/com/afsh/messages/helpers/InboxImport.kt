package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.models.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.afsh.commons.helpers.ensureBackgroundThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
 * Tracks message-import progress per thread across reconciles. A thread is cached once Room holds
 * its newest message ([ConversationRepository.holdsNewestMessage]), so a thread Room knows only
 * older messages of is imported again.
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

    /** Threads not cached, not abandoned, and not already read successfully. */
    fun pending(
        threadIds: Collection<Long>,
        cached: (Long) -> Boolean,
        failures: Map<Long, Int>,
        handled: Set<Long>,
    ): List<Long> = threadIds.filter {
        !cached(it) && !isAbandoned(failures, it) && it !in handled
    }

    /** True when every thread is cached, was read successfully, or was abandoned. */
    fun finished(
        threadIds: Collection<Long>,
        cached: (Long) -> Boolean,
        failures: Map<Long, Int>,
        handled: Set<Long>,
    ): Boolean = threadIds.all {
        cached(it) || isAbandoned(failures, it) || it in handled
    }
}

/**
 * The import of the phone's messages. Conversations come from the reconcile; this then copies each
 * stale thread's recent messages into Room, because search reads only Room. Both stages publish into
 * one [ImportProgress] so the inbox shows a single card until setup finishes. It runs until it has
 * finished once, and resumes where it stopped, since cached threads are skipped. Becoming the
 * default SMS app again [reopen]s it for the messages another app saw meanwhile.
 */
object InboxImport {
    private val state = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = state.asStateFlow()

    private val running = AtomicBoolean(false)
    // Bumped by [reopen]; a run started before it must not write its ledger over the fresh one.
    // [ledgerLock] makes that check and the ledger writes one step against [reopen].
    private val generation = AtomicInteger(0)
    private val ledgerLock = Any()
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

    /**
     * Another app was the default SMS app, so threads Room has may have newer messages. The next
     * import after a reconcile visits every stale thread again, from a fresh ledger. Nothing happens
     * when the ledger is already fresh, as on first run, where Android's broadcast can land while
     * the first import runs: that run then keeps its ledger and its progress.
     */
    fun reopen(context: Context) {
        val config = context.applicationContext.config
        synchronized(ledgerLock) {
            val fresh = !config.messagesImported &&
                config.importThreadFailures.isEmpty() &&
                config.importHandledThreads.isEmpty()
            if (fresh) return
            generation.incrementAndGet()
            config.messagesImported = false
            config.importThreadFailures = emptySet()
            config.importHandledThreads = emptySet()
        }
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
            val startedIn = generation.get()
            try {
                val conversations = app.conversationsDB.getAll().filter { !it.isScheduled }.associateBy { it.threadId }
                val threadIds = conversations.keys
                val repository = ConversationRepository.from(app)
                val cached = { threadId: Long -> repository.holdsNewestMessage(conversations.getValue(threadId)) }
                val afterConversations = conversationsShareDone
                if (threadIds.isEmpty()) {
                    synchronized(ledgerLock) {
                        if (generation.get() != startedIn) return@ensureBackgroundThread
                        app.config.messagesImported = true
                        app.config.importThreadFailures = emptySet()
                        app.config.importHandledThreads = emptySet()
                    }
                    if (afterConversations) publish(100)
                    return@ensureBackgroundThread
                }
                var failures = ImportFailureLedger.parseFailures(app.config.importThreadFailures)
                var handled = ImportFailureLedger.parseHandled(app.config.importHandledThreads)
                val pending = ImportFailureLedger.pending(
                    threadIds = threadIds,
                    cached = cached,
                    failures = failures,
                    handled = handled,
                )
                // A held conversations bar continues; otherwise a bar shows only when there is
                // something to copy, so a resume with nothing stale does not flash one.
                val showProgress = afterConversations || pending.isNotEmpty()
                if (showProgress) {
                    publish(ImportProgressMath.messagesPercent(0, pending.size.coerceAtLeast(1), afterConversations))
                }
                // One bad thread must not abort the rest, and a permanently failing thread must
                // not restart the whole import after every reconcile: only [pending] is visited,
                // failures are counted, and abandoned threads are skipped forever.
                pending.chunked(BATCH_THREADS).forEachIndexed { batch, ids ->
                    val fetched = ArrayList<Message>()
                    ids.forEach { threadId ->
                        try {
                            val messages = TelephonyReader(app).getMessages(threadId, includeScheduledMessages = false)
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
                // Threads outside [pending] were cached, handled or abandoned when it was built, and
                // nothing here undoes that, so only [pending] needs checking again.
                val finished = ImportFailureLedger.finished(
                    threadIds = pending,
                    cached = cached,
                    failures = failures,
                    handled = handled,
                )
                synchronized(ledgerLock) {
                    if (generation.get() != startedIn) return@ensureBackgroundThread
                    app.config.messagesImported = finished
                    app.config.importThreadFailures =
                        if (finished) emptySet() else ImportFailureLedger.serializeFailures(failures)
                    app.config.importHandledThreads =
                        if (finished) emptySet() else ImportFailureLedger.serializeHandled(handled)
                }
                if (finished && showProgress) publish(100)
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

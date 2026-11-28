package com.afsh.messages.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Room is the inbox source of truth. This schedules provider reconciliation and applies
 * immediate row updates for sends/receives without waiting for Telephony.
 */
object InboxRepository {

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    // Main thread only: a pass starts on it, and its outcome is posted back to it.
    private var reconciling = false
    private var reconcilePending = false

    /** A forced pass was asked for; set from any thread, taken by the next pass. */
    private val pendingForce = AtomicBoolean(false)
    private var reconcileListener: ((ReconcileStatus) -> Unit)? = null

    private val reconcileRunnable = Runnable {
        val context = appContext ?: return@Runnable
        runProviderReconcile(context, force = pendingForce.getAndSet(false))
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setReconcileListener(listener: ((ReconcileStatus) -> Unit)?) {
        reconcileListener = listener
    }

    /**
     * This app just became the default SMS app. Messages sent or received meanwhile are in Telephony
     * but not Room, and the dirty-check misses new messages in threads Room already has, so the next
     * reconcile (launch or resume) runs forced, and the message import runs again for threads whose
     * newest message Room lacks. Schedules nothing: the caller may be a receiver.
     */
    fun requestForceReconcile(context: Context) {
        val app = resolveContext(context) ?: return
        app.config.forceReconcilePending = true
        InboxImport.reopen(app)
    }

    /** 73's Retry: reconcile with the provider now rather than after the debounce. */
    fun retry(context: Context) = scheduleProviderReconcile(context, immediate = true, force = true)

    /** Lightweight inbox refresh: sync unread badges from Telephony, then debounce a reconcile. */
    fun refreshInbox(context: Context? = null, reconcile: Boolean = true) {
        val app = resolveContext(context) ?: return
        refreshUnreadCounts(app)
        if (reconcile) {
            // Dirty-check: Room already holds structural edits; only pull Telephony when it drifted.
            scheduleProviderReconcile(app, force = false)
        }
    }

    /**
     * @param force when false, a cheap thread-count check can replace the full
     * [ConversationRepository.conversations] pass with [InboxReconciler.syncRecentWindow].
     * Launch and resume use force=false so a warm Room DB paints without a full Telephony pass.
     * Explicit repairs (retry, spam backfill) pass force=true. A [requestForceReconcile] forces the
     * next run whatever this says.
     */
    fun scheduleProviderReconcile(
        context: Context? = null,
        immediate: Boolean = false,
        force: Boolean = true,
    ) {
        val app = resolveContext(context) ?: return
        if (force) pendingForce.set(true)
        handler.removeCallbacks(reconcileRunnable)
        if (immediate) {
            handler.post(reconcileRunnable)
        } else {
            handler.postDelayed(reconcileRunnable, RECONCILE_DEBOUNCE_MS)
        }
    }

    /**
     * Cold-start path: refresh unread badges, then reconcile fully only when Room is empty or its
     * thread count disagrees with Telephony; otherwise re-read only the newest threads.
     */
    fun scheduleLaunchReconcile(context: Context) {
        val app = resolveContext(context) ?: return
        refreshUnreadCounts(app)
        scheduleProviderReconcile(app, immediate = true, force = false)
    }

    fun refreshUnreadCounts(context: Context? = null, threadIds: Collection<Long> = emptyList()) {
        val app = resolveContext(context) ?: return
        ensureBackgroundThread { ConversationRepository.from(app).syncUnreadCounts(threadIds) }
    }

    fun bumpAfterOutgoing(context: Context, threadId: Long, snippet: String) {
        val app = resolveContext(context) ?: return
        ensureBackgroundThread { ConversationRepository.from(app).bumpAfterOutgoing(threadId, snippet) }
    }

    private fun runProviderReconcile(context: Context, force: Boolean) {
        if (reconciling) {
            reconcilePending = true
            if (force) pendingForce.set(true)
            return
        }
        reconciling = true
        ensureBackgroundThread {
            // Cleared only after a successful pass, so a failed or killed run leaves it for the next one.
            val requested = context.config.forceReconcilePending
            val pass = providerReconcilePass(force, requested) { needsFullReconcile(context) }
            if (pass == ProviderReconcilePass.RECENT_WINDOW) {
                val synced = try {
                    InboxReconciler.syncRecentWindow(context)
                    true
                } catch (_: Exception) {
                    false
                }
                handler.post {
                    // An import stopped mid-way (killed, or reopened) resumes without a full pass.
                    if (synced) InboxImport.importMessagesIfNeeded(context)
                    reconciling = false
                    reconcileListener?.invoke(if (synced) ReconcileStatus.DONE else ReconcileStatus.FAILED)
                    drainPending(context)
                }
                return@ensureBackgroundThread
            }
            handler.post {
                reconcileListener?.invoke(ReconcileStatus.RUNNING)
                // The reconciler calls back on its worker; the outcome is the main thread's, as above.
                InboxReconciler.reconcile(
                    context = context,
                    forced = requested,
                    onFinished = {
                        handler.post {
                            if (requested) context.config.forceReconcilePending = false
                            refreshUnreadCounts(context)
                            InboxImport.importMessagesIfNeeded(context)
                            reconciling = false
                            reconcileListener?.invoke(ReconcileStatus.DONE)
                            drainPending(context)
                        }
                    },
                    onFailed = {
                        handler.post {
                            reconciling = false
                            reconcileListener?.invoke(ReconcileStatus.FAILED)
                            drainPending(context)
                        }
                    },
                )
            }
        }
    }

    /** Main thread. Starts the pass asked for while one ran; [pendingForce] still says whether it is forced. */
    private fun drainPending(context: Context) {
        if (!reconcilePending) return
        reconcilePending = false
        scheduleProviderReconcile(context, immediate = true, force = false)
    }

    /** See [ConversationRepository.needsFullReconcile]. */
    internal fun needsFullReconcile(context: Context): Boolean =
        ConversationRepository.from(context).needsFullReconcile()

    private fun resolveContext(context: Context?): Context? {
        return (context ?: appContext)?.applicationContext?.also { appContext = it }
    }

    private const val RECONCILE_DEBOUNCE_MS = 400L
}

/** How much of Telephony a provider reconcile reads. */
internal enum class ProviderReconcilePass {
    /** Room's thread count matches Telephony: only the newest threads are re-read. */
    RECENT_WINDOW,

    /** Every thread: inserts, updates and removals. */
    FULL,
}

/**
 * A forced or requested run, or a Room that [dirty] finds drifted from Telephony, needs the
 * [ProviderReconcilePass.FULL] pass. The dirty-check walks every thread, so a forced run skips it.
 */
internal fun providerReconcilePass(force: Boolean, requested: Boolean, dirty: () -> Boolean): ProviderReconcilePass =
    if (force || requested || dirty()) ProviderReconcilePass.FULL else ProviderReconcilePass.RECENT_WINDOW

/** Where a provider reconcile is; the inbox shows 73's notice after FAILED until a later DONE. */
enum class ReconcileStatus { RUNNING, DONE, FAILED }

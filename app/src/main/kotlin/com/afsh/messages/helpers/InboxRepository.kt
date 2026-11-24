package com.afsh.messages.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config

/**
 * Room is the inbox source of truth. This schedules provider reconciliation and applies
 * immediate row updates for sends/receives without waiting for Telephony.
 */
object InboxRepository {

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var reconciling = false
    private var reconcilePending = false
    private var pendingForce = false
    private var reconcileListener: ((ReconcileStatus) -> Unit)? = null

    private val reconcileRunnable = Runnable {
        val context = appContext ?: return@Runnable
        val force = pendingForce
        pendingForce = false
        runProviderReconcile(context, force = force)
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
     * reconcile (launch or resume) runs forced. Schedules nothing: the caller may be a receiver.
     */
    fun requestForceReconcile(context: Context) {
        val app = resolveContext(context) ?: return
        app.config.forceReconcilePending = true
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
     * @param force when false, a cheap thread-count / latest-date check can skip [ConversationRepository.conversations].
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
        pendingForce = pendingForce || force
        handler.removeCallbacks(reconcileRunnable)
        if (immediate) {
            handler.post(reconcileRunnable)
        } else {
            handler.postDelayed(reconcileRunnable, RECONCILE_DEBOUNCE_MS)
        }
    }

    /**
     * Cold-start path: refresh unread badges, then reconcile only when Room is empty or the
     * Telephony digest disagrees with Room.
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
            pendingForce = pendingForce || force
            return
        }
        reconciling = true
        ensureBackgroundThread {
            // Cleared only after a successful pass, so a failed or killed run leaves it for the next one.
            val requested = context.config.forceReconcilePending
            val needsFull = force || requested || needsFullReconcile(context)
            handler.post {
                if (!needsFull) {
                    reconciling = false
                    reconcileListener?.invoke(ReconcileStatus.DONE)
                    drainPending(context)
                    return@post
                }
                reconcileListener?.invoke(ReconcileStatus.RUNNING)
                InboxReconciler.reconcile(
                    context = context,
                    onFinished = {
                        if (requested) context.config.forceReconcilePending = false
                        refreshUnreadCounts(context)
                        InboxImport.importMessagesIfNeeded(context)
                        reconciling = false
                        reconcileListener?.invoke(ReconcileStatus.DONE)
                        drainPending(context)
                    },
                    onFailed = {
                        reconciling = false
                        reconcileListener?.invoke(ReconcileStatus.FAILED)
                        drainPending(context)
                    },
                )
            }
        }
    }

    private fun drainPending(context: Context) {
        if (!reconcilePending) return
        reconcilePending = false
        val force = pendingForce
        pendingForce = false
        scheduleProviderReconcile(context, immediate = true, force = force)
    }

    /** True when Room has no rows (first import) or Telephony count/latest-date disagree with Room. */
    internal fun needsFullReconcile(context: Context): Boolean =
        ConversationRepository.from(context).needsFullReconcile()

    private fun resolveContext(context: Context?): Context? {
        return (context ?: appContext)?.applicationContext?.also { appContext = it }
    }

    private const val RECONCILE_DEBOUNCE_MS = 400L
}

/** Where a provider reconcile is; the inbox shows 73's notice after FAILED until a later DONE. */
enum class ReconcileStatus { RUNNING, DONE, FAILED }

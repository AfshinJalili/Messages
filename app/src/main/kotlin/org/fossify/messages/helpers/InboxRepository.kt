package org.fossify.messages.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getUnreadCountsByThread

/**
 * Room is the inbox source of truth. This schedules provider reconciliation and applies
 * immediate row updates for sends/receives without waiting for Telephony.
 */
object InboxRepository {

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var reconciling = false
    private var reconcilePending = false
    private var reconcileListener: ((Boolean) -> Unit)? = null

    private val reconcileRunnable = Runnable {
        val context = appContext ?: return@Runnable
        runProviderReconcile(context)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setReconcileListener(listener: ((Boolean) -> Unit)?) {
        reconcileListener = listener
    }

    /** Lightweight inbox refresh: sync unread badges from Telephony, then debounce a full reconcile. */
    fun refreshInbox(context: Context? = null, reconcile: Boolean = true) {
        val app = resolveContext(context) ?: return
        refreshUnreadCounts(app)
        if (reconcile) {
            scheduleProviderReconcile(app)
        }
    }

    fun scheduleProviderReconcile(context: Context? = null, immediate: Boolean = false) {
        val app = resolveContext(context) ?: return
        handler.removeCallbacks(reconcileRunnable)
        if (immediate) {
            handler.post(reconcileRunnable)
        } else {
            handler.postDelayed(reconcileRunnable, RECONCILE_DEBOUNCE_MS)
        }
    }

    fun refreshUnreadCounts(context: Context? = null, threadIds: Collection<Long> = emptyList()) {
        val app = resolveContext(context) ?: return
        ensureBackgroundThread {
            val counts = app.getUnreadCountsByThread()
            val targets = if (threadIds.isEmpty()) {
                app.conversationsDB.getNonArchived().map { it.threadId }
            } else {
                threadIds
            }
            targets.forEach { threadId ->
                val count = counts[threadId] ?: 0
                app.conversationsDB.updateUnreadCount(threadId, count)
            }
        }
    }

    fun bumpAfterOutgoing(context: Context, threadId: Long, snippet: String) {
        val app = resolveContext(context) ?: return
        ensureBackgroundThread {
            val now = (System.currentTimeMillis() / 1000).toInt()
            val existing = app.conversationsDB.getConversationWithThreadId(threadId)
            val row = existing ?: app.getConversations(threadId).firstOrNull()
            if (row != null) {
                app.conversationsDB.insertOrUpdate(
                    row.copy(
                        snippet = snippet,
                        date = now,
                        read = true,
                        unreadCount = 0,
                    )
                )
            }
        }
    }

    private fun runProviderReconcile(context: Context) {
        if (reconciling) {
            reconcilePending = true
            return
        }
        reconciling = true
        reconcileListener?.invoke(true)
        InboxReconciler.reconcile(
            context = context,
            onFinished = {
                refreshUnreadCounts(context)
                reconciling = false
                reconcileListener?.invoke(false)
                if (reconcilePending) {
                    reconcilePending = false
                    scheduleProviderReconcile(context, immediate = true)
                }
            },
            onFailed = {
                reconciling = false
                reconcileListener?.invoke(false)
                if (reconcilePending) {
                    reconcilePending = false
                    scheduleProviderReconcile(context, immediate = true)
                }
            },
        )
    }

    private fun resolveContext(context: Context?): Context? {
        return (context ?: appContext)?.applicationContext?.also { appContext = it }
    }

    private const val RECONCILE_DEBOUNCE_MS = 400L
}

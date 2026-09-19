package org.fossify.messages.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getUnreadCountsByThread

/**
 * Room is the inbox source of truth. This schedules provider reconciliation and applies
 * immediate row updates for sends/receives without waiting for Telephony.
 */
object InboxRepository {

    private val handler = Handler(Looper.getMainLooper())
    private var reconciling = false
    private var reconcilePending = false
    private var reconcileListener: ((Boolean) -> Unit)? = null

    fun setReconcileListener(listener: ((Boolean) -> Unit)?) {
        reconcileListener = listener
    }

    fun scheduleProviderReconcile(context: Context, immediate: Boolean = false) {
        val app = context.applicationContext
        handler.removeCallbacksAndMessages(null)
        val run = Runnable { runProviderReconcile(app) }
        if (immediate) {
            handler.post(run)
        } else {
            handler.postDelayed(run, RECONCILE_DEBOUNCE_MS)
        }
    }

    fun refreshUnreadCounts(context: Context, threadIds: Collection<Long> = emptyList()) {
        ensureBackgroundThread {
            val app = context.applicationContext
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
        ensureBackgroundThread {
            val app = context.applicationContext
            val now = (System.currentTimeMillis() / 1000).toInt()
            val existing = app.conversationsDB.getConversationWithThreadId(threadId)
            if (existing != null) {
                app.conversationsDB.insertOrUpdate(
                    existing.copy(
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
                reconcilePending = false
            },
        )
    }

    private const val RECONCILE_DEBOUNCE_MS = 400L
}

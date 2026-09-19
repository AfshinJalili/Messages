package org.fossify.messages.helpers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import java.util.concurrent.ConcurrentHashMap

/** Keep refreshes from restoring rows during the undo window and provider write. */
object UndoDeletion {
    var version = 0
        private set
    val threads: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    val messages: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    val spam: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val handler = Handler(Looper.getMainLooper())
    private val batches = mutableMapOf<Int, Batch>()

    private class Action(val undo: () -> Unit, val commit: () -> Unit, val completed: () -> Unit)
    private class Batch(val actions: MutableList<Action>, val timer: Runnable, val deadline: Long) {
        var snackbar: Snackbar? = null
        var activity: java.lang.ref.WeakReference<Activity>? = null
    }

    fun offer(activity: Activity, undo: () -> Unit, commit: () -> Unit, completed: () -> Unit = {}) {
        activity.runOnUiThread {
            version++
            val previous = batches.remove(activity.taskId)
            previous?.let { handler.removeCallbacks(it.timer); it.snackbar?.dismiss() }
            val actions = previous?.actions ?: mutableListOf()
            actions.add(Action(undo, commit, completed))
            val taskId = activity.taskId
            val timer = Runnable {
                val batch = batches.remove(taskId)
                batch?.snackbar?.dismiss()
                ensureBackgroundThread {
                    actions.forEach { action ->
                        try {
                            action.commit()
                            handler.post {
                                version++
                                action.completed()
                                (batch?.activity?.get() as? org.fossify.messages.activities.SimpleActivity)?.refreshAfterDeletion()
                                refreshConversations()
                                refreshMessages()
                            }
                        } catch (e: Exception) {
                            handler.post {
                                version++
                                action.undo()
                                activity.showErrorToast(e)
                                (batch?.activity?.get() as? org.fossify.messages.activities.SimpleActivity)?.refreshAfterDeletion()
                                refreshConversations()
                                refreshMessages()
                            }
                        }
                    }
                }
            }
            batches[taskId] = Batch(actions, timer, SystemClock.uptimeMillis() + 5000)
            handler.postDelayed(timer, 5000)
            attach(activity)
        }
    }

    /** Preserve access to Undo when navigating or recreating an activity. */
    fun attach(activity: Activity) {
        val batch = batches[activity.taskId] ?: return
        val remaining = (batch.deadline - SystemClock.uptimeMillis()).toInt()
        if (remaining <= 0) return
        batch.activity = java.lang.ref.WeakReference(activity)
        batch.snackbar?.dismiss()
        batch.snackbar = Snackbar.make(activity.findViewById<View>(android.R.id.content), R.string.items_deleted, remaining)
            .setAction(org.fossify.commons.R.string.undo) {
                if (batches[activity.taskId] !== batch) return@setAction
                batches.remove(activity.taskId)
                handler.removeCallbacks(batch.timer)
                version++
                batch.actions.asReversed().forEach { it.undo() }
                (activity as? org.fossify.messages.activities.SimpleActivity)?.refreshAfterDeletion()
                refreshConversations()
                refreshMessages()
            }.also { it.show() }
    }
}

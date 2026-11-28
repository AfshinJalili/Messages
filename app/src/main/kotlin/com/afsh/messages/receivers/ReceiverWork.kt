package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import com.afsh.commons.helpers.ensureBackgroundThread

/**
 * Runs [work] off the main thread and holds the broadcast until it ends. Android may kill a process
 * whose receiver has returned, so work started without this can stop halfway.
 */
internal fun BroadcastReceiver.goAsyncInBackground(work: () -> Unit) {
    val pending = goAsync()
    ensureBackgroundThread {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}

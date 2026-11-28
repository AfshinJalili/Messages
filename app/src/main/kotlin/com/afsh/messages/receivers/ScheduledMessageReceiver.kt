package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.helpers.SCHEDULED_MESSAGE_ID
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.messaging.ScheduledMessageSender
import kotlin.time.Duration.Companion.minutes

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "simple.messenger:scheduled.message.receiver"
        )
        wakelock.acquire(1.minutes.inWholeMilliseconds)

        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                val messageId = intent.getLongExtra(SCHEDULED_MESSAGE_ID, 0L)
                ScheduledMessageSender.from(context).sendDue(threadId, messageId)
            } finally {
                try {
                    if (wakelock.isHeld) wakelock.release()
                } catch (_: Exception) {
                }

                pendingResult.finish()
            }
        }
    }
}

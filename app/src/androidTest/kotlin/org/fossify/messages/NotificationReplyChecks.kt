package org.fossify.messages

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER
import org.fossify.messages.helpers.NotificationHelper
import org.fossify.messages.helpers.REPLY
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReplyChecks {
    @Test
    fun senderOnlyNotificationHasQuickReply() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String?, mode: Int) =
                target.getSharedPreferences("notification-reply-check", mode)
        }
        context.config.lockScreenVisibilitySetting = LOCK_SCREEN_SENDER
        val threadId = Long.MAX_VALUE - 76543
        val manager = target.getSystemService(NotificationManager::class.java)
        val keep = InstrumentationRegistry.getArguments().getString("keep_notification") == "true"
        try {
            NotificationHelper(context).showMessageNotification(
                messageId = 0,
                address = "5550100",
                body = "Quick reply verification. No SMS was sent.",
                threadId = threadId,
                bitmap = null,
                sender = "Quick reply check"
            )
            val deadline = SystemClock.uptimeMillis() + 5000
            while (manager.activeNotifications.none { it.id == threadId.hashCode() } && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            val notification = manager.activeNotifications.first { it.id == threadId.hashCode() }.notification
            check(notification.actions.orEmpty().any { action ->
                action.remoteInputs.orEmpty().any { it.resultKey == REPLY && it.allowFreeFormInput }
            }) { "Sender-only notification is missing its quick reply input" }
            check(context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER)
        } finally {
            if (!keep) {
                manager.cancel(threadId.hashCode())
                ShortcutManagerCompat.removeDynamicShortcuts(target, listOf(threadId.toString()))
                ShortcutManagerCompat.removeLongLivedShortcuts(target, listOf(threadId.toString()))
            }
            target.deleteSharedPreferences("notification-reply-check")
        }
    }
}

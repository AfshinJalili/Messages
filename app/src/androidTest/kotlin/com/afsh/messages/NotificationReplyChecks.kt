package com.afsh.messages

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.LOCK_SCREEN_SENDER
import com.afsh.messages.helpers.NotificationHelper
import com.afsh.messages.helpers.REPLY
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReplyChecks {
    /** The lock-screen setting is written to throwaway settings; the app's real one stays as it is. */
    @Test
    fun senderOnlyNotificationHasQuickReply() = IsolatedPreferences("notification-reply-check").use { prefs ->
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = prefs.context
        context.config.lockScreenVisibilitySetting = LOCK_SCREEN_SENDER
        val threadId = Long.MAX_VALUE - 76543
        val manager = target.getSystemService(NotificationManager::class.java)
        val keep = InstrumentationRegistry.getArguments().getString("keep_notification") == "true"
        val body = "Quick reply verification. No SMS was sent."
        try {
            NotificationHelper(context).showMessageNotification(
                messageId = 0,
                isMms = false,
                address = "5550100",
                body = body,
                threadId = threadId,
                bitmap = null,
                sender = "Quick reply check",
                continuing = true,
                stillAlerts = { true },
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
            // Sender only: the sender and "New message", never the body, whatever Android's lock-screen
            // option; where Android hides sensitive content it shows the public version, which says the same.
            check(notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Quick reply check")
            check(notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == target.getString(R.string.new_message))
            check(!notification.extras.containsKey(Notification.EXTRA_MESSAGES)) { "The notification shows the message" }
            check(notification.visibility == Notification.VISIBILITY_PRIVATE) { "Message notification is not private" }
            val public = checkNotNull(notification.publicVersion) { "Message notification has no public version" }
            check(public.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Quick reply check")
            check(public.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == target.getString(R.string.new_message))
            check(!public.extras.containsKey(Notification.EXTRA_MESSAGES) && !public.extras.containsKey(Notification.EXTRA_BIG_TEXT)) {
                "The public version shows the message"
            }
        } finally {
            if (!keep) {
                manager.cancel(threadId.hashCode())
                ShortcutManagerCompat.removeDynamicShortcuts(target, listOf(threadId.toString()))
                ShortcutManagerCompat.removeLongLivedShortcuts(target, listOf(threadId.toString()))
            }
        }
    }
}

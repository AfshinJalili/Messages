package com.afsh.messages

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.activities.ThreadActivity
import com.afsh.messages.helpers.IS_RECYCLE_BIN
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.helpers.THREAD_TITLE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagingEventsChecks {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(50)
        }
        return predicate()
    }

    private fun cleanupThreadNotification(threadId: Long) {
        manager.cancel(threadId.hashCode())
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(threadId.toString()))
        ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(threadId.toString()))
    }

    private fun incoming(threadId: Long, body: String = "Check body") = MessagingEvent.IncomingMessage(
        threadId = threadId,
        messageId = 0L,
        isMms = false,
        isSpam = false,
        address = "5550199",
        senderName = "Event Check",
        body = body,
    )

    @Test
    fun incomingEventSuppressedWhileViewingConversationAndAlertsForOtherThread() {
        val openThreadId = Long.MAX_VALUE - 41001
        val otherThreadId = Long.MAX_VALUE - 41002
        val room = RoomOnlyThread(
            context,
            fixtureConversation(openThreadId, "Active Thread", snippet = "Hi", phone = "555019901", scheduled = true),
        )
        room.insert()
        try {
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, openThreadId)
                .putExtra(THREAD_NUMBER, "555019901")
                .putExtra(THREAD_TITLE, "Active Thread")
            ActivityScenario.launch<ThreadActivity>(intent).use {
                instrumentation.waitForIdleSync()

                // Active thread must be suppressed.
                MessagingEvents.publish(incoming(openThreadId, "Suppressed active message"))
                SystemClock.sleep(400)
                assertFalse(
                    "Active conversation must not post a tray notification",
                    manager.activeNotifications.any { it.id == openThreadId.hashCode() },
                )

                // A different thread must still alert.
                MessagingEvents.publish(incoming(otherThreadId, "Alert for other thread"))
                assertTrue(
                    "Other conversation must still post a tray notification",
                    awaitCondition { manager.activeNotifications.any { it.id == otherThreadId.hashCode() } },
                )
            }
        } finally {
            room.delete()
            cleanupThreadNotification(openThreadId)
            cleanupThreadNotification(otherThreadId)
        }
    }

    @Test
    fun enteringConversationClearsExistingTrayNotification() {
        val threadId = Long.MAX_VALUE - 41003
        val room = RoomOnlyThread(
            context,
            fixtureConversation(threadId, "Pending Alert", snippet = "Hi", phone = "555019903", scheduled = true),
        )
        room.insert()
        try {
            // Publish while backgrounded so a tray notification appears first.
            MessagingEvents.publish(incoming(threadId, "Arrived while away"))
            assertTrue(
                "Background incoming message must post a tray notification",
                awaitCondition { manager.activeNotifications.any { it.id == threadId.hashCode() } },
            )

            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, threadId)
                .putExtra(THREAD_NUMBER, "555019903")
                .putExtra(THREAD_TITLE, "Pending Alert")
            ActivityScenario.launch<ThreadActivity>(intent).use {
                assertTrue(
                    "Entering the conversation must clear its tray notification",
                    awaitCondition { manager.activeNotifications.none { it.id == threadId.hashCode() } },
                )
            }
        } finally {
            room.delete()
            cleanupThreadNotification(threadId)
        }
    }

    @Test
    fun recycleBinConversationDoesNotSuppressIncomingNotification() {
        val threadId = Long.MAX_VALUE - 41004
        val room = RoomOnlyThread(
            context,
            fixtureConversation(threadId, "Recycled Thread", snippet = "Deleted", phone = "555019904", scheduled = true),
        )
        room.insert()
        try {
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, threadId)
                .putExtra(THREAD_NUMBER, "555019904")
                .putExtra(THREAD_TITLE, "Recycled Thread")
                .putExtra(IS_RECYCLE_BIN, true)
            ActivityScenario.launch<ThreadActivity>(intent).use {
                instrumentation.waitForIdleSync()
                MessagingEvents.publish(incoming(threadId, "Live message while in Recycle Bin"))
                assertTrue(
                    "Recycle Bin view must not suppress live incoming notifications",
                    awaitCondition { manager.activeNotifications.any { it.id == threadId.hashCode() } },
                )
            }
        } finally {
            room.delete()
            cleanupThreadNotification(threadId)
        }
    }

    @Test
    fun sendFailureShowsInThreadSnackbarWhenViewingConversation() {
        val threadId = Long.MAX_VALUE - 41005
        val room = RoomOnlyThread(
            context,
            fixtureConversation(threadId, "Failure Thread", snippet = "Hi", phone = "555019905", scheduled = true),
        )
        room.insert()
        try {
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, threadId)
                .putExtra(THREAD_NUMBER, "555019905")
                .putExtra(THREAD_TITLE, "Failure Thread")
            ActivityScenario.launch<ThreadActivity>(intent).use { scenario ->
                instrumentation.waitForIdleSync()
                val reason = "No service"
                MessagingEvents.publish(
                    MessagingEvent.SendFailed(
                        threadId = threadId,
                        messageId = 1L,
                        isMms = false,
                        recipientName = "Failure Thread",
                        reason = reason,
                    )
                )
                assertTrue(
                    "Active conversation must show the send failure reason in its snackbar",
                    awaitCondition {
                        var shown: String? = null
                        scenario.onActivity { shown = it.snackbarHost.currentSnackbarData?.visuals?.message }
                        shown == reason
                    },
                )
            }
        } finally {
            room.delete()
            cleanupThreadNotification(threadId)
        }
    }
}

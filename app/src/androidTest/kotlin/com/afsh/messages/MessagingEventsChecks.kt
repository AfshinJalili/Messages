package com.afsh.messages

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.commons.extensions.appLockManager
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.helpers.PROTECTION_PIN
import com.afsh.messages.activities.AppLockActivity
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
import java.math.BigInteger
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class MessagingEventsChecks {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun awaitNotification(threadId: Long, expectedPresent: Boolean, timeoutMs: Long = 5_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val present = manager.activeNotifications.any { it.id == threadId.hashCode() }
            if (present == expectedPresent) return true
            SystemClock.sleep(50)
        }
        return manager.activeNotifications.any { it.id == threadId.hashCode() } == expectedPresent
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

    private fun threadIntent(
        threadId: Long,
        phone: String,
        title: String,
        recycleBin: Boolean = false,
    ): Intent = Intent(context, ThreadActivity::class.java)
        .putExtra(THREAD_ID, threadId)
        .putExtra(THREAD_NUMBER, phone)
        .putExtra(THREAD_TITLE, title)
        .apply { if (recycleBin) putExtra(IS_RECYCLE_BIN, true) }

    private fun withSyntheticThread(
        threadId: Long,
        phone: String,
        title: String,
        extraCleanupIds: List<Long> = emptyList(),
        block: () -> Unit,
    ) {
        val room = RoomOnlyThread(
            context,
            fixtureConversation(threadId, title, snippet = "Hi", phone = phone, scheduled = true),
        )
        room.insert()
        try {
            block()
        } finally {
            room.delete()
            cleanupThreadNotification(threadId)
            extraCleanupIds.forEach(::cleanupThreadNotification)
        }
    }

    @Test
    fun incomingEventSuppressedWhileViewingConversationAndAlertsForOtherThread() {
        val openThreadId = Long.MAX_VALUE - 41001
        val otherThreadId = Long.MAX_VALUE - 41002
        withSyntheticThread(openThreadId, "555019901", "Active Thread", listOf(otherThreadId)) {
            ActivityScenario.launch<ThreadActivity>(threadIntent(openThreadId, "555019901", "Active Thread")).use {
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
                    awaitNotification(otherThreadId, expectedPresent = true),
                )
            }
        }
    }

    @Test
    fun enteringConversationClearsExistingTrayNotification() {
        val threadId = Long.MAX_VALUE - 41003
        withSyntheticThread(threadId, "555019903", "Pending Alert") {
            MessagingEvents.publish(incoming(threadId, "Arrived while away"))
            assertTrue(
                "Background incoming message must post a tray notification",
                awaitNotification(threadId, expectedPresent = true),
            )

            ActivityScenario.launch<ThreadActivity>(threadIntent(threadId, "555019903", "Pending Alert")).use {
                assertTrue(
                    "Entering the conversation must clear its tray notification",
                    awaitNotification(threadId, expectedPresent = false),
                )
            }
        }
    }

    @Test
    fun recycleBinConversationDoesNotSuppressIncomingNotification() {
        val threadId = Long.MAX_VALUE - 41004
        withSyntheticThread(threadId, "555019904", "Recycled Thread") {
            val intent = threadIntent(threadId, "555019904", "Recycled Thread", recycleBin = true)
            ActivityScenario.launch<ThreadActivity>(intent).use {
                instrumentation.waitForIdleSync()
                MessagingEvents.publish(incoming(threadId, "Live message while in Recycle Bin"))
                assertTrue(
                    "Recycle Bin view must not suppress live incoming notifications",
                    awaitNotification(threadId, expectedPresent = true),
                )
            }
        }
    }

    @Test
    fun appLockDoesNotSuppressIncomingNotification() {
        val threadId = Long.MAX_VALUE - 41006
        val config = context.baseConfig
        val wasOn = config.isAppPasswordProtectionOn
        val previousHash = config.appPasswordHash
        val previousType = config.appProtectionType
        withSyntheticThread(threadId, "555019906", "Locked Thread") {
            try {
                config.appProtectionType = PROTECTION_PIN
                config.appPasswordHash = sha1("1234")
                config.isAppPasswordProtectionOn = true
                context.appLockManager.lock()

                ActivityScenario.launch<AppLockActivity>(Intent(context, AppLockActivity::class.java)).use {
                    instrumentation.waitForIdleSync()
                    MessagingEvents.publish(incoming(threadId, "Arrived while locked"))
                    assertTrue(
                        "App Lock must not suppress incoming notifications",
                        awaitNotification(threadId, expectedPresent = true),
                    )
                }
            } finally {
                config.isAppPasswordProtectionOn = wasOn
                config.appPasswordHash = previousHash
                config.appProtectionType = previousType
                context.appLockManager.unlock()
            }
        }
    }

    @Test
    fun sendFailureShowsInThreadSnackbarWhenViewingConversation() {
        val threadId = Long.MAX_VALUE - 41005
        withSyntheticThread(threadId, "555019905", "Failure Thread") {
            ActivityScenario.launch<ThreadActivity>(threadIntent(threadId, "555019905", "Failure Thread")).use { scenario ->
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
                scenario.awaitUntil("Active conversation must show the send failure reason in its snackbar") { activity ->
                    val current = activity.snackbarHost.currentSnackbarData
                    current?.visuals?.message == reason
                }
            }
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(40, '0')
    }
}

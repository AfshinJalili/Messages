package com.afsh.messages

import android.os.SystemClock
import androidx.room.InvalidationTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getMessagesDB
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.models.Conversation
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every write to conversations re-runs the inbox query and rebuilds the list on the main thread,
 * so refreshing unread counts must write nothing when nothing changed, and one batch when it did.
 */
@RunWith(AndroidJUnit4::class)
class InboxRefreshChecks {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun unreadRefreshWritesOnlyWhatChanged() {
        val room = RoomOnlyThread(
            context,
            Conversation(Long.MAX_VALUE - 7, "Refresh fixture", 1, true, "Refresh fixture", "", false, "5550100777", isScheduled = true),
        )
        val invalidations = AtomicInteger()
        val observer = object : InvalidationTracker.Observer("conversations") {
            override fun onInvalidated(tables: Set<String>) {
                invalidations.incrementAndGet()
            }
        }
        val tracker = context.getMessagesDB().invalidationTracker
        try {
            room.insert()
            tracker.addObserver(observer)
            settle(tracker)
            invalidations.set(0)

            // Telephony has no unread messages for this thread and Room agrees: nothing to write.
            InboxRepository.refreshUnreadCounts(context, listOf(room.conversation.threadId))
            SystemClock.sleep(SETTLE_MS)
            settle(tracker)
            check(invalidations.get() == 0) { "An unchanged refresh wrote ${invalidations.get()} times" }

            // Room is stale: one batch brings it to Telephony's zero.
            context.conversationsDB.updateUnreadCount(room.conversation.threadId, 3)
            settle(tracker)
            invalidations.set(0)
            InboxRepository.refreshUnreadCounts(context, listOf(room.conversation.threadId))
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
            while (context.conversationsDB.getConversationWithThreadId(room.conversation.threadId)?.unreadCount != 0) {
                check(SystemClock.elapsedRealtime() < deadline) { "The stale count was never corrected" }
                SystemClock.sleep(POLL_MS)
            }
            settle(tracker)
            check(invalidations.get() == 1) { "A one-row change notified ${invalidations.get()} times" }
        } finally {
            tracker.removeObserver(observer)
            room.delete()
        }
    }

    /** Room reports invalidations asynchronously; flush them before counting. */
    private fun settle(tracker: InvalidationTracker) {
        tracker.refreshVersionsSync()
        SystemClock.sleep(POLL_MS)
    }

    private companion object {
        const val SETTLE_MS = 500L
        const val POLL_MS = 50L
        const val TIMEOUT_MS = 5_000L
    }
}

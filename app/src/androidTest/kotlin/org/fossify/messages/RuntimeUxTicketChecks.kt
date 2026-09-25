package org.fossify.messages

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.extensions.*
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem.ThreadUnreadSeparator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Inserts only disposable local provider rows; never sends SMS/MMS. Requires the default SMS role. */
@RunWith(AndroidJUnit4::class)
class RuntimeUxTicketChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val rows = mutableListOf<android.net.Uri>()

    private fun sms(phone: String, threadId: Long, read: Boolean, index: Int): Long {
        val id = context.insertNewSMS(phone, "", "Ticket fixture $index\n".repeat(6),
            System.currentTimeMillis() + index * 1000L, if (read) 1 else 0,
            threadId, Telephony.Sms.MESSAGE_TYPE_INBOX, -1)
        rows.add(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id))
        return id
    }

    private fun cleanup(threadId: Long) {
        rows.forEach { uri ->
            context.contentResolver.delete(uri, null, null)
        }
        rows.clear()
        context.messagesDB.deleteThreadMessages(threadId)
        context.conversationsDB.deleteThreadId(threadId)
    }

    private fun awaitCondition(message: String, diagnostics: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("$message; ${diagnostics()}", condition())
    }

    @Test fun msg14_inboundSmsUpdatesInboxBadgeWhileOnInbox() {
        val phone = "555${System.currentTimeMillis() % 100000000}14"
        val threadId = context.getThreadId(phone)
        try {
            check(sms(phone, threadId, true, 0) > 0)
            val fixture = checkNotNull(context.getConversations(threadId).firstOrNull())
            check(fixture.read && fixture.unreadCount == 0)
            context.insertOrUpdateConversation(fixture)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                awaitCondition("Read fixture appears") {
                    var found = false
                    scenario.onActivity { activity ->
                        found = activity.inboxRows.any { it.threadId == threadId && it.conversation.read }
                    }
                    found
                }
                repeat(2) { index ->
                    sms(phone, threadId, false, index + 1)
                    refreshConversations()
                    var detail = ""
                    awaitCondition("Inbound count ${index + 1} reaches visible inbox", {
                        "$detail; provider=${context.getUnreadCountsByThread()[threadId]}; cached=${context.conversationsDB.getConversationWithThreadId(threadId)?.unreadCount}"
                    }) {
                        var correct = false
                        scenario.onActivity { activity ->
                            val row = activity.inboxRows.singleOrNull { it.threadId == threadId }?.conversation
                            detail = "row=${row?.read}/${row?.unreadCount}"
                            correct = row?.read == false && row.unreadCount == index + 1
                        }
                        correct
                    }
                }
            }
        } finally { cleanup(threadId) }
    }

    @Test fun msg15And16_openAtFirstUnreadAndMarkReadAtBottom() {
        val phone = "555${System.currentTimeMillis() % 100000000}15"
        val threadId = context.getThreadId(phone)
        try {
            repeat(35) { sms(phone, threadId, true, it) }
            repeat(12) { sms(phone, threadId, false, 35 + it) }
            context.getConversations(threadId).firstOrNull()?.let { context.insertOrUpdateConversation(it) }
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, threadId).putExtra(THREAD_NUMBER, phone).putExtra(THREAD_TITLE, "Scroll fixture")
            ActivityScenario.launch<ThreadActivity>(intent).use { scenario ->
                awaitCondition("Thread loads history") {
                    var loaded = false
                    scenario.onActivity { loaded = (it.findViewById<RecyclerView>(R.id.thread_messages_list).adapter?.itemCount ?: 0) > 30 }
                    loaded
                }
                awaitCondition("Many unreads open at first unread, not bottom") {
                    var correct = false
                    scenario.onActivity {
                        val list = it.findViewById<RecyclerView>(R.id.thread_messages_list)
                        correct = list.canScrollVertically(1) && list.scrollState == RecyclerView.SCROLL_STATE_IDLE
                    }
                    correct
                }
                assertEquals(12, context.getUnreadCountsByThread()[threadId])
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.thread_messages_list)
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(3, 0)
                }
                instrumentation.waitForIdleSync()
            }
            val remainingAfterMidScrollLeave = context.getUnreadCountsByThread()[threadId] ?: 0
            assertEquals("Leaving mid-thread preserves all unreads", 12, remainingAfterMidScrollLeave)

            ActivityScenario.launch<ThreadActivity>(intent).use { scenario ->
                awaitCondition("Thread reloads") {
                    var loaded = false
                    scenario.onActivity { loaded = (it.findViewById<RecyclerView>(R.id.thread_messages_list).adapter?.itemCount ?: 0) > 30 }
                    loaded
                }
                scenario.onActivity { it.findViewById<View>(R.id.scroll_to_bottom_fab).performClick() }
                awaitCondition("FAB scroll to bottom marks the whole thread read") {
                    var correct = false
                    scenario.onActivity {
                        val list = it.findViewById<RecyclerView>(R.id.thread_messages_list)
                        correct = !list.canScrollVertically(1) && list.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                            (context.getUnreadCountsByThread()[threadId] ?: 0) == 0
                    }
                    correct
                }
            }
            assertEquals(0, context.conversationsDB.getConversationWithThreadId(threadId)?.unreadCount)
        } finally { cleanup(threadId) }
    }

    @Test fun msg15_singleScreenUnreadClearsOnlyItsNotification() {
        val phone = "555${System.currentTimeMillis() % 100000000}17"
        val threadId = context.getThreadId(phone)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channelId = "ticket-scroll-check"
        val otherNotificationId = Int.MIN_VALUE + 17
        check(manager.activeNotifications.none { it.id == otherNotificationId })
        manager.createNotificationChannel(android.app.NotificationChannel(channelId, "Scroll test", android.app.NotificationManager.IMPORTANCE_LOW))
        try {
            repeat(30) { sms(phone, threadId, true, it) }
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, threadId).putExtra(THREAD_NUMBER, phone).putExtra(THREAD_TITLE, "Unread scroll test")
            ActivityScenario.launch<ThreadActivity>(intent).use { scenario ->
                awaitCondition("Read history loads") {
                    var loaded = false
                    scenario.onActivity { loaded = (it.findViewById<RecyclerView>(R.id.thread_messages_list).adapter?.itemCount ?: 0) >= 30 }
                    loaded
                }
                scenario.onActivity {
                    (it.findViewById<RecyclerView>(R.id.thread_messages_list).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(2, 0)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertTrue("History navigation arrow remains available", it.findViewById<View>(R.id.scroll_to_bottom_fab).isShown)
                    assertFalse("Read history has no unread counter", it.findViewById<View>(R.id.scroll_fab_unread_badge).isShown)
                }
                sms(phone, threadId, false, 30)
                refreshMessages()
                awaitCondition("One unread message shows a badge") {
                    var shown = false
                    scenario.onActivity {
                        val badge = it.findViewById<TextView>(R.id.scroll_fab_unread_badge)
                        shown = badge.isShown && badge.text.toString() == "1"
                    }
                    shown
                }
                val notification = android.app.Notification.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Scroll test fixture").build()
                manager.notify(threadId.hashCode(), notification)
                manager.notify(otherNotificationId, notification)
                awaitCondition("Both test notifications are present") {
                    manager.activeNotifications.map { it.id }.containsAll(listOf(threadId.hashCode(), otherNotificationId))
                }
                scenario.onActivity {
                    captureThread(it, "msg15-unread-fab.png")
                    it.findViewById<View>(R.id.scroll_to_bottom_fab).performClick()
                }
                awaitCondition("A single tap reaches bottom, reads the visible message and removes the divider") {
                    var reached = false
                    scenario.onActivity {
                        val list = it.findViewById<RecyclerView>(R.id.thread_messages_list)
                        reached = !list.canScrollVertically(1) && list.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                            !it.findViewById<View>(R.id.scroll_fab_unread_badge).isShown &&
                            !(list.adapter as ThreadAdapter).currentList.contains(ThreadUnreadSeparator)
                    }
                    reached && (context.getUnreadCountsByThread()[threadId] ?: 0) == 0
                }
                assertTrue(manager.activeNotifications.none { it.id == threadId.hashCode() })
                assertTrue("Unrelated notification is preserved", manager.activeNotifications.any { it.id == otherNotificationId })
                awaitCondition("Divider removal is visible after the item animation") {
                    var removed = false
                    scenario.onActivity {
                        removed = it.findViewById<View>(R.id.thread_unread_separator_holder)?.isShown != true
                    }
                    removed
                }
                scenario.onActivity { captureThread(it, "msg15-at-bottom.png") }
            }
        } finally {
            manager.cancel(threadId.hashCode())
            manager.cancel(otherNotificationId)
            manager.deleteNotificationChannel(channelId)
            cleanup(threadId)
        }
    }

    private fun captureThread(activity: ThreadActivity, name: String) {
        val view = activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun msg16_selectedSmsAndMmsReadPreservesUnseenRows() {
        val phone = "555${System.currentTimeMillis() % 100000000}16"
        val threadId = context.getThreadId(phone)
        try {
            val visibleId = sms(phone, threadId, false, 0)
            val unseenId = sms(phone, threadId, false, 1)
            val mmsUri = checkNotNull(context.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                put(Telephony.Mms.MESSAGE_TYPE, 132)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            }))
            rows.add(mmsUri)
            val smsMessages = context.getMessages(threadId).filterNot { it.isMMS }
            val visible = smsMessages.single { it.id == visibleId }
            val unseen = smsMessages.single { it.id == unseenId }
            context.messagesDB.insertMessages(visible, unseen)
            context.getConversations(threadId).firstOrNull()?.let { context.insertOrUpdateConversation(it) }
            val mms = visible.copy(id = ContentUris.parseId(mmsUri), isMMS = true)
            context.markVisibleMessagesRead(threadId, listOf(visible, mms))
            assertEquals(1, context.getUnreadCountsByThread()[threadId])
            assertEquals(1, context.conversationsDB.getConversationWithThreadId(threadId)?.unreadCount)
            assertFalse(context.getMessages(threadId).single { it.id == unseenId && !it.isMMS }.read)
            assertFalse(context.messagesDB.getThreadMessages(threadId).single { it.id == unseenId }.read)
            assertFalse("SMS/MMS IDs must not alias in the adapter", Message.areItemsTheSame(visible, visible.copy(isMMS = true)))
        } finally { cleanup(threadId) }
    }
}

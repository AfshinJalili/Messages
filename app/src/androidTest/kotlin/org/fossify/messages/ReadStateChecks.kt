package org.fossify.messages

import android.content.Intent
import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadStateChecks {
    @Test
    fun completedReadUpdatesInboxDuringSlowRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // No provider messages use this ID. Only this disposable Room row is changed.
        val fixture = Conversation(Long.MAX_VALUE - 1, "Read-state fixture", 1, false,
            "Read-state fixture", "", false, "5550100999", isScheduled = true, unreadCount = 3)
        val refreshing = MainActivity::class.java.getDeclaredField("refreshInProgress").apply { isAccessible = true }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val bus = org.greenrobot.eventbus.EventBus.getDefault()
                    if (!bus.isRegistered(activity)) bus.register(activity)
                }
                context.conversationsDB.insertOrUpdate(fixture)
                scenario.onActivity { activity ->
                    // Hold the full refresh so this checks the fast path after opening/closing a thread.
                    refreshing.setBoolean(activity, true)
                    MainActivity::class.java.getDeclaredMethod("setupConversations", ArrayList::class.java, Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(activity, arrayListOf(fixture), false)
                }
                context.markThreadMessagesRead(fixture.threadId)
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId)?.read == true)
                fun awaitRead() {
                    var read = false
                    val readDeadline = SystemClock.elapsedRealtime() + 1000
                    while (!read && SystemClock.elapsedRealtime() < readDeadline) {
                        scenario.onActivity { activity ->
                            val adapter = activity.findViewById<RecyclerView>(R.id.conversations_list).adapter as ConversationsAdapter
                            val row = adapter.currentList.singleOrNull { it.threadId == fixture.threadId }
                            read = row?.read == true && row.unreadCount == 0
                        }
                        SystemClock.sleep(20)
                    }
                    check(read) { "Read completed in the database, but the inbox still shows the thread as unread" }
                }
                awaitRead()
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId)?.unreadCount == 0)

            }
        } finally {
            context.conversationsDB.deleteThreadId(fixture.threadId)
        }
    }

    @Test
    fun openingEmptyThreadDoesNotMarkItRead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // A normal empty row avoids the intentional cleanup of empty scheduled threads.
        val fixture = Conversation(Long.MAX_VALUE - 2, "", 1, false,
            "Empty fixture", "", false, "5550100998", unreadCount = 1)
        context.conversationsDB.insertOrUpdate(fixture)
        try {
            val intent = Intent(context, ThreadActivity::class.java)
                .putExtra(THREAD_ID, fixture.threadId)
                .putExtra(THREAD_NUMBER, fixture.phoneNumber)
                .putExtra(THREAD_TITLE, fixture.title)
            ActivityScenario.launch<ThreadActivity>(intent).use {
                instrumentation.waitForIdleSync()
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId)?.read == false)
            }
        } finally {
            context.conversationsDB.deleteThreadId(fixture.threadId)
        }
    }

}

package com.afsh.messages

import android.content.Intent
import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.activities.MainActivity
import com.afsh.messages.activities.ThreadActivity
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.helpers.THREAD_TITLE
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadStateChecks {
    @Test
    fun completedReadUpdatesInboxFromRoom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val room = RoomOnlyThread(
            context,
            fixtureConversation(
                Long.MAX_VALUE - 1, "Read-state fixture", snippet = "Read-state fixture", read = false,
                phone = "5550100999", scheduled = true, unreadCount = 3,
            ),
        )
        val fixture = room.conversation
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                room.insert()
                ConversationRepository.from(context).markThreadRead(fixture.threadId)
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId)?.read == true)
                scenario.awaitUntil("Read completed in Room, but the inbox still shows the thread as unread", READ_TIMEOUT_MS) { activity ->
                    val row = activity.inboxRows.singleOrNull { it.threadId == fixture.threadId }?.conversation
                    row?.read == true && row.unreadCount == 0
                }
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId)?.unreadCount == 0)
            }
        } finally {
            room.delete()
        }
    }

    @Test
    fun openingEmptyThreadDoesNotMarkItRead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = fixtureConversation(Long.MAX_VALUE - 2, "Empty fixture", snippet = "", read = false, phone = "5550100998", unreadCount = 1)
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

    private companion object {
        // The emulator in CI is slow to refresh the inbox after a write.
        const val READ_TIMEOUT_MS = 10_000L
    }
}

package org.fossify.messages

import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DeletionChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun conversationStaysHiddenAcrossRefreshAndUndoSurvivesRecreation() {
        val context = instrumentation.targetContext
        // Scheduled with a number no provider thread uses, so reconciliation keeps this Room-only row.
        // It also needs a pending scheduled message: reconciliation deletes a scheduled thread with none.
        val fixture = Conversation(
            Long.MAX_VALUE - 123, "Fixture", 1, true, "Fixture", "", false, "5550100987654", isScheduled = true,
        )
        fun MainActivity.shows() = inboxRows.any { it.threadId == fixture.threadId }
        fun ActivityScenario<MainActivity>.await(message: String, timeoutMs: Long = 5000, condition: (MainActivity) -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var met = false
            while (!met && SystemClock.elapsedRealtime() < deadline) {
                onActivity { met = condition(it) }
                if (!met) SystemClock.sleep(50)
            }
            check(met) {
                "$message (room row=${context.conversationsDB.getConversationWithThreadId(fixture.threadId) != null}, " +
                    "hidden=${fixture.threadId in UndoDeletion.threads})"
            }
        }
        val pending = Message(
            Long.MAX_VALUE - 124, "See you at five", Telephony.Sms.MESSAGE_TYPE_SENT, 0, arrayListOf(),
            (System.currentTimeMillis() / 1000 + 3600).toInt(), true, fixture.threadId, false, null, "5550100987654", "Fixture", "", -1,
            isScheduled = true,
        )
        context.conversationsDB.insertOrUpdate(fixture)
        context.messagesDB.insertOrUpdate(pending)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                // A cold start loads the owner's whole inbox first, which can take longer than the undo window.
                scenario.await("Fixture reaches the inbox", timeoutMs = 20_000) { it.shows() }
                scenario.onActivity { it.deleteWithUndo(listOf(fixture)) }
                scenario.onActivity { check(!it.shows()) { "Deleted conversation must disappear immediately" } }
                // Recreate first: the whole check has to fit inside the 5 s undo window.
                scenario.recreate()
                // Undo must survive recreation: the new activity re-shows it a frame after resume. The M3
                // snackbar hides its action from the unmerged semantics tree, so press it through the host.
                scenario.await("Undo is offered again after recreation") { it.snackbarHost.currentSnackbarData != null }
                refreshConversations()
                instrumentation.waitForIdleSync()
                scenario.onActivity { check(!it.shows()) { "Refresh resurrected a pending deletion" } }
                scenario.onActivity { activity ->
                    val undo = activity.snackbarHost.currentSnackbarData
                    check(undo?.visuals?.actionLabel == activity.getString(org.fossify.commons.R.string.undo))
                    undo?.performAction()
                }
                scenario.await("Undo restores the row") { fixture.threadId !in UndoDeletion.threads && it.shows() }
                Thread.sleep(5200)
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId) != null) { "Undo must prevent the delete" }
            }
        } finally {
            context.messagesDB.delete(pending.id)
            context.conversationsDB.deleteThreadId(fixture.threadId)
        }
    }

    /** Two quick deletions merge into one batch; one Undo must reverse both and nothing may queue behind it. */
    @Test
    fun undoAfterTwoQuickDeletionsUndoesBoth() {
        val undone = AtomicInteger()
        val committed = AtomicInteger()
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                repeat(2) {
                    UndoDeletion.offer(activity, undo = { undone.incrementAndGet() }, commit = { committed.incrementAndGet() })
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 3000
            var shown = false
            while (!shown && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { shown = it.snackbarHost.currentSnackbarData != null }
                if (!shown) SystemClock.sleep(50)
            }
            check(shown) { "Undo must be offered" }
            scenario.onActivity { it.snackbarHost.currentSnackbarData?.performAction() }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            scenario.onActivity { check(it.snackbarHost.currentSnackbarData == null) { "Undo must not reveal a queued second snackbar" } }
            check(undone.get() == 2) { "Undo must reverse both merged deletions, got ${undone.get()}" }
            Thread.sleep(5500)
            check(committed.get() == 0) { "Undo must cancel the pending commits" }
        }
    }

    @Test
    fun deletionWaitsFiveSecondsThenCommitsOnce() {
        val committed = CountDownLatch(1)
        val count = AtomicInteger()
        val completed = CountDownLatch(1)
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                UndoDeletion.offer(activity, undo = { error("Unexpected undo") }, commit = {
                    count.incrementAndGet()
                    committed.countDown()
                }, completed = { completed.countDown() })
            }
            check(!committed.await(4500, TimeUnit.MILLISECONDS)) { "Deletion committed before the five-second undo window" }
            check(committed.await(3, TimeUnit.SECONDS))
            check(completed.await(3, TimeUnit.SECONDS))
            check(count.get() == 1)
        }
    }
}

package com.afsh.messages

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.activities.MainActivity
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.helpers.refreshConversations
import com.afsh.messages.helpers.UndoDeletion
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
        val room = RoomOnlyThread(
            context,
            fixtureConversation(Long.MAX_VALUE - 123, "Fixture", snippet = "Fixture", phone = "5550100987654", scheduled = true),
        )
        val fixture = room.conversation
        fun MainActivity.shows() = inboxRows.any { it.threadId == fixture.threadId }
        room.insert()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                // A cold start loads the owner's whole inbox first, which can take longer than the undo window.
                scenario.awaitUntil("Fixture reaches the inbox", timeoutMs = 20_000) { it.shows() }
                scenario.onActivity { it.deleteWithUndo(listOf(fixture)) }
                check(!scenario.read { it.shows() }) { "Deleted conversation must disappear immediately" }
                // Recreate first: the whole check has to fit inside the 5 s undo window.
                scenario.recreate()
                // Undo must survive recreation: the new activity re-shows it a frame after resume. The M3
                // snackbar hides its action from the unmerged semantics tree, so press it through the host.
                scenario.awaitUntil("Undo is offered again after recreation") { it.snackbarHost.currentSnackbarData != null }
                refreshConversations()
                instrumentation.waitForIdleSync()
                check(!scenario.read { it.shows() }) { "Refresh resurrected a pending deletion" }
                scenario.onActivity { activity ->
                    val undo = activity.snackbarHost.currentSnackbarData
                    check(undo?.visuals?.actionLabel == activity.getString(org.fossify.commons.R.string.undo))
                    undo?.performAction()
                }
                scenario.awaitUntil("Undo restores the row") { fixture.threadId !in UndoDeletion.threads && it.shows() }
                Thread.sleep(5200)
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId) != null) { "Undo must prevent the delete" }
            }
        } finally {
            room.delete()
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
            scenario.awaitUntil("Undo must be offered", timeoutMs = 3000) { it.snackbarHost.currentSnackbarData != null }
            scenario.onActivity { it.snackbarHost.currentSnackbarData?.performAction() }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            check(scenario.read { it.snackbarHost.currentSnackbarData } == null) { "Undo must not reveal a queued second snackbar" }
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

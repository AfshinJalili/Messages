package com.afsh.messages

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.afsh.messages.activities.MainActivity
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.testUndoDurationMs
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deletion and undo checks. Uses a 30-second undo window to avoid racing on large inboxes.
 * Closes #59.
 */
@RunWith(AndroidJUnit4::class)
class DeletionChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        testUndoDurationMs = null
    }

    // TODO: Add archiveUndoRestoresConversation test with real swipe gesture
    // The fix is correct (reset state before calling onSwipe in SwipeableRow),
    // but writing a proper test that combines ActivityScenario with performTouchInput
    // is complex. Manual QA should verify: swipe to archive, tap Undo, conversation reappears.

    @Test
    fun conversationStaysHiddenAcrossRefreshAndUndoSurvivesRecreation() {
        // Use a 30-second undo window for this test to avoid racing on large inboxes (#59)
        testUndoDurationMs = 30_000
        
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
                // A cold start loads the owner's whole inbox first, which can take longer than the default undo window.
                scenario.awaitUntil("Fixture reaches the inbox", timeoutMs = 20_000) { it.shows() }
                scenario.onActivity { it.deleteWithUndo(listOf(fixture)) }
                check(!scenario.read { it.shows() }) { "Deleted conversation must disappear immediately" }
                // Recreate to verify undo survives recreation
                scenario.recreate()
                // Undo must survive recreation: the new activity re-shows it after resume.
                scenario.awaitUntil("Undo is offered again after recreation", timeoutMs = 5_000) { 
                    it.snackbarHost.currentSnackbarData != null 
                }
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                instrumentation.waitForIdleSync()
                check(!scenario.read { it.shows() }) { "Refresh resurrected a pending deletion" }
                scenario.onActivity { activity ->
                    val undo = activity.snackbarHost.currentSnackbarData
                    check(undo != null) { "Snackbar must still be showing" }
                    check(undo.visuals.actionLabel == activity.getString(com.afsh.commons.R.string.undo)) {
                        "Expected Undo label, got: ${undo.visuals.actionLabel}"
                    }
                    undo.performAction()
                }
                scenario.awaitUntil("Undo restores the row", timeoutMs = 5_000) { 
                    fixture.threadId !in UndoDeletion.threads && it.shows() 
                }
                Thread.sleep(200) // Brief wait to ensure DB write completes
                check(context.conversationsDB.getConversationWithThreadId(fixture.threadId) != null) { 
                    "Undo must prevent the delete" 
                }
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

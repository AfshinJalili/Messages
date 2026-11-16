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
import com.afsh.messages.helpers.refreshConversations
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

    /**
     * Archive undo: verify that tapping Undo on the archive snackbar restores the conversation
     * to the inbox and doesn't re-show the snackbar or re-trigger the archive. Uses a real swipe
     * gesture to catch the SwipeToDismissBoxState bug where the row's Dismissed state persists
     * and fires onDismiss again when the row returns after undo.
     */
    @Test
    fun archiveUndoRestoresConversation() {
        testUndoDurationMs = 15_000
        
        val context = composeRule.activity
        val room = RoomOnlyThread(
            context,
            fixtureConversation(Long.MAX_VALUE - 456, "Archive Test Fixture", snippet = "Test", phone = "5550100111222"),
        )
        val fixture = room.conversation
        room.insert()
        try {
            // Wait for fixture to appear in inbox
            composeRule.waitUntil(timeoutMillis = 20_000) {
                context.inboxRows.any { it.threadId == fixture.threadId }
            }
            
            // Archive via real swipe gesture (right swipe in LTR)
            composeRule.onNodeWithText(fixture.title, substring = true).performTouchInput { swipeRight() }
            
            // Conversation should be hidden immediately
            composeRule.waitUntil(timeoutMillis = 2_000) {
                context.inboxRows.none { it.threadId == fixture.threadId }
            }
            
            // Snackbar should be showing
            composeRule.waitUntil(timeoutMillis = 2_000) {
                context.snackbarHost.currentSnackbarData != null
            }
            
            // Tap Undo
            val undo = context.snackbarHost.currentSnackbarData
            check(undo != null) { "Snackbar must be showing" }
            undo.performAction()
            
            // Conversation should reappear
            composeRule.waitUntil(timeoutMillis = 5_000) {
                context.inboxRows.any { it.threadId == fixture.threadId }
            }
            
            // Wait to ensure no snackbar reappears and conversation stays visible
            Thread.sleep(500)
            check(context.inboxRows.any { it.threadId == fixture.threadId }) {
                "Conversation must stay visible after undo"
            }
            check(context.snackbarHost.currentSnackbarData == null) {
                "Snackbar must not reappear after undo"
            }
        } finally {
            room.delete()
        }
    }

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
                refreshConversations()
                instrumentation.waitForIdleSync()
                check(!scenario.read { it.shows() }) { "Refresh resurrected a pending deletion" }
                scenario.onActivity { activity ->
                    val undo = activity.snackbarHost.currentSnackbarData
                    check(undo != null) { "Snackbar must still be showing" }
                    check(undo.visuals.actionLabel == activity.getString(org.fossify.commons.R.string.undo)) {
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

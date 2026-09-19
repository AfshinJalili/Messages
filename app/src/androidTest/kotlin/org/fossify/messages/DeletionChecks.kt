package org.fossify.messages

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.models.Conversation
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
        val committed = AtomicInteger()
        val fixture = Conversation(Long.MAX_VALUE - 123, "Fixture", 1, true, "Fixture", "", false, "5550100")
        lateinit var adapter: ConversationsAdapter
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            val loaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                adapter = ConversationsAdapter(activity, MyRecyclerView(activity), {}, {})
                adapter.updateConversations(arrayListOf(fixture)) { loaded.countDown() }
            }
            check(loaded.await(5, TimeUnit.SECONDS))
            scenario.onActivity {
                adapter.deleteWithUndo(listOf(fixture)) { committed.incrementAndGet() }
            }
            instrumentation.waitForIdleSync()
            val refreshed = CountDownLatch(1)
            scenario.onActivity {
                check(adapter.currentList.isEmpty()) { "Deleted conversation must disappear immediately" }
                adapter.updateConversations(arrayListOf(fixture)) { refreshed.countDown() }
            }
            check(refreshed.await(5, TimeUnit.SECONDS))
            scenario.onActivity { check(adapter.currentList.isEmpty()) { "Refresh resurrected a pending deletion" } }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val undo = activity.findViewById<View>(com.google.android.material.R.id.snackbar_action)
                check(undo != null && undo.performClick()) { "Undo must survive recreation" }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                check(fixture.threadId !in UndoDeletion.threads)
                check(adapter.currentList.any { it.threadId == fixture.threadId })
            }
            Thread.sleep(5200)
            check(committed.get() == 0) { "Undo must prevent the provider delete" }
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

    @Test
    fun selectedCategoryHasNoCheckmark() {
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val filters = activity.findViewById<android.view.ViewGroup>(R.id.inbox_filters)
                check(filters.childCount > 0)
                for (i in 0 until filters.childCount) {
                    val chip = filters.getChildAt(i) as Chip
                    chip.isChecked = true
                    check(!chip.isCheckedIconVisible)
                }
            }
        }
    }
}

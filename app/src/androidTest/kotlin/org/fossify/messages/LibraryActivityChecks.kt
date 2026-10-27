package org.fossify.messages

import android.app.Activity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.ArchivedConversationsActivity
import org.fossify.messages.activities.RecycleBinConversationsActivity
import org.fossify.messages.activities.StarredMessagesActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Library activities start on the device's own data and render their page. Nothing is changed. */
@RunWith(AndroidJUnit4::class)
class LibraryActivityChecks {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun starredOpens() = opens(StarredMessagesActivity::class.java, R.string.starred_subtitle, "starred")

    @Test
    fun archiveOpens() = opens(ArchivedConversationsActivity::class.java, R.string.archive_hint, "archive")

    @Test
    fun recycleBinOpens() = opens(RecycleBinConversationsActivity::class.java, R.string.recycle_bin_note, "recycle-bin")

    private fun <T : Activity> opens(activity: Class<T>, marker: Int, shot: String) {
        ActivityScenario.launch(activity).use {
            val text = context.getString(marker)
            compose.waitUntil(RENDER_TIMEOUT_MS) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            Screenshots.capture("device-$shot")
        }
    }

    private companion object {
        const val RENDER_TIMEOUT_MS = 10_000L
    }
}

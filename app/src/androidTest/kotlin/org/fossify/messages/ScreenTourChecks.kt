package org.fossify.messages

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.activities.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches top-level screens, waits until they render, and captures them as QA evidence. */
@RunWith(AndroidJUnit4::class)
class ScreenTourChecks {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun inboxRendersAndIsCaptured() {
        val searchHint = compose.activity.getString(R.string.inbox_search_hint)
        compose.waitUntil(RENDER_TIMEOUT_MS) { compose.onAllNodesWithText(searchHint).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        check(Screenshots.capture("inbox").length() > 0)
    }

    private companion object {
        const val RENDER_TIMEOUT_MS = 10_000L
    }
}

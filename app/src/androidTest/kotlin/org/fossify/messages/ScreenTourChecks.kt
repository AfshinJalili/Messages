package org.fossify.messages

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
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
        // The search entry is text in the tall layout and an icon description in the compact one.
        val searchEntry = hasText(searchHint) or hasContentDescription(searchHint)
        compose.waitUntil(RENDER_TIMEOUT_MS) { compose.onAllNodes(searchEntry, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        check(Screenshots.capture("inbox").length() > 0)
    }

    @Test
    fun libraryRendersAndIsCaptured() {
        val library = compose.activity.getString(R.string.inbox_nav_library)
        compose.waitUntil(RENDER_TIMEOUT_MS) { compose.onAllNodes(hasText(library)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(library))[0].performClick()
        val principle = compose.activity.getString(R.string.library_principle_title)
        compose.waitUntil(RENDER_TIMEOUT_MS) { compose.onAllNodes(hasText(principle)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        check(Screenshots.capture("17-library").length() > 0)
    }

    private companion object {
        const val RENDER_TIMEOUT_MS = 10_000L
    }
}

package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.inbox.LibraryDestination
import org.fossify.messages.ui.library.LibraryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 17 · Library: destinations follow the settings, open on tap, and spam carries its unread count. */
@RunWith(AndroidJUnit4::class)
class LibraryChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun destinationsOpenAndKeepTheirTargets() {
        val opened = mutableListOf<LibraryDestination>()
        compose.setContent {
            OpenLineTheme(dark = false) {
                LibraryScreen(LibraryDestination.entries, unreadSpam = 0, onOpen = { opened += it })
            }
        }
        LibraryDestination.entries.forEach { destination ->
            compose.onNodeWithText(text(destination.label)).assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText(text(destination.description)).assertExists()
        }
        check(opened == LibraryDestination.entries) { "Opened $opened" }
        compose.onNodeWithText(text(R.string.library_principle_title)).assertExists()
    }

    @Test
    fun unavailableDestinationsAreLeftOutAndSpamShowsItsCount() {
        val shown = listOf(LibraryDestination.STARRED, LibraryDestination.SPAM)
        compose.setContent {
            OpenLineTheme(dark = false) {
                LibraryScreen(shown, unreadSpam = 7, onOpen = {})
            }
        }
        compose.onNodeWithText(text(R.string.archived_conversations)).assertDoesNotExist()
        compose.onNodeWithText(text(org.fossify.commons.R.string.recycle_bin)).assertDoesNotExist()
        val count = String.format(compose.activity.resources.configuration.locales[0], "%d", 7)
        compose.onNodeWithText(count).assertExists()
    }
}

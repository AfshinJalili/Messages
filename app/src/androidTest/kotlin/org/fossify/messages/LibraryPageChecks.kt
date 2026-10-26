package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.library.ArchiveEvent
import org.fossify.messages.ui.library.ArchiveScreen
import org.fossify.messages.ui.library.ArchiveUiState
import org.fossify.messages.ui.library.RecycleBinEvent
import org.fossify.messages.ui.library.RecycleBinScreen
import org.fossify.messages.ui.library.RecycleBinUiState
import org.fossify.messages.ui.library.RecycledConversation
import org.fossify.messages.ui.library.StarredMessage
import org.fossify.messages.ui.library.StarredScreen
import org.fossify.messages.ui.library.StarredUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 18 Starred, 19 Archive and 22 Recycle bin: behavior on sample data, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class LibraryPageChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val navigation = PageNavigation(TopDestination.LIBRARY, {})
    private val now = System.currentTimeMillis()

    private fun conversation(id: Long, title: String, snippet: String = "", daysAgo: Int = 0) = Conversation(
        id, snippet, ((now / 1000) - daysAgo * DAY_SECONDS).toInt(), true, title, "", false, "+1555000$id",
    )

    private val starred = listOf(
        StarredMessage(1, false, 1, "Mina Farahani", "The address is 24 Willow Street.", now),
        StarredMessage(2, true, 2, "Dad", "Old family album · 2 photos", now - DAY_MILLIS),
        StarredMessage(3, false, 3, "Book club", "Thursday at 7, at Jo’s place.", now - 8 * DAY_MILLIS),
    )
    private val archived = listOf(
        InboxRow(conversation(11, "Parcel service", "Delivered · Your package…", 5), null, false, false),
        InboxRow(conversation(12, "Yoga studio", "See you next month", 10), null, false, false),
        InboxRow(conversation(13, "Nadia", "Thank you for the photos", 24), null, false, false),
    )
    private val recycled = listOf(
        RecycledConversation(conversation(21, "Mina Farahani"), 2, now),
        RecycledConversation(conversation(22, "Pharmacy"), 1, now - DAY_MILLIS),
    )

    @Test
    fun starredCardsOpenTheirMessageAndShowDayAndTime() {
        val opened = mutableListOf<StarredMessage>()
        compose.setContent {
            OpenLineTheme(dark = false) {
                StarredScreen(StarredUiState(loading = false, messages = starred), navigation, onBack = {}, onOpen = { opened += it })
            }
        }
        compose.onNodeWithText("Dad", substring = true, useUnmergedTree = true).performClick()
        check(opened.single().messageId == 2L) { "Opened $opened" }
        val today = text(R.string.inbox_section_today)
        compose.onAllNodesWithText(today, substring = true, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun emptyStarredExplainsHowToStar() {
        compose.setContent {
            OpenLineTheme(dark = false) {
                StarredScreen(StarredUiState(loading = false), navigation, onBack = {}, onOpen = {})
            }
        }
        compose.onNodeWithText(text(R.string.no_starred_messages)).assertExists()
        compose.onNodeWithText(text(R.string.starred_empty_text)).assertExists()
    }

    @Test
    fun archiveUnarchivesFromTalkBackAndSelectsOnLongPress() {
        val events = mutableListOf<ArchiveEvent>()
        var state by mutableStateOf(ArchiveUiState(loading = false, rows = archived))
        compose.setContent {
            OpenLineTheme(dark = false) {
                ArchiveScreen(state, navigation, remember { SnackbarHostState() }, onEvent = { events += it })
            }
        }
        val yoga = hasContentDescription("Yoga studio", substring = true)
        val unarchive = compose.onNode(yoga).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == text(R.string.unarchive) }
        compose.runOnUiThread { unarchive.action() }
        check((events.last() as ArchiveEvent.Unarchive).conversations.single().threadId == 12L) { "Events $events" }
        compose.onNode(yoga).performTouchInput { longClick() }
        check(events.last() == ArchiveEvent.ToggleSelection(archived[1])) { "Events $events" }

        state = state.copy(selected = setOf(12))
        compose.onNodeWithText(resourcesPlural(R.plurals.inbox_selected, 1)).assertExists()
        compose.onNodeWithText(text(org.fossify.commons.R.string.delete)).performClick()
        check((events.last() as ArchiveEvent.Delete).conversations.single().threadId == 12L) { "Events $events" }
    }

    @Test
    fun recycleBinRestoresAtOnceAndConfirmsPermanentDeletion() {
        val events = mutableListOf<RecycleBinEvent>()
        compose.setContent {
            OpenLineTheme(dark = false) {
                RecycleBinScreen(
                    RecycleBinUiState(loading = false, conversations = recycled),
                    navigation,
                    remember { SnackbarHostState() },
                    onEvent = { events += it },
                )
            }
        }
        compose.onNodeWithText(text(R.string.recycle_bin_deleted_today), substring = true).assertExists()
        compose.onNodeWithText(text(R.string.recycle_bin_deleted_yesterday), substring = true).assertExists()

        compose.onAllNodesWithText(text(R.string.restore))[0].performClick()
        check(events.last() == RecycleBinEvent.Restore(recycled[0].conversation)) { "Events $events" }

        compose.onAllNodesWithText(text(R.string.delete_forever))[1].performClick()
        check(events.last() !is RecycleBinEvent.DeleteForever) { "Delete forever must ask first" }
        compose.onNodeWithText(text(R.string.delete_forever_title)).assertExists()
        // The dialog's confirm button is the last "Delete forever" on screen.
        compose.onAllNodesWithText(text(R.string.delete_forever)).let { it[2].performClick() }
        check(events.last() == RecycleBinEvent.DeleteForever(recycled[1].conversation)) { "Events $events" }

        compose.onNodeWithText(text(R.string.recycle_bin_empty)).performClick()
        compose.onNodeWithText(text(R.string.empty_bin_title)).assertExists()
        compose.onNodeWithText(text(org.fossify.commons.R.string.cancel)).performClick()
        check(events.last() !is RecycleBinEvent.EmptyBin) { "Cancel must not empty the bin" }
    }

    @Test
    fun libraryPagesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var page by mutableStateOf(0)
        compose.setContent {
            DesignVariantContent(variant) {
                when (page) {
                    0 -> StarredScreen(StarredUiState(loading = false, messages = starred), navigation, onBack = {}, onOpen = {})
                    1 -> ArchiveScreen(ArchiveUiState(loading = false, rows = archived), navigation, remember { SnackbarHostState() }, onEvent = {})
                    else -> RecycleBinScreen(
                        RecycleBinUiState(loading = false, conversations = recycled),
                        navigation,
                        remember { SnackbarHostState() },
                        onEvent = {},
                    )
                }
            }
        }
        listOf("18-starred", "19-archive", "22-recycle-bin").forEachIndexed { index, name ->
            page = index
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
    }

    private fun resourcesPlural(id: Int, count: Int) = compose.activity.resources.getQuantityString(id, count, count)

    private companion object {
        const val DAY_SECONDS = 86_400L
        const val DAY_MILLIS = DAY_SECONDS * 1000
    }
}

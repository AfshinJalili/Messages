package com.afsh.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.models.Message
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.thread.THREAD_TOP_BAR_TAG
import com.afsh.messages.ui.thread.ThreadEvent
import com.afsh.messages.ui.thread.ThreadHeaderState
import com.afsh.messages.ui.thread.ThreadMenuAction
import com.afsh.messages.ui.thread.ThreadTopBar
import com.afsh.messages.ui.thread.ThreadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The conversation top bar on sample data, found by what TalkBack reads: labels, the heading and the bar's tag. */
@RunWith(AndroidJUnit4::class)
class ThreadTopBarChecks {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private val header = ThreadHeaderState(
        title = "Mina Farahani",
        subtitle = "+98 912 000 0000",
        canDial = true,
        actions = listOf(ThreadMenuAction.SEARCH, ThreadMenuAction.DETAILS, ThreadMenuAction.DELETE),
    )
    private val selectedMessage = Message(
        1, "Hi", 1, -1, arrayListOf(), 1700000000, true, 1, false, null, "", "Fixture", "", -1,
    )

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val inBar = hasAnyAncestor(hasTestTag(THREAD_TOP_BAR_TAG))

    private fun show(
        state: ThreadUiState,
        rtl: Boolean = false,
        textScale: Float = 1f,
        onEvent: (ThreadEvent) -> Unit = {},
    ) = compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            AfshTheme(dark = false, textScale = textScale) {
                Box(Modifier.width(360.dp)) { ThreadTopBar(state, onEvent) }
            }
        }
    }

    private fun button(label: Int) = compose.onNode(hasContentDescription(text(label)) and inBar)

    @Test fun conversationShowsContactCallAndMenu() {
        val events = mutableListOf<ThreadEvent>()
        show(ThreadUiState(header = header)) { events += it }

        compose.onNode(hasText(header.title) and isHeading and inBar).assertIsDisplayed()
        compose.onNode(hasText(header.subtitle) and inBar).assertIsDisplayed()

        button(org.fossify.commons.R.string.back).performClick()
        button(org.fossify.commons.R.string.dial_number).performClick()
        compose.onNode(hasText(header.title) and isHeading).performClick()
        assertEquals(listOf(ThreadEvent.Back, ThreadEvent.Dial, ThreadEvent.OpenDetails), events)

        button(org.fossify.commons.R.string.more_options).performClick()
        compose.onNodeWithText(text(R.string.search_in_conversation)).performClick()
        assertEquals(ThreadEvent.Menu(ThreadMenuAction.SEARCH), events.last())
    }

    @Test fun noCallOrMenuWhenTheConversationHasNone() {
        show(ThreadUiState(header = header.copy(canDial = false, actions = emptyList())))
        button(org.fossify.commons.R.string.dial_number).assertDoesNotExist()
        button(org.fossify.commons.R.string.more_options).assertDoesNotExist()
    }

    @Test fun selectionShowsCloseAndCount() {
        val events = mutableListOf<ThreadEvent>()
        val state = ThreadUiState(header = header, items = listOf(selectedMessage), selected = setOf(selectedMessage.getStableId()))
        show(state) { events += it }

        compose.onNode(hasText(context.resources.getQuantityString(R.plurals.inbox_selected, 1, 1)) and inBar).assertIsDisplayed()
        button(org.fossify.commons.R.string.dial_number).assertDoesNotExist()
        button(org.fossify.commons.R.string.more_options).assertDoesNotExist()
        button(org.fossify.commons.R.string.close).performClick()
        assertEquals(listOf(ThreadEvent.ClearSelection), events)
    }

    @Test fun searchTypesSubmitsAndCloses() {
        val events = mutableListOf<ThreadEvent>()
        show(ThreadUiState(header = header, searching = true)) { events += it }

        compose.onNodeWithText(text(R.string.search_in_conversation)).assertIsDisplayed()
        button(R.string.thread_search_next).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and inBar).performTextInput("park")
        button(R.string.thread_search_next).performClick()
        button(org.fossify.commons.R.string.close).performClick()
        assertEquals(listOf(ThreadEvent.SearchChanged, ThreadEvent.SearchSubmit("park"), ThreadEvent.SearchClose), events)
    }

    @Test fun longNameInPersianAtLargeTextKeepsEveryActionOnScreen() {
        val long = header.copy(title = "مینا فراهانی زاده از دفتر مرکزی شرکت حمل و نقل بین‌المللی")
        show(ThreadUiState(header = long), rtl = true, textScale = 2f)

        val bar = compose.onNodeWithTag(THREAD_TOP_BAR_TAG).getBoundsInRoot()
        val title = compose.onNode(isHeading and inBar).getBoundsInRoot()
        assertTrue("The bar grows to hold the name", title.top >= bar.top && title.bottom <= bar.bottom)
        listOf(org.fossify.commons.R.string.back, org.fossify.commons.R.string.dial_number, org.fossify.commons.R.string.more_options).forEach {
            val action = button(it).assertIsDisplayed().getBoundsInRoot()
            assertTrue("${text(it)} stays inside the bar", action.left >= bar.left && action.right <= bar.right)
        }
        // RTL puts back at the start, which is the right edge.
        val back = button(org.fossify.commons.R.string.back).getBoundsInRoot()
        val more = button(org.fossify.commons.R.string.more_options).getBoundsInRoot()
        assertTrue("Back leads in RTL", back.left > more.left)
    }

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetText)
}

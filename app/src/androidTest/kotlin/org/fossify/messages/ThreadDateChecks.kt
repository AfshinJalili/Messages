package org.fossify.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.models.SimpleContact
import org.fossify.commons.extensions.formatTime
import org.fossify.messages.helpers.ThreadDates
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.fossify.messages.models.ThreadItem.ThreadDateTime
import org.fossify.messages.models.buildThreadItems
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.thread.THREAD_LIST_TAG
import org.fossify.messages.ui.thread.THREAD_STICKY_DATE_TAG
import org.fossify.messages.ui.thread.ThreadTimeline
import org.fossify.messages.ui.thread.ThreadUiState
import org.joda.time.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** In-memory messages only; never sends or inserts provider messages. */
@RunWith(AndroidJUnit4::class)
class ThreadDateChecks {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val firstDay = DateTime.now().withMonthOfYear(9).withDayOfMonth(11).withTimeAtStartOfDay()

    private fun message(id: Int, date: DateTime, sim: Int = 1) = Message(
        id.toLong(), "Message $id. A longer message to check wrapping and the separate time footer.",
        2, 0, arrayListOf(), (date.millis / 1000).toInt(), true, Long.MAX_VALUE - 12,
        false, null, "", "Date fixture", "", sim)

    private fun fixture(): List<Message> {
        val messages = arrayListOf(message(1, firstDay.plusMinutes(1)), message(2, firstDay.plusHours(15), 2),
            message(3, firstDay.plusDays(1).minusSeconds(1)), message(4, firstDay.plusDays(1)))
        repeat(24) { messages.add(message(it + 5, firstDay.plusDays(1).plusMinutes(it + 1))) }
        messages[7] = messages[7].copy(type = 1, body = "Received message. پیام دریافتی", participants = arrayListOf(
            SimpleContact(0, 0, "Date fixture", "", arrayListOf(), arrayListOf(), arrayListOf())))
        messages[8] = messages[8].copy(body = "", isMMS = true, attachment = MessageAttachment(9, "", arrayListOf(
            Attachment(null, 9, "content://fixture/document", "application/pdf", 0, 0, "Document.pdf"))))
        messages[9] = messages[9].copy(body = "سلام", subscriptionId = 2)
        return messages
    }

    private fun state() = ThreadUiState(
        items = buildThreadItems(fixture(), isSpam = { false }, isHidden = { false }, isExpanded = { false }),
        simLabels = mapOf(1 to "1", 2 to "2"),
    )

    private fun show(state: ThreadUiState, rtl: Boolean = false, textScale: Float = 1f) = compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            OpenLineTheme(dark = false, textScale = textScale) {
                Box(Modifier.size(360.dp, 640.dp)) {
                    ThreadTimeline(state, SnackbarHostState(), {})
                }
            }
        }
    }

    @Test fun oneDividerPerDayAcrossGapsAndSimSwitches() {
        val dividers = state().items.filterIsInstance<ThreadDateTime>()
        assertEquals("Gaps and SIM switches must not split a day", 2, dividers.size)
        assertEquals(firstDay.millis / 1000, dividers[0].date.toLong())
        assertEquals(firstDay.plusDays(1).millis / 1000, dividers[1].date.toLong())
    }

    @Test fun metadataCarriesSimStatusAndAttachments() {
        show(state())
        // Newest first: the oldest rows sit at the far end of the reversed list.
        compose.onNodeWithTag(THREAD_LIST_TAG).performScrollToIndex(state().items.size - 12)
        compose.onAllNodes(hasContentDescription(context.getString(R.string.message_delivered), substring = true)).onFirst().assertExists()
        compose.onNodeWithText("Document.pdf").assertExists()
        openGroupedSimMessageDetails()
        compose.onNode(hasText(sheetInfo())).assertExists()
    }

    @Test fun stickyDateFollowsTopDayThenFades() {
        show(state())
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(THREAD_STICKY_DATE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(THREAD_LIST_TAG).performTouchInput { swipeDown(durationMillis = 400) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(THREAD_STICKY_DATE_TAG).assertExists()
        compose.onNodeWithText(ThreadDates.label((firstDay.plusDays(1).millis / 1000).toInt()), useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag(THREAD_STICKY_DATE_TAG).assertDoesNotExist()
    }

    @Test fun largeRtlMetadataStaysOnScreen() {
        show(state(), rtl = true, textScale = 1.5f)
        compose.onNodeWithTag(THREAD_LIST_TAG).performScrollToIndex(state().items.size - 12)
        openGroupedSimMessageDetails()
        val meta = compose.onNode(hasText(sheetInfo())).getUnclippedBoundsInRoot()
        // The sheet is its own full-width window, so it is bounded by the screen, not the 360 dp host.
        val screen = context.resources.configuration.screenWidthDp.dp
        assertTrue("Sheet info ${meta.left}..${meta.right} fits 0..$screen", meta.left >= 0.dp && meta.right <= screen)
    }

    // The action sheet's first line (R6-48): date · time · SIM.
    private fun sheetInfo() = fixture()[9].date.let { seconds ->
        val millis = seconds * 1000L
        listOf(android.text.format.DateFormat.format("EEE d MMM yyyy", millis).toString(), millis.formatTime(context), context.getString(R.string.message_sim_label, "2"))
            .joinToString(" · ")
    }

    private fun openGroupedSimMessageDetails() {
        // Message 10 (id 10) has SIM 2 but is inside a grouped run, so C-M1 hides its footer.
        // Its action sheet must still expose the exact message's timestamp and SIM.
        compose.onNodeWithText("سلام").performScrollTo().performTouchInput { click() }
    }

    @Test fun labelsAndFooterRefresh() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val date = DateTime(2026, 9, 11, 0, 0)
            assertEquals("11 September", ThreadDates.label((date.millis / 1000).toInt(), date))
            assertEquals("11 September 2026", ThreadDates.label((date.millis / 1000).toInt(), date.plusYears(1)))
            val message = message(1, date)
            assertFalse(Message.areContentsTheSame(message, message.copy(status = 64)))
            assertFalse(Message.areContentsTheSame(message, message.copy(subscriptionId = 2)))
            assertFalse(Message.areContentsTheSame(message, message.copy(type = 5)))
        } finally { Locale.setDefault(previous) }
    }
}

package org.fossify.messages

import android.provider.Telephony
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.models.Message
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.thread.MessageAction
import org.fossify.messages.ui.thread.ThreadEvent
import org.fossify.messages.ui.components.ThreadTimeline
import org.fossify.messages.ui.thread.ScrollRequest
import org.fossify.messages.ui.thread.ThreadUiState
import org.fossify.messages.ui.components.linkify
import org.fossify.messages.ui.thread.selectionActions
import org.fossify.messages.ui.thread.splitForBar
import org.fossify.messages.ui.thread.starKey
import org.fossify.messages.ui.thread.tapActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses in-memory bubbles only. Does not insert, send, or delete provider messages. */
@RunWith(AndroidJUnit4::class)
class MessageActionChecks {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun message(id: Long, body: String, scheduled: Boolean = false) =
        Message(id, body, Telephony.Sms.MESSAGE_TYPE_SENT, 0, arrayListOf(), 1700000000, true, 987654321, false, null, "", "Fixture", "", -1, isScheduled = scheduled)

    private fun show(state: ThreadUiState, textScale: Float = 1f, onEvent: (ThreadEvent) -> Unit) = compose.setContent {
        OpenLineTheme(dark = false, textScale = textScale) {
            Box(Modifier.size(360.dp, 640.dp)) {
                ThreadTimeline(state, SnackbarHostState(), onEvent)
            }
        }
    }

    @Test fun linksOpenAndStandaloneNumbersCopy() {
        val copied = mutableListOf<String>()
        val text = linkify("Use 123456 here, call +98 912 345 6789 or visit https://example.com/123?q=45 and کد ۱۲۳۴۵۶، مبلغ: 70,000,000 ساعت 13:25.") { copied += it }
        val links = text.getLinkAnnotations(0, text.length)
        val urls = links.filter { it.item is LinkAnnotation.Url }
        assertEquals(1, urls.size)
        assertEquals("https://example.com/123?q=45", (urls.single().item as LinkAnnotation.Url).url)
        val numbers = links.filter { it.item is LinkAnnotation.Clickable }.map { text.substring(it.start, it.end) }
        assertEquals("Digits inside the URL stay part of the link", listOf("123456", "+98 912 345 6789", "۱۲۳۴۵۶", "70,000,000", "13:25"), numbers)
        assertEquals("Plain text gets no links", 0, linkify("A plain recycled message") {}.getLinkAnnotations(0, 24).size)
    }

    @Test fun tapOpensMessageMenu() {
        val body = "Your code is 123456. Visit example.com for details."
        val fixture = message(987654321, body)
        val events = mutableListOf<ThreadEvent>()
        show(ThreadUiState(items = listOf(fixture))) { events += it }
        compose.onNode(hasContentDescription(context.getString(R.string.message_delivered), substring = true)).performClick()
        compose.onNodeWithText(context.getString(org.fossify.commons.R.string.copy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(org.fossify.commons.R.string.delete)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.star_message)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(org.fossify.commons.R.string.share)).assertExists()
        compose.onNodeWithText(context.getString(R.string.forward_message)).performClick()
        // The sheet acts only once it has slid away.
        compose.waitUntil(5_000) { events.any { it is ThreadEvent.Act } }
        assertEquals(ThreadEvent.Act(MessageAction.FORWARD, listOf(fixture)), events.filterIsInstance<ThreadEvent.Act>().single())
    }

    @Test fun longPressOpensActionsOnPlainAndLinkedText() {
        val plain = message(987654323, "A plain message without links")
        val linked = message(987654324, "Code 123456 at example.com today")
        val events = mutableListOf<ThreadEvent>()
        show(ThreadUiState(items = listOf(plain, linked))) { events += it }
        for (fixture in listOf(plain, linked)) {
            events.clear()
            // The last word, since links carry bidi isolates that break a whole-body match.
            compose.onNodeWithText(fixture.body.substringAfterLast(' '), substring = true).performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.inbox_select)).performClick()
            compose.waitUntil(5_000) { events.any { it is ThreadEvent.Act } }
            assertEquals(ThreadEvent.Act(MessageAction.SELECT, listOf(fixture)), events.filterIsInstance<ThreadEvent.Act>().single())
        }
    }

    @Test fun scrollRequestJumpsToAnOffscreenMessage() {
        val fixtures = (1L..60L).map { id ->
            message(id, if (id == 2L) "Offscreen target message" else "Fixture message $id")
        }
        val events = mutableListOf<ThreadEvent>()
        var state by mutableStateOf(ThreadUiState(items = fixtures))
        val listState = LazyListState()
        compose.setContent {
            OpenLineTheme(dark = false, textScale = 1f) {
                Box(Modifier.size(360.dp, 640.dp)) { ThreadTimeline(state, SnackbarHostState(), { events += it }, listState = listState) }
            }
        }
        compose.onNodeWithText("Offscreen target message").assertDoesNotExist()
        state = state.copy(scrollRequest = ScrollRequest.ToMessage(7, 2, isMms = false))
        compose.waitForIdle()
        val targetIndex = fixtures.asReversed().indexOfFirst { it.id == 2L }
        val visibleIndices = listState.layoutInfo.visibleItemsInfo.joinToString { it.index.toString() }
        val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        val target = compose.onNodeWithText("Offscreen target message")
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        val diagnostics = "JumpSettled(7)=${ThreadEvent.JumpSettled(7) in events}; targetIndex=$targetIndex; firstVisible=${listState.firstVisibleItemIndex}; targetOffset=${targetInfo?.offset}; targetSize=${targetInfo?.size}; viewport=${listState.layoutInfo.viewportStartOffset}..${listState.layoutInfo.viewportEndOffset}; targetTextBounds=$targetBounds; visible=[$visibleIndices]"
        assertTrue(
            diagnostics,
            ThreadEvent.JumpSettled(7) in events,
        )
        try {
            target.assertIsDisplayed()
        } catch (failure: AssertionError) {
            throw AssertionError("Scroll request left its target undisplayed. $diagnostics", failure)
        }
    }

    @Test fun largeTextMenuKeepsLastActionReachable() {
        show(ThreadUiState(items = listOf(message(987654322, "Bottom message"))), textScale = 2.5f) {}
        compose.onNode(hasContentDescription(context.getString(R.string.message_delivered), substring = true)).performClick()
        compose.onNodeWithText(context.getString(org.fossify.commons.R.string.share)).performScrollTo().assertIsDisplayed()
    }

    @Test fun selectionBarAndActionRules() {
        val one = message(1, "First")
        val two = message(2, "Second")
        val selectedOne = ThreadUiState(items = listOf(one, two), selected = setOf(one.getStableId()))
        val (bar, overflow) = selectionActions(selectedOne).splitForBar()
        assertEquals(listOf(MessageAction.COPY, MessageAction.FORWARD, MessageAction.STAR, MessageAction.DELETE), bar)
        assertTrue(overflow.containsAll(listOf(MessageAction.SHARE, MessageAction.SELECT_TEXT, MessageAction.DETAILS, MessageAction.SELECT_ALL)))
        assertFalse("Plain messages have nothing to save", MessageAction.SAVE_AS in overflow)

        val both = selectedOne.copy(selected = setOf(one.getStableId(), two.getStableId()))
        val many = selectionActions(both)
        assertFalse(MessageAction.FORWARD in many || MessageAction.SHARE in many || MessageAction.DETAILS in many)

        val starred = both.copy(starred = setOf(one.starKey(), two.starKey()))
        assertTrue(MessageAction.UNSTAR in selectionActions(starred) && MessageAction.STAR !in selectionActions(starred))
        val recycled = both.copy(isRecycleBin = true)
        assertTrue(MessageAction.RESTORE in selectionActions(recycled) && MessageAction.STAR !in selectionActions(recycled))

        assertTrue("Scheduled messages offer edit and send now", MessageAction.DETAILS in tapActions(message(3, "Later", scheduled = true), selectedOne))
        assertFalse(MessageAction.DETAILS in tapActions(one, selectedOne))

        val held = selectedOne.copy(spamReasons = mapOf(one.id to org.fossify.messages.helpers.BLOCK_REASON_KEYWORD))
        assertEquals(listOf(MessageAction.NOT_SPAM, MessageAction.BLOCK_SENDER), tapActions(one.copy(senderPhoneNumber = "fixture-sender"), held))
        assertFalse(MessageAction.BLOCK_SENDER in selectionActions(held))

        val mms = one.copy(isMMS = true)
        val collided = ThreadUiState(items = listOf(one, mms), selected = setOf(one.getStableId()))
        assertEquals("An SMS and an MMS with the same id select separately", listOf(one), collided.selectedMessages)
        val withPhoto = one.copy(id = 4, attachment = org.fossify.messages.models.MessageAttachment(4, "", arrayListOf(
            org.fossify.messages.models.Attachment(null, 4, "content://fixture/photo", "image/jpeg", 0, 0, "photo.jpg"))))
        val mixed = ThreadUiState(items = listOf(withPhoto, two), selected = setOf(withPhoto.getStableId(), two.getStableId()))
        assertTrue("Save as when any selected message has an attachment", MessageAction.SAVE_AS in selectionActions(mixed))

        show(selectedOne) {}
        bar.forEach { compose.onNodeWithText(context.getString(it.label)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(org.fossify.commons.R.string.more_options)).assertIsDisplayed()
    }
}

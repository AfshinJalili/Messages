package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.inbox.InboxRow
import com.afsh.messages.ui.inbox.InboxScreen
import com.afsh.messages.ui.inbox.InboxUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * D01 / 80: the inbox header follows the finger, settles when let go, comes back on any scroll
 * up, stays open while selecting, and keeps its collapse across a restore. Checks only where the
 * search field sits, never how the header is built.
 */
@RunWith(AndroidJUnit4::class)
class InboxHeaderScrollChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private val rows = (1..40L).map {
        InboxRow(Conversation(it, "Message $it", 1, true, "Person $it", "", false, "55501$it"), draft = null, pinned = false, muted = false)
    }

    private fun fieldTop(): Dp = compose.onNodeWithText(text(R.string.inbox_search_hint), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun selectAllTop(): Dp =
        compose.onNodeWithText(text(org.fossify.commons.R.string.select_all), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun near(a: Dp, b: Dp) = abs((a - b).value) < 1f

    private fun settle() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Inbox(state: InboxUiState) {
        DesignVariantContent(DesignVariant.LIGHT) {
            InboxScreen(
                state = state,
                snackbarHostState = remember { SnackbarHostState() },
                onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
                onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
            )
        }
    }

    @Test
    fun headerFollowsTheScroll() {
        compose.setContent { Inbox(InboxUiState(rows = rows)) }
        val open = fieldTop()
        // Mid-drag, before letting go: a few pixels of finger move the field by the same few pixels.
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -viewConfiguration.touchSlop - 1f))
            moveBy(Offset(0f, -30f))
        }
        compose.waitForIdle()
        val mid = fieldTop()
        check(mid < open && open - mid < Dp(40f)) { "Mid-drag the header moves with the finger: $open → $mid" }
        compose.onRoot().performTouchInput { up() }
        settle()
        check(near(fieldTop(), open)) { "A header let go near open settles open" }

        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        check(fieldTop() < open - Dp(20f)) { "A full swipe up collapses the header" }
        Screenshots.capture("80-band-collapsed")
        // On the list, not the header: a finger moving down there scrolls the list up.
        compose.onRoot().performTouchInput { swipeDown(startY = centerY - 200f, endY = centerY + 600f) }
        settle()
        check(near(fieldTop(), open)) { "Scrolling up brings the header back" }
    }

    @Test
    fun selectingOpensTheHeaderAndKeepsItOpen() {
        var state by mutableStateOf(InboxUiState(rows = rows, selected = setOf(1L)))
        compose.setContent { Inbox(state) }
        val open = selectAllTop()

        state = state.copy(selected = emptySet())
        settle()
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        check(fieldTop() < open - Dp(20f)) { "The header collapsed before selecting" }

        state = state.copy(selected = setOf(1L))
        settle()
        check(near(selectAllTop(), open)) { "Selecting opens the header: $open → ${selectAllTop()}" }
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        check(near(selectAllTop(), open)) { "While selecting, scrolling leaves the header open" }
    }

    @Test
    fun collapseSurvivesARestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Inbox(InboxUiState(rows = rows)) }
        val open = fieldTop()
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        val collapsed = fieldTop()
        check(collapsed < open - Dp(20f)) { "The header collapsed before the restore" }

        restoration.emulateSavedInstanceStateRestore()
        settle()
        check(near(fieldTop(), collapsed)) { "A restored inbox keeps its collapse: $collapsed → ${fieldTop()}" }
    }
}

package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D01 / 80: the band follows the finger instead of snapping, settles when let go, and comes back on any scroll up. */
@RunWith(AndroidJUnit4::class)
class BandScrollChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private val rows = (1..40L).map {
        InboxRow(Conversation(it, "Message $it", 1, true, "Person $it", "", false, "55501$it"), draft = null, pinned = false, muted = false)
    }

    private fun fieldTop(): Dp = compose.onNodeWithText(text(R.string.inbox_search_hint), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun bandFollowsTheScroll() {
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                InboxScreen(
                    state = InboxUiState(rows = rows),
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
                    onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
                )
            }
        }
        val open = fieldTop()
        // Mid-drag, before letting go: a few pixels of finger move the field by the same few pixels.
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -viewConfiguration.touchSlop - 1f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, -30f))
        }
        compose.waitForIdle()
        val mid = fieldTop()
        check(mid < open && open - mid < Dp(40f)) { "Mid-drag the band moves with the finger: $open → $mid" }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        Screenshots.capture("80-band-collapsed")
        compose.onRoot().performTouchInput { swipeDown(endY = centerY + 60f) }
        compose.waitForIdle()
        check(kotlin.math.abs((fieldTop() - open).value) < 1f) { "Scrolling up brings the band back" }
    }
}

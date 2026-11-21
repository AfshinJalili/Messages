package com.afsh.messages

import com.afsh.messages.ui.components.AfshIcon
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.AfshTheme
import androidx.compose.ui.platform.testTag
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.BottomBarHeight
import com.afsh.messages.ui.components.SelectionBar
import com.afsh.messages.ui.components.NavCounts
import com.afsh.messages.ui.components.PrimaryNavigation
import com.afsh.messages.ui.components.navCounts
import com.afsh.messages.ui.components.TopDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * On a 360 dp phone, up to 2× text, LTR and RTL: the Library badge sits fully inside its item (the
     * item clips), and every label stays on one line, unellipsized, inside its item (#169). Items are
     * tabs, and at normal text the bar is exactly [BottomBarHeight] tall (#227).
     */
    @Test
    fun bottomBarAtLargeText() {
        var rtl by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            TestTheme(rtl = rtl, fontScale = scale) {
                Box(Modifier.width(360.dp).consumeWindowInsets(WindowInsets.systemBars)) {
                    PrimaryNavigation(TopDestination.LIBRARY, onSelect = {}, modifier = Modifier.testTag(BAR), counts = NavCounts(library = 120))
                }
            }
        }
        val resources = compose.activity.resources
        val badge = compose.activity.localizedNumber(99) + "+"
        compose.onAllNodes(isNavigationItem()).assertCountEquals(TopDestination.entries.size)
        compose.onNode(isNavigationItem(resources.getString(TopDestination.LIBRARY.label))).assertIsSelected()
        // Bars that stand in for the navigation read its height from BottomBarHeight; at normal text they must match.
        val bar = compose.onAllNodesWithTag(BAR).fetchSemanticsNodes().single().let { with(compose.density) { it.size.height.toDp() } }
        check((bar - BottomBarHeight).value in -0.5f..0.5f) { "The navigation bar is $bar tall, BottomBarHeight is $BottomBarHeight" }
        for (fontScale in listOf(1f, 1.3f, 2f)) for (direction in listOf(false, true)) {
            scale = fontScale
            rtl = direction
            compose.waitForIdle()
            val where = "scale=$fontScale rtl=$direction"
            for (destination in TopDestination.entries) {
                val label = resources.getString(destination.label)
                val item = compose.onNode(isNavigationItem(label)).getUnclippedBoundsInRoot()
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                val layout = text.textLayout()
                check(layout.lineCount == 1 && !layout.isLineEllipsized(0)) { "$label is cut ($where)" }
                check(text.getUnclippedBoundsInRoot() inside item) { "$label leaves its item ($where)" }
                if (destination == TopDestination.LIBRARY) {
                    val badgeBounds = compose.onNodeWithText(badge, useUnmergedTree = true).getUnclippedBoundsInRoot()
                    check(badgeBounds inside item) { "Badge is clipped ($where): $badgeBounds in $item" }
                }
            }
        }
    }

    /** The Library badge stays whichever destination is open; Settings used to drop it. */
    @Test
    fun libraryBadgeShowsOnEveryDestination() {
        var selected by mutableStateOf(TopDestination.INBOX)
        compose.setContent {
            AfshTheme(dark = false) {
                PrimaryNavigation(selected, onSelect = {}, counts = NavCounts(library = 7))
            }
        }
        val seven = compose.activity.localizedNumber(7)
        for (destination in TopDestination.entries) {
            selected = destination
            compose.waitForIdle()
            compose.onNodeWithText(seven, useUnmergedTree = true).assertExists("No Library badge on $destination")
        }
    }

    /** The Inbox badge counts unread messages, and only when its setting is on (it is off by default). */
    @Test
    fun inboxBadgeFollowsItsSetting() {
        check(navCounts(unreadSpam = 2, unreadMessages = 5, inboxCountShown = false) == NavCounts(library = 2, inbox = 0))
        check(navCounts(unreadSpam = 2, unreadMessages = 5, inboxCountShown = true) == NavCounts(library = 2, inbox = 5))
        check(NavCounts(library = 2, inbox = 5).of(TopDestination.INBOX) == 5)
        check(NavCounts(library = 2, inbox = 5).of(TopDestination.LIBRARY) == 2)
        check(NavCounts(library = 2, inbox = 5).of(TopDestination.SETTINGS) == 0)
        var shown by mutableStateOf(false)
        compose.setContent {
            AfshTheme(dark = false) {
                PrimaryNavigation(TopDestination.SETTINGS, onSelect = {}, counts = navCounts(0, 5, shown))
            }
        }
        val five = compose.activity.localizedNumber(5)
        compose.onNodeWithText(five, useUnmergedTree = true).assertDoesNotExist()
        shown = true
        compose.waitForIdle()
        compose.onNodeWithText(five, useUnmergedTree = true).assertExists()
    }

    /**
     * The selection bar (Material 3's bottom app bar) is exactly as tall as the navigation bar it replaces;
     * its actions are found by their labels, run in reading order in LTR and RTL, and extras open from More options.
     */
    @Test
    fun selectionBarStandsInForTheNavigation() {
        var rtl by mutableStateOf(false)
        var clicked: String? = null
        val actions = listOf("Archive", "Pin", "Delete").map { label -> BarAction(label, AfshIcon.Check, label == "Delete") { clicked = label } }
        val overflow = listOf(BarAction("Block", AfshIcon.Check) { clicked = "Block" })
        compose.setContent {
            TestTheme(rtl = rtl) {
                Box(Modifier.width(360.dp).consumeWindowInsets(WindowInsets.systemBars)) {
                    SelectionBar(actions, Modifier.testTag(BAR), overflow = overflow)
                }
            }
        }
        val bar = compose.onAllNodesWithTag(BAR).fetchSemanticsNodes().single().let { with(compose.density) { it.size.height.toDp() } }
        check((bar - BottomBarHeight).value in -0.5f..0.5f) { "The selection bar is $bar tall, BottomBarHeight is $BottomBarHeight" }
        for (direction in listOf(false, true)) {
            rtl = direction
            compose.waitForIdle()
            val archive = compose.onNodeWithContentDescription("Archive").getUnclippedBoundsInRoot()
            val delete = compose.onNodeWithContentDescription("Delete").getUnclippedBoundsInRoot()
            check(if (direction) archive.left > delete.left else archive.left < delete.left) { "Actions out of reading order (rtl=$direction)" }
        }
        compose.onNodeWithContentDescription("Pin").performClick()
        check(clicked == "Pin") { "Pin did not act" }
        compose.onNodeWithContentDescription(compose.activity.getString(com.afsh.commons.R.string.more_options)).performClick()
        compose.onNodeWithText("Block").assertIsDisplayed().performClick()
        check(clicked == "Block") { "Block did not act" }
    }

    private companion object {
        const val BAR = "navigation"
    }
}

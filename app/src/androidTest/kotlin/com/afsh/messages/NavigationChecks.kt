package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.AfshTheme
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
     * item clips), and every label stays on one line, unellipsized, inside its item (#169).
     */
    @Test
    fun bottomBarAtLargeText() {
        var rtl by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = scale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                AfshTheme(dark = false) {
                    Box(Modifier.width(360.dp)) {
                        PrimaryNavigation(TopDestination.LIBRARY, onSelect = {}, counts = NavCounts(library = 120))
                    }
                }
            }
        }
        val resources = compose.activity.resources
        val badge = String.format(resources.configuration.locales[0], "%d", 99) + "+"
        for (fontScale in listOf(1f, 1.3f, 2f)) for (direction in listOf(false, true)) {
            scale = fontScale
            rtl = direction
            compose.waitForIdle()
            val where = "scale=$fontScale rtl=$direction"
            for (destination in TopDestination.entries) {
                val label = resources.getString(destination.label)
                val item = compose.onNode(isSelectable() and hasText(label, substring = true)).getUnclippedBoundsInRoot()
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                val layout = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
                check(layout.single().lineCount == 1 && !layout.single().isLineEllipsized(0)) { "$label is cut ($where)" }
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
        val seven = String.format(compose.activity.resources.configuration.locales[0], "%d", 7)
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
        val five = String.format(compose.activity.resources.configuration.locales[0], "%d", 5)
        compose.onNodeWithText(five, useUnmergedTree = true).assertDoesNotExist()
        shown = true
        compose.waitForIdle()
        compose.onNodeWithText(five, useUnmergedTree = true).assertExists()
    }

    private infix fun DpRect.inside(outer: DpRect): Boolean {
        val slack = 0.5.dp
        return left >= outer.left - slack && top >= outer.top - slack && right <= outer.right + slack && bottom <= outer.bottom + slack
    }
}

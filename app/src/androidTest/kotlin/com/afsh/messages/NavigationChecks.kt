package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.AfshTheme
import androidx.compose.ui.platform.testTag
import com.afsh.messages.ui.components.BottomBarHeight
import com.afsh.messages.ui.components.PrimaryNavigation
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
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = scale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                AfshTheme(dark = false) {
                    Box(Modifier.width(360.dp).consumeWindowInsets(WindowInsets.systemBars)) {
                        PrimaryNavigation(TopDestination.LIBRARY, onSelect = {}, modifier = Modifier.testTag(BAR), unreadSpam = 120)
                    }
                }
            }
        }
        val resources = compose.activity.resources
        val badge = String.format(resources.configuration.locales[0], "%d", 99) + "+"
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

    private companion object {
        const val BAR = "navigation"
    }

    private infix fun DpRect.inside(outer: DpRect): Boolean {
        val slack = 0.5.dp
        return left >= outer.left - slack && top >= outer.top - slack && right <= outer.right + slack && bottom <= outer.bottom + slack
    }
}

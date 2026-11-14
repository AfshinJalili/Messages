package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.and
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.PageSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared page app bar and scaffold: title, Back, selection mode, scrolling and RTL, found by role and description. */
@RunWith(AndroidJUnit4::class)
class PageScaffoldChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val back get() = text(org.fossify.commons.R.string.back)
    private val close get() = text(org.fossify.commons.R.string.close)
    private val selectAll get() = text(org.fossify.commons.R.string.select_all)
    private fun heading(title: String) = compose.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    private fun page(
        selected: Int = 0,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onBack: () -> Unit = {},
        onClear: () -> Unit = {},
        onSelectAll: () -> Unit = {},
    ) = compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AfshTheme(dark = false) {
                PageScaffold(
                    title = "Archive",
                    subtitle = "Kept out of the inbox",
                    onBack = onBack,
                    selection = if (selected > 0) {
                        PageSelection(selected, onClear = onClear, actions = listOf(BarAction("Unarchive", R.drawable.ic_lucide_inbox) {}), onSelectAll = onSelectAll)
                    } else {
                        null
                    },
                ) {
                    LazyColumn { items(60) { Text("Row $it", Modifier.fillMaxWidth().padding(16.dp)) } }
                }
            }
        }
    }

    @Test
    fun titleIsAHeadingAndBackLeaves() {
        var backs = 0
        page(onBack = { backs++ })
        heading("Archive").assertIsDisplayed()
        compose.onNodeWithText("Kept out of the inbox").assertIsDisplayed()
        compose.onNodeWithContentDescription(back).performClick()
        check(backs == 1) { "Back calls onBack: $backs" }
    }

    @Test
    fun selectionShowsCountWithCloseAndSelectAll() {
        var clears = 0
        var selectAlls = 0
        page(selected = 2, onClear = { clears++ }, onSelectAll = { selectAlls++ })
        heading(resourcesPlural(R.plurals.inbox_selected, 2)).assertIsDisplayed()
        check(compose.onAllNodes(hasText("Archive")).fetchSemanticsNodes().isEmpty()) { "The page title gives way to the count" }
        compose.onNodeWithContentDescription(selectAll).performClick()
        check(selectAlls == 1) { "Select all: $selectAlls" }
        compose.onNodeWithContentDescription(close).performClick()
        check(clears == 1) { "Close clears: $clears" }
        compose.onNodeWithContentDescription("Unarchive").assertExists()
    }

    @Test
    fun systemBackClearsSelection() {
        var clears = 0
        var backs = 0
        page(selected = 1, onBack = { backs++ }, onClear = { clears++ })
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        check(clears == 1 && backs == 0) { "Back clears the selection first: clears=$clears backs=$backs" }
    }

    @Test
    fun barStaysWhileThePageScrolls() {
        page()
        val before = heading("Archive").getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = heading("Archive").getUnclippedBoundsInRoot()
        check(before == after) { "The pinned bar does not move: $before → $after" }
    }

    @Test
    fun rightToLeftPutsBackAtTheStart() {
        page(direction = LayoutDirection.Rtl)
        val up = compose.onNodeWithContentDescription(back).getUnclippedBoundsInRoot()
        val title = heading("Archive").getUnclippedBoundsInRoot()
        check(up.left > title.right) { "In RTL Back sits right of the title: back=$up title=$title" }
    }

    private fun resourcesPlural(id: Int, count: Int) = compose.activity.resources.getQuantityString(id, count, count)
}

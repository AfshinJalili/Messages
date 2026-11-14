package com.afsh.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared sheet frame, in-memory only. */
@RunWith(AndroidJUnit4::class)
class SheetChecks {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var open by mutableStateOf(true)

    private fun show(rtl: Boolean = false, showTitle: Boolean = true) = compose.setContent {
        AfshTheme(dark = false) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize()) {
                    if (open) {
                        AfshSheet(title = "Fixture sheet", onDismiss = { calls += "dismiss"; open = false }, showTitle = showTitle) {
                            AfshSheetItem("First", org.fossify.commons.R.drawable.ic_person_vector, onClick = { dismissThen { calls += "first" } }, supporting = "Supporting line", tile = true)
                            AfshSheetItem("Remove", org.fossify.commons.R.drawable.ic_delete_vector, onClick = { dismissThen { calls += "remove" } }, danger = true)
                        }
                    }
                }
            }
        }
    }

    @Test fun titleIsTheHeadingAndPaneTitle() {
        show()
        compose.onNode(isSheet("Fixture sheet")).assertExists()
        compose.onNode(hasText("Fixture sheet") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        compose.onNode(sheetAction("First", "Fixture sheet")).assertIsDisplayed()
        compose.onNode(sheetAction("Remove", "Fixture sheet")).assertIsDisplayed()
    }

    @Test fun hiddenTitleStillNamesThePane() {
        show(showTitle = false)
        compose.onNode(isSheet("Fixture sheet")).assertExists()
        compose.onNode(hasText("Fixture sheet")).assertDoesNotExist()
    }

    @Test fun rowActsOnlyAfterTheSheetHasGone() {
        show()
        compose.onNode(sheetAction("First")).performClick()
        compose.waitUntil(5_000) { "first" in calls }
        assertEquals(listOf("dismiss", "first"), calls)
        compose.onNode(isSheet("Fixture sheet")).assertDoesNotExist()
    }

    @Test fun rowsWorkRightToLeft() {
        show(rtl = true)
        compose.onNode(hasText("Supporting line") and hasAnyAncestor(isSheet("Fixture sheet"))).assertExists()
        compose.onNode(sheetAction("Remove")).performClick()
        compose.waitUntil(5_000) { "remove" in calls }
        assertEquals(listOf("dismiss", "remove"), calls)
    }
}

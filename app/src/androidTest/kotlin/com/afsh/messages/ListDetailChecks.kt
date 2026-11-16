package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.components.LocalListPane
import com.afsh.messages.ui.components.TopDestination
import com.afsh.messages.ui.inbox.InboxUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 85: as the 456 dp list pane of a wide window, the inbox keeps the rail instead of a bottom bar. */
@RunWith(AndroidJUnit4::class)
class ListDetailChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = InboxUiState(rows = sampleInboxRows)

    @Test
    fun listPaneUsesTheRail() {
        var pane by mutableStateOf(true)
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                CompositionLocalProvider(LocalListPane provides pane) {
                    Box(Modifier.width(456.dp).fillMaxHeight()) {
                        TestInbox(state)
                    }
                }
            }
        }
        val library = compose.activity.getString(TopDestination.LIBRARY.label)
        val settings = compose.activity.getString(TopDestination.SETTINGS.label)
        fun stacked(): Boolean {
            val a = compose.onNode(isNavigationItem(library)).getUnclippedBoundsInRoot()
            val b = compose.onNode(isNavigationItem(settings)).getUnclippedBoundsInRoot()
            return b.top > a.bottom
        }
        check(stacked()) { "The list pane must show the rail" }
        Screenshots.capture("85-list-pane")
        pane = false
        compose.waitForIdle()
        check(!stacked()) { "A narrow window on its own keeps the bottom bar" }
    }
}

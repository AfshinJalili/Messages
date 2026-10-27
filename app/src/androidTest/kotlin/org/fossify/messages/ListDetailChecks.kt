package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.components.LocalListPane
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 85: as the 456 dp list pane of a wide window, the inbox keeps the rail instead of a bottom bar. */
@RunWith(AndroidJUnit4::class)
class ListDetailChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = InboxUiState(
        rows = listOf(
            InboxRow(Conversation(1, "The train leaves at six.", 1, true, "Mina Farahani", "", false, "5550142"), draft = null, pinned = false, muted = false),
            InboxRow(Conversation(2, "Photo • That’s the one!", 1, true, "Dad", "", false, "5550176"), draft = null, pinned = false, muted = false),
        ),
    )

    @Test
    fun listPaneUsesTheRail() {
        var pane by mutableStateOf(true)
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                CompositionLocalProvider(LocalListPane provides pane) {
                    Box(Modifier.width(456.dp).fillMaxHeight()) {
                        InboxScreen(
                            state = state,
                            snackbarHostState = remember { SnackbarHostState() },
                            onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
                            onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
                        )
                    }
                }
            }
        }
        val library = compose.activity.getString(TopDestination.LIBRARY.label)
        val settings = compose.activity.getString(TopDestination.SETTINGS.label)
        fun stacked(): Boolean {
            val a = compose.onAllNodesWithText(library, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
            val b = compose.onAllNodesWithText(settings, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
            return b.top > a.bottom
        }
        check(stacked()) { "The list pane must show the rail" }
        Screenshots.capture("85-list-pane")
        pane = false
        compose.waitForIdle()
        check(!stacked()) { "A narrow window on its own keeps the bottom bar" }
    }
}

package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxUiState
import org.fossify.messages.ui.thread.COMPOSER_SEND_TAG
import org.fossify.messages.ui.thread.ComposerState
import org.fossify.messages.ui.thread.ThreadComposer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 73 (inbox refresh failed) and 67 (blocked sender): behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class StateNoticeChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private val rows = listOf(
        InboxRow(Conversation(1, "The train leaves at six.", 1, true, "Mina Farahani", "", false, "5550142"), draft = null, pinned = false, muted = false),
        InboxRow(Conversation(2, "Photo • That’s the one!", 1, true, "Dad", "", false, "5550176"), draft = null, pinned = false, muted = false),
    )

    @Composable
    private fun Inbox(state: InboxUiState, onRetry: () -> Unit) = InboxScreen(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
        onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
        onRetryRefresh = onRetry,
    )

    @Test
    fun refreshFailureKeepsTheListAndRetries() {
        var retried = 0
        var state by mutableStateOf(InboxUiState(rows = rows, refreshFailed = true))
        compose.setContent { DesignVariantContent(DesignVariant.LIGHT) { Inbox(state) { retried++ } } }
        compose.onNodeWithText(text(R.string.refresh_failed_title)).assertExists()
        compose.onNodeWithText("Mina Farahani").assertExists()
        compose.onNodeWithText(text(R.string.retry)).performClick()
        check(retried == 1) { "Retry ran $retried times" }
        state = state.copy(refreshFailed = false)
        compose.onNodeWithText(text(R.string.refresh_failed_title)).assertDoesNotExist()
    }

    @Test
    fun blockedSenderGetsNoComposer() {
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                ThreadComposer(ComposerState(blocked = true), "", TextFieldValue(""), false, {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.unblock_to_reply)).assertExists()
        compose.onNodeWithTag(COMPOSER_SEND_TAG).assertDoesNotExist()
    }

    @Test
    fun noticesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var page by mutableStateOf(0)
        compose.setContent {
            DesignVariantContent(variant) {
                if (page == 0) {
                    Inbox(InboxUiState(rows = rows, refreshFailed = true)) {}
                } else {
                    ThreadComposer(ComposerState(blocked = true), "", TextFieldValue(""), false, {}, {})
                }
            }
        }
        listOf("73-refresh-failed", "67-blocked").forEachIndexed { index, name ->
            page = index
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
    }
}

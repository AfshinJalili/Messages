package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.inbox.InboxUiState
import com.afsh.messages.ui.thread.ComposerState
import com.afsh.messages.ui.thread.ThreadComposer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 73 (inbox refresh failed) and 67 (blocked sender): behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class StateNoticeChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private val rows = sampleInboxRows

    @Test
    fun refreshFailureKeepsTheListAndRetries() {
        var retried = 0
        var state by mutableStateOf(InboxUiState(rows = rows, refreshFailed = true))
        compose.setContent { DesignVariantContent(DesignVariant.LIGHT) { TestInbox(state, onRetryRefresh = { retried++ }) } }
        compose.onNodeWithText(text(R.string.refresh_failed_title)).assertExists()
        // A row merges its text into one node for TalkBack.
        compose.onNodeWithText("Mina Farahani", useUnmergedTree = true).assertExists()
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
        // Neither a field nor a send button, found as TalkBack finds them.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.send_message)).assertDoesNotExist()
    }

    @Test
    fun noticesAreCapturedInEveryVariant() {
        val capture = VariantCapture(compose)
        capture.show { page ->
            if (page == 0) {
                TestInbox(InboxUiState(rows = rows, refreshFailed = true))
            } else {
                ThreadComposer(ComposerState(blocked = true), "", TextFieldValue(""), false, {}, {})
            }
        }
        capture.captureEach(listOf("73-refresh-failed", "67-blocked"))
    }
}

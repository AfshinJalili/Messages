package com.afsh.messages

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.ui.thread.ComposerEvent
import com.afsh.messages.ui.thread.ComposerState
import com.afsh.messages.ui.thread.ThreadComposer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The composer, found the way TalkBack finds it: by role, label and text, not by test tags. */
@RunWith(AndroidJUnit4::class)
class ComposerChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val events = mutableListOf<ComposerEvent>()
    private var state by mutableStateOf(ComposerState())
    private var value by mutableStateOf(TextFieldValue())

    private fun show() = compose.setContent {
        DesignVariantContent(DesignVariant.LIGHT) {
            ThreadComposer(state, "Mina", value, sendOnEnter = false, onTextChange = { value = it }, onEvent = { events += it })
        }
    }

    @Test
    fun emptyFieldOffersDictationAndTextOffersSend() {
        show()
        compose.onNodeWithText(text(R.string.composer_hint_name, "Mina"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.speak_message)).assert(isButton).assertIsEnabled().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Hello")
        compose.onNodeWithContentDescription(text(R.string.send_message)).assert(isButton).assertIsEnabled().performClick()
        assertEquals(listOf(ComposerEvent.Dictate, ComposerEvent.Send), events)
    }

    @Test
    fun aScheduledSendWaitsForContent() {
        state = ComposerState(scheduledAt = "Tomorrow 9:00")
        show()
        compose.onNodeWithContentDescription(text(R.string.schedule_message)).assertIsNotEnabled()
        compose.onNodeWithText("Tomorrow 9:00").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.cancel_schedule_send)).performClick()
        assertEquals(listOf<ComposerEvent>(ComposerEvent.CancelSchedule), events)
    }

    @Test
    fun theCounterAndMmsLabelAreRead() {
        state = ComposerState(smsParts = 2, smsLeft = 18)
        value = TextFieldValue("x")
        show()
        compose.onNodeWithContentDescription(text(R.string.composer_sms_counter, 18, 2)).assertIsDisplayed()
        state = ComposerState(isMms = true)
        compose.onNodeWithContentDescription(text(R.string.mms)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.composer_send_mms)).assert(isButton).assertIsEnabled()
    }

    @Test
    fun aPendingAttachmentBlocksSendAndCanBeRemoved() {
        val pending = AttachmentSelection("a", Uri.EMPTY, "image/jpeg", "photo.jpg", isPending = true)
        state = ComposerState(attachments = listOf(pending))
        value = TextFieldValue("Caption")
        show()
        compose.onNodeWithContentDescription(text(R.string.send_message)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.composer_remove_attachment)).assert(isButton).performClick()
        assertEquals(listOf<ComposerEvent>(ComposerEvent.RemoveAttachment(pending)), events)
    }

    @Test
    fun theAttachButtonOpensTheSheet() {
        show()
        compose.onNodeWithContentDescription(text(R.string.attachment)).assert(isButton).performClick()
        compose.onNodeWithText(text(R.string.attach_title)).assertIsDisplayed()
    }

    @Test
    fun aSenderThatTakesNoRepliesShowsOnlyTheNotice() {
        state = ComposerState(canReply = false)
        show()
        compose.onNodeWithText(text(R.string.composer_cant_reply)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(text(R.string.send_message)).assertDoesNotExist()
        assertEquals(listOf<ComposerEvent>(ComposerEvent.ReplyInfo), events)
    }
}

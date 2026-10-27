package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.ui.newmessage.NewMessageEvent
import org.fossify.messages.ui.newmessage.NewMessageScreen
import org.fossify.messages.ui.newmessage.NewMessageUiState
import org.fossify.messages.ui.newmessage.Person
import org.fossify.messages.ui.newmessage.PhoneChoice
import org.fossify.messages.ui.newmessage.Recipient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 05, 16 and 42 on sample contacts: behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class NewMessageChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun person(name: String, vararg numbers: String) = Person(name, "", numbers.map { PhoneChoice(it, "Mobile") })
    private val mina = person("Mina Farahani", "5550142")
    private val dad = person("Dad", "5550176")
    private val samira = person("Samira", "5550128", "5550199")
    private val state = NewMessageUiState(
        suggested = listOf(mina, dad, samira),
        contacts = listOf(person("Amir Shah", "5550129"), dad, mina, person("Nadia", "5550133"), samira),
    )

    private fun show(onEvent: (NewMessageEvent) -> Unit) = compose.setContent {
        DesignVariantContent(DesignVariant.LIGHT) { NewMessageScreen(state, onEvent) }
    }

    private val search get() = compose.onAllNodes(hasSetTextAction()).onFirst()

    @Test
    fun suggestionWritesToThatPersonAndSeveralNumbersAsk() {
        val events = mutableListOf<NewMessageEvent>()
        show { events += it }
        compose.onAllNodesWithText("Dad").onFirst().performClick()
        check(events.last() == NewMessageEvent.Write(listOf(Recipient("5550176", "Dad")))) { "Events $events" }

        compose.onAllNodesWithText("Samira").onFirst().performClick()
        compose.onNodeWithText("5550199").performClick()
        check(events.last() == NewMessageEvent.Write(listOf(Recipient("5550199", "Samira")))) { "Events $events" }
    }

    @Test
    fun aNumberThatCannotTakeAnSmsStopsAtCheckTheNumber() {
        val events = mutableListOf<NewMessageEvent>()
        show { events += it }
        search.performTextInput("+1 555 01#*")
        search.performImeAction()
        compose.onNodeWithText(text(R.string.check_the_number)).assertExists()
        check(events.isEmpty()) { "An invalid number must not open a thread: $events" }
        compose.onNodeWithText(text(R.string.edit_number)).performClick()
        compose.onNodeWithText(text(R.string.new_message)).assertExists()
    }

    @Test
    fun writableAddresses() {
        listOf("0912 555 0100", "+98 (912) 555-0100", "300", "mina@example.test").forEach {
            check(org.fossify.messages.ui.newmessage.isWritableAddress(it)) { "$it should be writable" }
        }
        listOf("12", "MCI", "+1 555 01#*", "555 01AB", "+").forEach {
            check(!org.fossify.messages.ui.newmessage.isWritableAddress(it)) { "$it should stop at 42" }
        }
    }

    @Test
    fun aGroupNeedsTwoPeople() {
        val events = mutableListOf<NewMessageEvent>()
        show { events += it }
        compose.onNodeWithText(text(R.string.start_a_group)).performClick()
        compose.onNodeWithText("Nadia").performClick()
        val next = compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.continue_with_people, 1, 1))
        next.assertIsNotEnabled()
        compose.onNodeWithText("Amir Shah").performClick()
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.continue_with_people, 2, 2))
            .assertIsEnabled()
            .performClick()
        check(
            events.last() == NewMessageEvent.Write(listOf(Recipient("5550133", "Nadia"), Recipient("5550129", "Amir Shah"))),
        ) { "Events $events" }
    }

    @Test
    fun newMessagePagesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        compose.setContent { DesignVariantContent(variant) { NewMessageScreen(state, {}) } }
        fun captureAll(name: String) = DesignVariant.entries.forEach {
            variant = it
            compose.waitForIdle()
            Screenshots.capture(name + it.suffix)
        }
        captureAll("05-new-message")
        compose.onNodeWithText(text(R.string.start_a_group)).performClick()
        compose.onNodeWithText("Nadia").performClick()
        compose.onNodeWithText("Amir Shah").performClick()
        captureAll("16-new-group")
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        search.performTextInput("+1 555 01#*")
        search.performImeAction()
        captureAll("42-invalid-number")
    }
}

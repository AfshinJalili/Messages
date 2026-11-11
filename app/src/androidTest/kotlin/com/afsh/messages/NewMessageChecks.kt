package com.afsh.messages

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
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.newmessage.NewMessageEvent
import com.afsh.messages.ui.newmessage.NewMessageScreen
import com.afsh.messages.ui.newmessage.NewMessageUiState
import com.afsh.messages.ui.newmessage.Person
import com.afsh.messages.ui.newmessage.PhoneChoice
import com.afsh.messages.ui.newmessage.Recipient
import com.afsh.messages.ui.ltr
import com.afsh.messages.ui.thread.ManagePeoplePanel
import com.afsh.messages.ui.thread.ManagePeopleState
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
    fun managePeopleKeepsTheFirstPersonAndAddsFromContactsAndNumbers() {
        val added = mutableListOf<Recipient>()
        val removed = mutableListOf<Recipient>()
        var people by mutableStateOf(listOf(Recipient("5550142", "Mina Farahani"), Recipient("5550176", "Dad")))
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                ManagePeoplePanel(
                    ManagePeopleState(open = true, people = people, contacts = state),
                    onAdd = { added += it; people = people + it },
                    onRemove = { removed += it; people = people - it },
                    onDone = {},
                    onAllowContacts = {},
                )
            }
        }
        // The conversation's own person has no remove button; the others do.
        compose.onNodeWithContentDescription(text(R.string.remove_person, "Mina Farahani")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.remove_person, "Dad")).performClick()
        check(removed == listOf(Recipient("5550176", "Dad"))) { "Removed $removed" }

        search.performTextInput("Nadia")
        compose.onAllNodesWithText("Nadia").onLast().performClick()
        check(added.last() == Recipient("5550133", "Nadia")) { "Added $added" }

        search.performTextInput("0912 555 0100")
        compose.onNodeWithText(text(R.string.add_number, ltr("0912 555 0100"))).performClick()
        check(added.last() == Recipient("0912 555 0100", "0912 555 0100")) { "Added $added" }
    }

    @Test
    fun writableAddresses() {
        listOf("0912 555 0100", "+98 (912) 555-0100", "300", "mina@example.test").forEach {
            check(com.afsh.messages.ui.newmessage.isWritableAddress(it)) { "$it should be writable" }
        }
        listOf("12", "MCI", "+1 555 01#*", "555 01AB", "+").forEach {
            check(!com.afsh.messages.ui.newmessage.isWritableAddress(it)) { "$it should stop at 42" }
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
        // Ends back in light English, so the next step can find its labels.
        fun captureAll(name: String) {
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
            variant = DesignVariant.LIGHT
            compose.waitForIdle()
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

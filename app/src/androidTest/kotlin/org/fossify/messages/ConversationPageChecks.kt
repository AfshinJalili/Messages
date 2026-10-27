package org.fossify.messages

import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Message
import org.fossify.messages.ui.components.ConfirmationDialog
import org.fossify.messages.ui.components.RenameDialog
import org.fossify.messages.ui.details.ConversationDetailsScreen
import org.fossify.messages.ui.details.ConversationDetailsUiState
import org.fossify.messages.ui.details.DetailsEvent
import org.fossify.messages.ui.details.Member
import org.fossify.messages.ui.details.SimChoice
import org.fossify.messages.ui.message.CardField
import org.fossify.messages.ui.message.ContactCard
import org.fossify.messages.ui.message.ContactCardScreen
import org.fossify.messages.ui.message.ContactCardUiState
import org.fossify.messages.ui.message.DeliveryMark
import org.fossify.messages.ui.message.FieldAction
import org.fossify.messages.ui.message.MessageDetailsScreen
import org.fossify.messages.ui.message.MessageFacts
import org.fossify.messages.ui.message.SelectTextScreen
import org.fossify.messages.ui.schedule.ScheduleTarget
import org.fossify.messages.ui.schedule.ScheduledEvent
import org.fossify.messages.ui.schedule.ScheduledMessageScreen
import org.fossify.messages.ui.schedule.SendLaterScreen
import org.fossify.messages.ui.thread.ThreadTimeline
import org.fossify.messages.ui.thread.ThreadUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** 09, 12, 13, 14, 39, 44, 47, 82 and 84 on sample data: behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class ConversationPageChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /** Tomorrow at 09:00, so the pages never depend on when the test runs. */
    private val tomorrowNine = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val target = ScheduleTarget("I’ll send you the details tomorrow.", "To Mina Farahani · SIM 1")

    private val details = ConversationDetailsUiState(
        title = "Mina Farahani",
        members = listOf(Member("Mina Farahani", "555 0142", "")),
        canDial = true,
        sims = listOf(SimChoice(1, "SIM 1 · Hamrah"), SimChoice(2, "SIM 2 · Irancell")),
        loading = false,
    )

    private val facts = MessageFacts(
        body = "Yes, see you there.",
        time = "12:42",
        mark = DeliveryMark.DELIVERED,
        rows = listOf("Type" to "SMS", "From" to "You · SIM 1", "To" to "Mina Farahani", "Sent" to "Today, 12:42", "Status" to "Delivered"),
    )

    private val card = ContactCardUiState(
        cards = listOf(
            ContactCard(
                "Amir Shah",
                listOf(
                    CardField("Mobile", "555 0129", FieldAction.DIAL),
                    CardField("Email", "amir@example.test", FieldAction.EMAIL),
                    CardField("Company", "Atelier North"),
                ),
            ),
        ),
        source = "Shared by Mina · Today",
    )

    private fun scheduled() = Message(
        7, "Happy birthday Mina! Dinner is on me tonight.", Telephony.Sms.MESSAGE_TYPE_QUEUED, 0, arrayListOf(),
        (tomorrowNine / 1000).toInt(), true, 987654321, false, null, "", "", "", -1, isScheduled = true,
    )

    @Test
    fun detailsActionsAndSimChoice() {
        val events = mutableListOf<DetailsEvent>()
        compose.setContent { DesignVariantContent(DesignVariant.LIGHT) { ConversationDetailsScreen(details, { events += it }) } }
        compose.onAllNodesWithText(text(org.fossify.commons.R.string.call)).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        compose.onNodeWithText(text(R.string.mute)).performClick()
        compose.onNodeWithText(text(R.string.conversation_name)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.sim)).performScrollTo().performClick()
        compose.onNodeWithText("SIM 2 · Irancell").performClick()
        check(
            events == listOf(DetailsEvent.Call, DetailsEvent.ToggleMute, DetailsEvent.Rename, DetailsEvent.SetSim(2)),
        ) { "Events $events" }
    }

    @Test
    fun renameNeedsANameAndTrimsIt() {
        var renamed: String? = null
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                RenameDialog(
                    title = text(R.string.rename_conversation),
                    current = "",
                    placeholder = "Book club",
                    explanation = text(R.string.rename_explanation),
                    confirmLabel = text(R.string.save_name),
                    dismissLabel = text(org.fossify.commons.R.string.cancel),
                    onRename = { renamed = it },
                    onDismiss = {},
                )
            }
        }
        val save = compose.onNodeWithText(text(R.string.save_name))
        save.assertIsNotEnabled()
        compose.onNodeWithText("Book club").performTextInput("  Readers  ")
        save.assertIsEnabled().performClick()
        check(renamed == "Readers") { "Renamed $renamed" }
    }

    @Test
    fun scheduledEditSavesOnlyAChange() {
        val events = mutableListOf<ScheduledEvent>()
        var time by mutableStateOf(tomorrowNine)
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                ScheduledMessageScreen(target, time, timeChanged = time != tomorrowNine, onEvent = { events += it })
            }
        }
        val save = compose.onNodeWithText(text(R.string.save_changes))
        save.assertIsNotEnabled()
        time = tomorrowNine + 3_600_000
        save.assertIsEnabled()
        time = tomorrowNine
        compose.onNodeWithText(target.text).performTextClearance()
        save.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.send_now)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.cancel_scheduled_message)).performScrollTo().performClick()
        check(events == listOf(ScheduledEvent.SendNow, ScheduledEvent.Cancel)) { "Events $events" }
    }

    @Test
    fun sendLaterRefusesThePast() {
        var chosen: Long? = null
        val now = tomorrowNine + 1
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                SendLaterScreen(target, tomorrowNine, onBack = {}, onSchedule = { chosen = it }, now = { now })
            }
        }
        compose.onNodeWithText(text(R.string.must_pick_time_in_the_future)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.schedule_message)).assertIsNotEnabled()
        check(chosen == null)
    }

    @Test
    fun selectTextCopiesTheWholeMessageWithoutASelection() {
        var copied: String? = null
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                SelectTextScreen("Mina Farahani · 12:41", "The address is 24 Willow Street. See you there.", {}, { copied = it })
            }
        }
        compose.onNodeWithText(text(R.string.copy_whole_message)).performClick()
        check(copied == "The address is 24 Willow Street. See you there.") { "Copied $copied" }
    }

    @Test
    fun scheduledMessageTapOpensItsSheet() {
        compose.setContent {
            DesignVariantContent(DesignVariant.LIGHT) {
                Box(Modifier.fillMaxSize()) { ThreadTimeline(ThreadUiState(items = listOf(scheduled())), remember { SnackbarHostState() }, {}) }
            }
        }
        compose.onNodeWithText("Happy birthday", substring = true).performClick()
        compose.onNodeWithText(text(R.string.edit_message_and_time)).assertExists()
        compose.onNodeWithText(text(R.string.send_now)).assertExists()
        compose.onNodeWithText(text(R.string.scheduled_for, ""), substring = true).assertExists()
    }

    @Test
    fun conversationPagesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var page by mutableIntStateOf(0)
        compose.setContent {
            DesignVariantContent(variant) {
                when (page) {
                    0 -> ConversationDetailsScreen(details, {})
                    1 -> SendLaterScreen(target, tomorrowNine, onBack = {}, onSchedule = {})
                    2 -> MessageDetailsScreen(facts, onBack = {})
                    3 -> ContactCardScreen(card, onBack = {}, onField = {}, onSave = {})
                    4 -> SelectTextScreen("Mina Farahani · 12:41", "The address is 24 Willow Street. See you there.", {}, {})
                    5 -> ScheduledMessageScreen(target, tomorrowNine, timeChanged = false, onEvent = {})
                    6 -> RenameDialog(
                        title = text(R.string.rename_conversation),
                        current = "Book club",
                        placeholder = "Book club",
                        explanation = text(R.string.rename_explanation),
                        confirmLabel = text(R.string.save_name),
                        dismissLabel = text(org.fossify.commons.R.string.cancel),
                        onRename = {},
                        onDismiss = {},
                    )
                    7 -> ConfirmationDialog(
                        title = compose.activity.resources.getQuantityString(R.plurals.delete_conversations_title, 3, 3),
                        explanation = compose.activity.resources.getQuantityString(R.plurals.delete_conversations_to_bin_text, 3),
                        confirmLabel = text(org.fossify.commons.R.string.delete),
                        dismissLabel = text(org.fossify.commons.R.string.cancel),
                        onConfirm = {},
                        onDismiss = {},
                    )
                    else -> Box(Modifier.fillMaxSize()) {
                        ThreadTimeline(ThreadUiState(items = listOf(scheduled())), remember { SnackbarHostState() }, {})
                    }
                }
            }
        }
        listOf(
            "09-details", "12-send-later", "13-message-details", "14-contact-card",
            "44-select-text", "47-scheduled-edit", "39-rename", "82-delete-confirmation",
        ).forEachIndexed { index, name ->
            page = index
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
        // 84: the scheduled message's sheet.
        page = 8
        variant = DesignVariant.LIGHT
        compose.onNodeWithText("Happy birthday", substring = true).performClick()
        compose.waitForIdle()
        Screenshots.capture("84-scheduled-sheet")
    }
}

package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.SpamReviewMessage
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.spam.AddRuleScreen
import org.fossify.messages.ui.spam.AddRuleUiState
import org.fossify.messages.ui.spam.BlockRulesEvent
import org.fossify.messages.ui.spam.BlockRulesScreen
import org.fossify.messages.ui.spam.BlockRulesUiState
import org.fossify.messages.ui.spam.BlockedNumbersScreen
import org.fossify.messages.ui.spam.BlockedNumbersUiState
import org.fossify.messages.ui.spam.BlockedSender
import org.fossify.messages.ui.spam.MoveRulesScreen
import org.fossify.messages.ui.spam.RuleKind
import org.fossify.messages.ui.spam.SpamEvent
import org.fossify.messages.ui.spam.SpamScreen
import org.fossify.messages.ui.spam.SpamUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** 20, 21, 38, 50 and 51 on sample data: behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class SpamPageChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private val library = PageNavigation(TopDestination.LIBRARY, {})
    private val settings = PageNavigation(TopDestination.SETTINGS, {})
    /** A time on today (or [daysAgo] days back), so the day pills never depend on when the test runs. */
    private fun at(hour: Int, minute: Int, daysAgo: Int = 0) = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    private fun spam(id: Long, thread: Long, sender: String, body: String, date: Long) =
        SpamReviewMessage(id, thread, sender, sender, "", body, date, read = true, reason = 0)

    private val messages = listOf(
        spam(1, 1, "0912 418 6630", "Special offer ending tonight", at(9, 18)),
        spam(2, 1, "0912 418 6630", "Last chance. Reply STOP to opt out.", at(9, 19)),
        spam(3, 2, "MCI offer", "50% extra data until midnight.", at(18, 2, daysAgo = 1)),
    )

    @Test
    fun timelineNamesEachSenderOnceAndHoldsAMessageForItsActions() {
        val events = mutableListOf<SpamEvent>()
        compose.setContent {
            OpenLineTheme(dark = false) {
                SpamScreen(SpamUiState(loading = false, messages = messages), library, remember { SnackbarHostState() }, { events += it })
            }
        }
        compose.onNodeWithText(text(R.string.inbox_section_today)).assertExists()
        compose.onNodeWithText(text(R.string.inbox_section_yesterday)).assertExists()
        compose.onAllNodesWithText("0912 418 6630").assertCountEquals(1)

        compose.onNodeWithText("Last chance", substring = true).performClick()
        check(events.last() == SpamEvent.Open(messages[1])) { "Events $events" }

        compose.onNodeWithText("Last chance", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.not_spam_short)).performClick()
        check(events.last() == SpamEvent.NotSpam(messages[1])) { "Events $events" }
        compose.onNodeWithText(text(R.string.not_spam_short)).assertDoesNotExist()

        compose.onNodeWithText("50% extra data", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.block_sender)).performClick()
        check(events.last() !is SpamEvent.BlockSender) { "Block must ask first" }
        compose.onNodeWithText(text(R.string.block_sender_title, "MCI offer")).assertExists()
        compose.onAllNodesWithText(text(R.string.block_sender)).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        check(events.last() == SpamEvent.BlockSender(messages[2])) { "Events $events" }
    }

    @Test
    fun rulesToggleTheFilterAndRemoveAndAddPhrases() {
        val events = mutableListOf<BlockRulesEvent>()
        var state by mutableStateOf(BlockRulesUiState(blockedNumbers = 3, phrases = listOf("Claim your reward", "Free prize")))
        compose.setContent {
            OpenLineTheme(dark = false) { BlockRulesScreen(state, settings, { events += it }) }
        }
        val filter = compose.onNodeWithText(text(R.string.filter_obvious_spam))
        filter.assertIsOn().performClick()
        check(events.last() == BlockRulesEvent.SetFilter(false)) { "Events $events" }
        state = state.copy(filterEnabled = false)
        filter.assertIsOff()

        compose.onNodeWithContentDescription(text(R.string.remove_phrase, "Free prize")).performClick()
        check(events.last() == BlockRulesEvent.RemovePhrase("Free prize")) { "Events $events" }
        compose.onNodeWithText("Claim your reward").performClick()
        check(events.last() == BlockRulesEvent.EditPhrase("Claim your reward")) { "Events $events" }

        compose.onNodeWithText(text(R.string.add_phrase_or_number)).performClick()
        compose.onNodeWithText(text(R.string.block_a_number)).performClick()
        check(events.last() == BlockRulesEvent.AddNumber) { "Events $events" }
    }

    @Test
    fun blockedNumbersUnblockAndPhraseErrorsExplainThemselves() {
        val unblocked = mutableListOf<BlockedSender>()
        var page by mutableIntStateOf(0)
        val sender = BlockedSender("555 0183", null, inSystemList = false)
        compose.setContent {
            OpenLineTheme(dark = false) {
                if (page == 0) {
                    BlockedNumbersScreen(
                        BlockedNumbersUiState(loading = false, senders = listOf(sender)),
                        settings, onBack = {}, onUnblock = { unblocked += it }, onBlockNumber = {},
                    )
                } else {
                    AddRuleScreen(
                        AddRuleUiState(RuleKind.PHRASE, value = "limited offer", error = R.string.phrase_already_blocked),
                        settings, onBack = {}, onValueChange = {}, onSave = {},
                    )
                }
            }
        }
        compose.onNodeWithText(text(org.fossify.commons.R.string.unblock)).performClick()
        check(unblocked == listOf(sender)) { "Unblocked $unblocked" }
        page = 1
        compose.onNodeWithText(text(R.string.phrase_already_blocked)).assertExists()
        compose.onNodeWithText(text(R.string.no_current_match)).assertExists()
    }

    @Test
    fun spamPagesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var page by mutableIntStateOf(0)
        val senders = listOf(
            BlockedSender("555 0183", null, false),
            BlockedSender("+15550190", "Unknown marketing line", false),
            BlockedSender("555 0197", null, true),
        )
        compose.setContent {
            DesignVariantContent(variant) {
                when (page) {
                    0 -> SpamScreen(SpamUiState(loading = false, messages = messages), library, remember { SnackbarHostState() }, {})
                    1 -> BlockRulesScreen(BlockRulesUiState(3, listOf("Claim your reward", "Free prize")), settings, {})
                    2 -> BlockedNumbersScreen(BlockedNumbersUiState(false, senders), settings, {}, {}, {})
                    3 -> AddRuleScreen(
                        AddRuleUiState(RuleKind.PHRASE, value = "limited offer", example = "A limited offer just for you"),
                        settings, {}, {}, {},
                    )
                    else -> MoveRulesScreen(3, 2, settings, remember { SnackbarHostState() }, {}, {}, {})
                }
            }
        }
        listOf("20-spam", "21-block-rules", "38-blocked-numbers", "50-add-phrase", "51-move-rules").forEachIndexed { index, name ->
            page = index
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
        // 20 draft: the held message over the scrim.
        page = 0
        variant = DesignVariant.LIGHT
        compose.onNodeWithText("50% extra data", substring = true).performTouchInput { longClick() }
        compose.waitForIdle()
        Screenshots.capture("20-spam-held")
    }
}

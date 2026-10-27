package org.fossify.messages

import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.commons.helpers.DATE_FORMAT_ONE
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.settings.ImportReview
import org.fossify.messages.ui.settings.SettingsEvent
import org.fossify.messages.ui.settings.SettingsPage
import org.fossify.messages.ui.settings.SettingsScreen
import org.fossify.messages.ui.settings.SettingsUiState
import org.fossify.messages.ui.settings.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 24–31, 48 and 49 on sample state: behavior, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class SettingsPageChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private val navigation = PageNavigation(TopDestination.SETTINGS, {})

    private val sample = SettingsUiState(
        theme = ThemeChoice.SYSTEM,
        accentSummary = "",
        fontSize = 1,
        language = "English",
        languagePicker = true,
        dateFormat = DATE_FORMAT_ONE,
        sendOnEnter = false,
        characterCounter = true,
        deliveryReports = true,
        mmsLimit = 0,
        swipeLeft = SwipeAction.DELETE,
        swipeRight = SwipeAction.ARCHIVE,
        keepArchived = true,
        useRecycleBin = true,
        recycleBinMessages = 4,
        recentSearches = 3,
        exportName = "messages_2026_10_27",
        review = ImportReview("messages_2026_09_01.json", conversations = 42, sms = 1_204, mms = 86, restoreSms = true, restoreMms = true),
        version = "1.4.0",
    )

    private fun show(page: SettingsPage, state: SettingsUiState = sample, onEvent: (SettingsEvent) -> Unit) {
        compose.setContent {
            OpenLineTheme(dark = false) {
                SettingsScreen(state, page, navigation, remember { SnackbarHostState() }, {}, onEvent)
            }
        }
    }

    @Test
    fun overviewOpensEachPageAndBlockRules() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsPage.OVERVIEW) { events += it }
        compose.onNodeWithText(text(R.string.settings_appearance)).performClick()
        compose.onNodeWithText(text(R.string.block_rules)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.settings_backup)).performScrollTo().performClick()
        check(
            events == listOf(
                SettingsEvent.Open(SettingsPage.APPEARANCE),
                SettingsEvent.OpenBlockRules,
                SettingsEvent.Open(SettingsPage.BACKUP),
            ),
        ) { "Events $events" }
    }

    @Test
    fun appearanceShowsTheDateItProducesAndPicksATheme() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsPage.APPEARANCE) { events += it }
        val sampleDate = DateFormat.format(DATE_FORMAT_ONE, System.currentTimeMillis()).toString()
        compose.onNodeWithText(sampleDate).performScrollTo().assertExists()
        compose.onAllNodesWithText(DATE_FORMAT_ONE).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.theme_dark)).performScrollTo().performClick()
        check(events.last() == SettingsEvent.SetTheme(ThemeChoice.DARK)) { "Events $events" }
    }

    @Test
    fun clearingSearchesAsksFirstAndDoesNothingWhenThereAreNone() {
        val events = mutableListOf<SettingsEvent>()
        var state by mutableStateOf(sample)
        compose.setContent {
            OpenLineTheme(dark = false) {
                SettingsScreen(state, SettingsPage.PRIVACY, navigation, remember { SnackbarHostState() }, {}, { events += it })
            }
        }
        compose.onNodeWithText(text(R.string.clear_recent_searches)).performScrollTo().performClick()
        check(events.isEmpty()) { "Clearing must ask first: $events" }
        compose.onNodeWithText(text(R.string.clear_recent_searches_title)).assertExists()
        compose.onAllNodesWithText(text(R.string.clear_recent_searches)).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        check(events == listOf(SettingsEvent.ClearRecentSearches)) { "Events $events" }

        state = state.copy(recentSearches = 0)
        compose.onNodeWithText(text(R.string.clear_recent_searches)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.clear_recent_searches_title)).assertDoesNotExist()
    }

    @Test
    fun exportNeedsSomethingToExportAndAName() {
        val events = mutableListOf<SettingsEvent>()
        var state by mutableStateOf(sample)
        compose.setContent {
            OpenLineTheme(dark = false) {
                SettingsScreen(state, SettingsPage.EXPORT, navigation, remember { SnackbarHostState() }, {}, { events += it })
            }
        }
        val create = compose.onNodeWithText(text(R.string.create_backup))
        create.performScrollTo().assertIsEnabled()
        state = state.copy(exportName = " ")
        create.assertIsNotEnabled()
        state = state.copy(exportName = "backup", exportSms = false, exportMms = false)
        create.assertIsNotEnabled()
        state = state.copy(exportSms = true)
        create.performClick()
        check(events.last() == SettingsEvent.CreateBackup) { "Events $events" }
    }

    @Test
    fun settingsPagesAreCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var page by mutableStateOf(SettingsPage.OVERVIEW)
        compose.setContent {
            DesignVariantContent(variant) {
                SettingsScreen(sample, page, navigation, remember { SnackbarHostState() }, {}, {})
            }
        }
        listOf(
            SettingsPage.OVERVIEW to "24-settings",
            SettingsPage.APPEARANCE to "25-appearance",
            SettingsPage.WRITING to "26-writing",
            SettingsPage.NOTIFICATIONS to "27-notifications",
            SettingsPage.PRIVACY to "28-privacy",
            SettingsPage.BACKUP to "29-backup",
            SettingsPage.EXPORT to "30-export",
            SettingsPage.IMPORT_REVIEW to "31-import-review",
            SettingsPage.ABOUT to "48-about",
            SettingsPage.INTERACTIONS to "49-interactions",
        ).forEach { (settingsPage, name) ->
            page = settingsPage
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
    }
}

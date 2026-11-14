package com.afsh.messages

import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.and
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.commons.helpers.DATE_FORMAT_ONE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.TopDestination
import com.afsh.messages.ui.settings.ImportReview
import com.afsh.messages.ui.settings.SettingsEvent
import com.afsh.messages.ui.settings.SettingsPage
import com.afsh.messages.ui.settings.SettingsScreen
import com.afsh.messages.ui.settings.SettingsUiState
import com.afsh.messages.ui.settings.ThemeChoice
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
            AfshTheme(dark = false) {
                SettingsScreen(state, page, navigation, remember { SnackbarHostState() }, {}, onEvent)
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    /** A row that opens a page or dialog, found by its label and its button role, never by layout. */
    private fun link(label: String) = compose.onNode(hasText(label) and hasRole(Role.Button) and hasClickAction())

    /** A switch row: the whole row is one toggleable node with the switch role. */
    private fun switch(label: String) = compose.onNode(hasText(label) and hasRole(Role.Switch) and isToggleable())

    private fun choice(label: String) = compose.onNode(hasText(label) and hasRole(Role.RadioButton))

    private fun SemanticsNodeInteraction.assertTarget() = assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)

    @Test
    fun overviewOpensEachPageAndBlockRules() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsPage.OVERVIEW) { events += it }
        link(text(R.string.settings_appearance)).performClick()
        link(text(R.string.block_rules)).performScrollTo().performClick()
        link(text(R.string.settings_backup)).performScrollTo().performClick()
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
        compose.onNode(hasText(sampleDate, substring = true)).performScrollTo().assertExists()
        compose.onAllNodesWithText(DATE_FORMAT_ONE).assertCountEquals(0)
        choice(text(R.string.theme_dark)).performScrollTo().performClick()
        check(events.last() == SettingsEvent.SetTheme(ThemeChoice.DARK)) { "Events $events" }
    }

    @Test
    fun clearingSearchesAsksFirstAndDoesNothingWhenThereAreNone() {
        val events = mutableListOf<SettingsEvent>()
        var state by mutableStateOf(sample)
        compose.setContent {
            AfshTheme(dark = false) {
                SettingsScreen(state, SettingsPage.PRIVACY, navigation, remember { SnackbarHostState() }, {}, { events += it })
            }
        }
        link(text(R.string.clear_recent_searches)).performScrollTo().performClick()
        check(events.isEmpty()) { "Clearing must ask first: $events" }
        compose.onNode(hasText(text(R.string.clear_recent_searches_title))).assertExists()
        compose.onAllNodesWithText(text(R.string.clear_recent_searches)).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        check(events == listOf(SettingsEvent.ClearRecentSearches)) { "Events $events" }

        state = state.copy(recentSearches = 0)
        link(text(R.string.clear_recent_searches)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.clear_recent_searches_title))).assertDoesNotExist()
    }

    @Test
    fun exportNeedsSomethingToExportAndAName() {
        val events = mutableListOf<SettingsEvent>()
        var state by mutableStateOf(sample)
        compose.setContent {
            AfshTheme(dark = false) {
                SettingsScreen(state, SettingsPage.EXPORT, navigation, remember { SnackbarHostState() }, {}, { events += it })
            }
        }
        val create = compose.onNode(hasText(text(R.string.create_backup)) and hasRole(Role.Button))
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
    fun writingSwitchesReadAndWriteTheirOwnSetting() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsPage.WRITING) { events += it }
        listOf(
            Triple(R.string.send_on_enter, sample.sendOnEnter, SettingsEvent.SetSendOnEnter(!sample.sendOnEnter)),
            Triple(R.string.show_character_counter, sample.characterCounter, SettingsEvent.SetCharacterCounter(!sample.characterCounter)),
            Triple(R.string.enable_delivery_reports, sample.deliveryReports, SettingsEvent.SetDeliveryReports(!sample.deliveryReports)),
        ).forEach { (label, on, expected) ->
            val row = switch(text(label)).performScrollTo()
            if (on) row.assertIsOn() else row.assertIsOff()
            row.assertTarget().performClick()
            check(events.last() == expected) { "${text(label)}: events $events" }
        }
    }

    @Test
    fun settingRowsKeepTheirRolesAndTargetsInRtlAtLargeFont() {
        val events = mutableListOf<SettingsEvent>()
        var page by mutableStateOf(SettingsPage.APPEARANCE)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AfshTheme(dark = false, textScale = 1.5f) {
                    SettingsScreen(sample.copy(fontSize = FONT_SIZE_LARGE), page, navigation, remember { SnackbarHostState() }, {}, { events += it })
                }
            }
        }
        choice(text(R.string.theme_system)).performScrollTo().assertIsSelected().assertTarget()
        switch(text(R.string.use_24_hour_time)).performScrollTo().assertTarget().performClick()
        check(events.last() == SettingsEvent.SetUse24Hour(!sample.use24Hour)) { "Events $events" }

        page = SettingsPage.OVERVIEW
        listOf(R.string.settings_appearance, R.string.settings_writing, R.string.settings_privacy).forEach {
            link(text(it)).performScrollTo().assertTarget()
        }
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

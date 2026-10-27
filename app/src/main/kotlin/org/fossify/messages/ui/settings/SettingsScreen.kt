package org.fossify.messages.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.ui.components.Choice
import org.fossify.messages.ui.components.ChoiceDialog
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.MessageDialog
import org.fossify.messages.ui.components.NavigationSetting
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.SettingItem
import org.fossify.messages.ui.components.SwitchSetting

/** Settings bodies use a 16dp top and 4dp between rows (24); the rows carry their own padding. */
private val SettingsBodyPadding = PaddingValues(
    start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp,
)

/**
 * Settings: the overview (24) and its pages, one at a time. [page] and [onPage] keep the stack in
 * the activity, so Back and recreation return to the right page.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    page: SettingsPage,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (page != SettingsPage.OVERVIEW) BackHandler(onBack = onBack)
    PageScaffold(
        title = stringResource(page.title),
        modifier = modifier,
        subtitle = stringResource(page.subtitle),
        // The overview is a top-level tab: no back button there (R1-01).
        onBack = onBack.takeIf { page != SettingsPage.OVERVIEW },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(SettingsBodyPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (page) {
                SettingsPage.OVERVIEW -> Overview(onEvent)
                SettingsPage.APPEARANCE -> AppearancePage(state, onEvent)
                SettingsPage.WRITING -> WritingPage(state, onEvent)
                SettingsPage.NOTIFICATIONS -> NotificationsPage(state, onEvent)
                SettingsPage.INTERACTIONS -> InteractionsPage(state, onEvent)
                SettingsPage.PRIVACY -> PrivacyPage(state, onEvent)
                SettingsPage.BACKUP -> BackupPage(onEvent)
                SettingsPage.EXPORT -> ExportPage(state, onEvent)
                SettingsPage.IMPORT_REVIEW -> ImportReviewPage(state, onEvent)
                SettingsPage.ABOUT -> AboutPage(state, onEvent)
            }
        }
    }
}

/** 24 · Settings / overview. */
@Composable
private fun ColumnScope.Overview(onEvent: (SettingsEvent) -> Unit) {
    fun open(page: SettingsPage) = onEvent(SettingsEvent.Open(page))
    SectionHeading(stringResource(R.string.settings_your_experience))
    NavigationSetting(
        stringResource(R.string.settings_appearance),
        onClick = { open(SettingsPage.APPEARANCE) },
        icon = R.drawable.ic_lucide_languages,
        value = stringResource(R.string.settings_appearance_summary),
    )
    NavigationSetting(
        stringResource(org.fossify.commons.R.string.notifications),
        onClick = { open(SettingsPage.NOTIFICATIONS) },
        icon = R.drawable.ic_lucide_bell,
        value = stringResource(R.string.settings_notifications_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_writing),
        onClick = { open(SettingsPage.WRITING) },
        icon = R.drawable.ic_lucide_send,
        value = stringResource(R.string.settings_writing_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_interactions),
        onClick = { open(SettingsPage.INTERACTIONS) },
        icon = R.drawable.ic_lucide_hand,
        value = stringResource(R.string.settings_interactions_summary),
    )
    SectionHeading(stringResource(R.string.settings_control))
    NavigationSetting(
        stringResource(R.string.block_rules),
        onClick = { onEvent(SettingsEvent.OpenBlockRules) },
        icon = R.drawable.ic_lucide_shield_ban,
        value = stringResource(R.string.settings_block_rules_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_privacy),
        onClick = { open(SettingsPage.PRIVACY) },
        icon = R.drawable.ic_lucide_lock_keyhole,
        value = stringResource(R.string.settings_privacy_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_backup),
        onClick = { open(SettingsPage.BACKUP) },
        icon = R.drawable.ic_lucide_database_backup,
        value = stringResource(R.string.settings_backup_summary),
    )
    NavigationSetting(
        stringResource(R.string.about_app),
        onClick = { open(SettingsPage.ABOUT) },
        icon = R.drawable.ic_lucide_info,
        value = stringResource(R.string.about_summary),
    )
}

/** 49 · Settings / gestures and dates ("Interactions"). Swipe directions are physical, not mirrored. */
@Composable
private fun InteractionsPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf<Boolean?>(null) }
    SectionHeading(stringResource(R.string.swipe_in_the_inbox))
    NavigationSetting(
        stringResource(R.string.swipe_right),
        onClick = { choosing = true },
        icon = R.drawable.ic_lucide_arrow_right,
        value = stringResource(state.swipeRight.label),
    )
    NavigationSetting(
        stringResource(R.string.swipe_left),
        onClick = { choosing = false },
        icon = R.drawable.ic_lucide_arrow_left,
        value = stringResource(state.swipeLeft.label),
    )
    SectionHeading(stringResource(R.string.archive_behavior))
    SwitchSetting(
        stringResource(R.string.keep_conversations_archived),
        checked = state.keepArchived,
        onCheckedChange = { onEvent(SettingsEvent.SetKeepArchived(it)) },
        icon = R.drawable.ic_lucide_archive,
        value = stringResource(if (state.keepArchived) R.string.keep_archived_on else R.string.keep_archived_off),
    )
    choosing?.let { towardsRight ->
        val current = if (towardsRight) state.swipeRight else state.swipeLeft
        // "Off" reads best last; Archive goes where the provider cannot archive.
        val actions = (SwipeAction.entries - SwipeAction.NONE)
            .filter { it != SwipeAction.ARCHIVE || state.archiveAvailable } +
            SwipeAction.NONE
        ChoiceDialog(
            title = stringResource(if (towardsRight) R.string.swipe_right else R.string.swipe_left),
            choices = actions.map { Choice(it, stringResource(it.label)) },
            selected = current,
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onChoose = {
                choosing = null
                onEvent(SettingsEvent.SetSwipe(towardsRight, it))
            },
            onDismiss = { choosing = null },
        )
    }
}

/** 48 · About / app information. */
@Composable
private fun AboutPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    InfoPanel(
        title = stringResource(R.string.app_launcher_name),
        body = stringResource(R.string.about_description),
        prominent = true,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    NavigationSetting(
        stringResource(R.string.help_and_support),
        onClick = { onEvent(SettingsEvent.OpenHelp) },
        icon = R.drawable.ic_lucide_circle_help,
        disclosure = false,
    )
    NavigationSetting(
        stringResource(R.string.privacy_information),
        onClick = { privacyOpen = true },
        icon = R.drawable.ic_lucide_eye_off,
        disclosure = false,
    )
    NavigationSetting(
        stringResource(R.string.open_source_licenses),
        onClick = { licensesOpen = true },
        icon = R.drawable.ic_lucide_file_text,
        disclosure = false,
    )
    SettingItem(stringResource(org.fossify.commons.R.string.app_version), icon = R.drawable.ic_lucide_info, value = state.version)
    if (privacyOpen) {
        MessageDialog(
            title = stringResource(R.string.privacy_information),
            text = stringResource(R.string.privacy_information_text),
            dismissLabel = stringResource(org.fossify.commons.R.string.ok),
            onDismiss = { privacyOpen = false },
        )
    }
    if (licensesOpen) {
        MessageDialog(
            title = stringResource(R.string.open_source_licenses),
            text = stringResource(R.string.open_source_licenses_text),
            dismissLabel = stringResource(org.fossify.commons.R.string.ok),
            onDismiss = { licensesOpen = false },
        )
    }
}

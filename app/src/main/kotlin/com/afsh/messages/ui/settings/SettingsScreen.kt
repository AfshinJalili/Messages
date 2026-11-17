package com.afsh.messages.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import com.afsh.messages.R
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.Choice
import com.afsh.messages.ui.components.ChoiceDialog
import com.afsh.messages.ui.components.MessageDialog
import com.afsh.messages.ui.components.NavigationSetting
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.SettingItem
import com.afsh.messages.ui.components.SwitchSetting
import com.afsh.messages.ui.ltr

/** Settings bodies use a 16dp top and 4dp between rows (24); the rows carry their own padding. */
private val SettingsBodyPadding: PaddingValues
    @Composable get() = with(Afsh.spacing) { PaddingValues(start = xl, top = lg, end = xl, bottom = xxl) }

/**
 * Settings: the overview (24) and its pages, one at a time. [page] and [onPage] keep the stack in
 * the activity, so Back and recreation return to the right page.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    page: SettingsPage,
    navigation: PageNavigation?,
    snackbarHostState: SnackbarHostState?,
    onBack: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** True when hosted as a MainActivity tab: the outer scaffold already owns nav and snackbar. */
    embedded: Boolean = false,
) {
    // Back (including predictive) is owned by ScreenStack in SettingsController so the gesture
    // drives the same slide-out as the up arrow and the system back button.
    PageScaffold(
        title = stringResource(page.title),
        modifier = modifier,
        subtitle = page.subtitle?.let { stringResource(it) },
        // The overview is a top-level tab: no back button there (R1-01).
        onBack = onBack.takeIf { page != SettingsPage.OVERVIEW },
        navigation = navigation.takeUnless { embedded },
        snackbarHostState = snackbarHostState.takeUnless { embedded },
        contentWindowInsets = if (embedded) WindowInsets(0) else null,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(SettingsBodyPadding),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
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
        icon = AfshIcon.Language,
        value = stringResource(R.string.settings_appearance_summary),
    )
    NavigationSetting(
        stringResource(org.fossify.commons.R.string.notifications),
        onClick = { open(SettingsPage.NOTIFICATIONS) },
        icon = AfshIcon.Notifications,
        value = stringResource(R.string.settings_notifications_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_writing),
        onClick = { open(SettingsPage.WRITING) },
        icon = AfshIcon.Send,
        value = stringResource(R.string.settings_writing_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_interactions),
        onClick = { open(SettingsPage.INTERACTIONS) },
        icon = AfshIcon.Stop,
        value = stringResource(R.string.settings_interactions_summary),
    )
    SectionHeading(stringResource(R.string.settings_control))
    NavigationSetting(
        stringResource(R.string.filter_rules),
        onClick = { onEvent(SettingsEvent.OpenBlockRules) },
        icon = AfshIcon.SpamShield,
        value = stringResource(R.string.settings_filter_rules_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_privacy),
        onClick = { open(SettingsPage.PRIVACY) },
        icon = AfshIcon.Lock,
        value = stringResource(R.string.settings_privacy_summary),
    )
    NavigationSetting(
        stringResource(R.string.settings_backup),
        onClick = { open(SettingsPage.BACKUP) },
        icon = AfshIcon.Backup,
        value = stringResource(R.string.settings_backup_summary),
    )
    NavigationSetting(
        stringResource(R.string.about_app),
        onClick = { open(SettingsPage.ABOUT) },
        icon = AfshIcon.Info,
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
        icon = AfshIcon.ArrowForward,
        value = stringResource(state.swipeRight.label),
    )
    NavigationSetting(
        stringResource(R.string.swipe_left),
        onClick = { choosing = false },
        icon = AfshIcon.ArrowBack,
        value = stringResource(state.swipeLeft.label),
    )
    SectionHeading(stringResource(R.string.archive_behavior))
    SwitchSetting(
        stringResource(R.string.keep_conversations_archived),
        checked = state.keepArchived,
        onCheckedChange = { onEvent(SettingsEvent.SetKeepArchived(it)) },
        icon = AfshIcon.Archive,
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
    NavigationSetting(
        stringResource(R.string.help_and_support),
        onClick = { onEvent(SettingsEvent.OpenHelp) },
        icon = AfshIcon.Help,
        disclosure = false,
    )
    NavigationSetting(
        stringResource(R.string.privacy_information),
        onClick = { privacyOpen = true },
        icon = AfshIcon.Hidden,
        disclosure = false,
    )
    NavigationSetting(
        stringResource(R.string.open_source_licenses),
        onClick = { licensesOpen = true },
        icon = AfshIcon.Document,
        disclosure = false,
    )
    SectionHeading(stringResource(R.string.about_updates))
    SettingItem(stringResource(org.fossify.commons.R.string.app_version), icon = AfshIcon.Info, value = ltr(state.version))
    // The app stays offline (#214): the browser fetches the release and Android installs it.
    NavigationSetting(
        stringResource(R.string.check_for_updates),
        onClick = { onEvent(SettingsEvent.CheckForUpdates) },
        icon = AfshIcon.Refresh,
        value = stringResource(R.string.check_for_updates_summary),
        disclosureIcon = AfshIcon.OpenExternal,
    )
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

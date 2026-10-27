package org.fossify.messages.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import org.fossify.messages.R
import org.fossify.messages.helpers.SwipeAction

/** The Settings pages, each reached from the overview (24) by a row of the same name (R5-35). */
enum class SettingsPage(@StringRes val title: Int, @StringRes val subtitle: Int) {
    OVERVIEW(org.fossify.commons.R.string.settings, R.string.settings_subtitle),
    APPEARANCE(R.string.settings_appearance, R.string.settings_appearance_subtitle),
    WRITING(R.string.settings_writing, R.string.settings_writing_subtitle),
    NOTIFICATIONS(org.fossify.commons.R.string.notifications, R.string.settings_notifications_subtitle),
    INTERACTIONS(R.string.settings_interactions, R.string.settings_interactions_subtitle),
    PRIVACY(R.string.settings_privacy, R.string.settings_privacy_subtitle),
    BACKUP(R.string.settings_backup, R.string.settings_backup_subtitle),
    EXPORT(R.string.export_messages, R.string.export_subtitle),
    IMPORT_REVIEW(R.string.restore_backup, R.string.restore_backup_subtitle),
    ABOUT(R.string.about_app, R.string.about_subtitle),
}

/** 25's theme cards. */
enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
}

/** A backup opened for 31, before anything is written. */
@Immutable
data class ImportReview(
    val fileName: String,
    val conversations: Int,
    val sms: Int,
    val mms: Int,
    val restoreSms: Boolean,
    val restoreMms: Boolean,
) {
    val messages get() = sms + mms
}

/** Everything the Settings pages show. [useEnglish] is null where the toggle does not apply. */
@Immutable
data class SettingsUiState(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val accentSummary: String = "",
    val fontSize: Int = 1,
    val language: String = "",
    val languagePicker: Boolean = false,
    val useEnglish: Boolean? = null,
    val dateFormat: String = "",
    val use24Hour: Boolean = false,
    val sendOnEnter: Boolean = false,
    val characterCounter: Boolean = false,
    val simpleCharacters: Boolean = false,
    val deliveryReports: Boolean = false,
    val longAsMms: Boolean = false,
    val groupAsMms: Boolean = false,
    val mmsLimit: Long = 0,
    val alertsEnabled: Boolean = true,
    val lockScreen: Int = 0,
    val swipeLeft: SwipeAction = SwipeAction.NONE,
    val swipeRight: SwipeAction = SwipeAction.NONE,
    val archiveAvailable: Boolean = true,
    val keepArchived: Boolean = false,
    val appLock: Boolean = false,
    val useRecycleBin: Boolean = false,
    val recycleBinMessages: Int = 0,
    val recentSearches: Int = 0,
    val exportSms: Boolean = true,
    val exportMms: Boolean = true,
    val exportName: String = "",
    val review: ImportReview? = null,
    val busy: Boolean = false,
    val version: String = "",
)

/** Everything the Settings pages can ask their activity to do. */
sealed interface SettingsEvent {
    data class Open(val page: SettingsPage) : SettingsEvent
    data object OpenBlockRules : SettingsEvent
    data class SetTheme(val theme: ThemeChoice) : SettingsEvent
    data object CustomizeColors : SettingsEvent
    data class SetFontSize(val size: Int) : SettingsEvent
    data object ChangeLanguage : SettingsEvent
    data class SetUseEnglish(val enabled: Boolean) : SettingsEvent
    data class SetDateFormat(val format: String) : SettingsEvent
    data class SetUse24Hour(val enabled: Boolean) : SettingsEvent
    data class SetSendOnEnter(val enabled: Boolean) : SettingsEvent
    data class SetCharacterCounter(val enabled: Boolean) : SettingsEvent
    data class SetSimpleCharacters(val enabled: Boolean) : SettingsEvent
    data class SetDeliveryReports(val enabled: Boolean) : SettingsEvent
    data class SetLongAsMms(val enabled: Boolean) : SettingsEvent
    data class SetGroupAsMms(val enabled: Boolean) : SettingsEvent
    data class SetMmsLimit(val bytes: Long) : SettingsEvent
    data object OpenAlertSettings : SettingsEvent
    data object OpenSoundSettings : SettingsEvent
    data class SetLockScreen(val visibility: Int) : SettingsEvent
    data class SetSwipe(val towardsRight: Boolean, val action: SwipeAction) : SettingsEvent
    data class SetKeepArchived(val enabled: Boolean) : SettingsEvent
    data object ToggleAppLock : SettingsEvent
    data class SetUseRecycleBin(val enabled: Boolean) : SettingsEvent
    data object EmptyRecycleBin : SettingsEvent
    data object ClearRecentSearches : SettingsEvent
    data class SetExportSms(val enabled: Boolean) : SettingsEvent
    data class SetExportMms(val enabled: Boolean) : SettingsEvent
    data class SetExportName(val name: String) : SettingsEvent
    data object CreateBackup : SettingsEvent
    data object ChooseBackup : SettingsEvent
    data class SetRestoreSms(val enabled: Boolean) : SettingsEvent
    data class SetRestoreMms(val enabled: Boolean) : SettingsEvent
    data object Restore : SettingsEvent
    data object OpenHelp : SettingsEvent
}

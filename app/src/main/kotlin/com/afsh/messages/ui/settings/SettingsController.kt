package com.afsh.messages.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.afsh.messages.BuildConfig
import com.afsh.messages.R
import com.afsh.messages.R as AppR
import com.afsh.messages.activities.BlockRulesActivity
import com.afsh.messages.activities.SimpleActivity
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.emptyMessagesRecycleBin
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.helpers.BackupFile
import com.afsh.messages.helpers.ExportOutcome
import com.afsh.messages.helpers.InvalidBackupException
import com.afsh.messages.helpers.MessagesExporter
import com.afsh.messages.helpers.MessagesImporter
import com.afsh.messages.helpers.NavBadges
import com.afsh.messages.helpers.refreshConversations
import com.afsh.messages.models.ImportResult
import androidx.compose.ui.graphics.toArgb
import com.afsh.messages.ui.afshColorScheme
import com.afsh.messages.ui.components.ScreenStack
import com.afsh.messages.ui.components.inform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.afsh.commons.extensions.getCurrentFormattedDateTime
import com.afsh.commons.extensions.isAValidFilename
import com.afsh.commons.extensions.launchViewIntent
import com.afsh.commons.helpers.PROTECTION_FINGERPRINT
import com.afsh.commons.helpers.SHOW_ALL_TABS
import com.afsh.commons.helpers.isQPlus
import com.afsh.commons.helpers.isTiramisuPlus
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Settings tab state and actions for [com.afsh.messages.activities.MainActivity]. Pages stack inside
 * the Settings tab so Back walks page by page; the three top-level destinations stay in one host.
 */
class SettingsController(
    private val activity: SimpleActivity,
    private val snackbarHost: SnackbarHostState,
    private val onThemeChanged: () -> Unit,
) {
    private val pages = mutableStateListOf(SettingsPage.OVERVIEW)
    private var state by mutableStateOf(SettingsUiState())
    private var backup: BackupFile.Messages? = null
    private var showSecurityDialog by mutableStateOf(false)
    private var securityDialogTab by mutableStateOf(SHOW_ALL_TABS)

    private val chooseBackup = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::openBackup)
    }
    private val createBackup = activity.registerForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let(::exportTo)
    }

    fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.getStringArrayList(PAGES)?.let { saved ->
            pages.clear()
            pages += saved.mapNotNull { name -> SettingsPage.entries.firstOrNull { it.name == name } }
                .filter { it != SettingsPage.IMPORT_REVIEW }
                .ifEmpty { listOf(SettingsPage.OVERVIEW) }
        }
        state = state.copy(exportName = "${activity.getString(R.string.messages)}_${activity.getCurrentFormattedDateTime()}")
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(PAGES, ArrayList(pages.map { it.name }))
    }

    fun onResume() = reload()

    fun reload() {
        state = readSettings()
        activity.lifecycleScope.launch {
            val recycled = withContext(Dispatchers.IO) {
                runCatching { activity.messagesDB.getArchivedCount() }.getOrDefault(0)
            }
            state = state.copy(recycleBinMessages = recycled)
        }
    }

    /** Clears nested settings pages when leaving the Settings tab. */
    fun resetToOverview() {
        if (pages.size > 1) pages.removeRange(1, pages.size)
        backup = null
        state = state.copy(review = null)
    }

    /** Pops a nested settings page. Returns false when already on the overview. */
    fun handleBack(): Boolean {
        if (pages.size <= 1) return false
        pages.removeAt(pages.lastIndex)
        return true
    }

    @Composable
    fun Content() {
        ScreenStack(
            screen = pages.last(),
            depth = pages.size,
            previous = pages.getOrNull(pages.lastIndex - 1),
            onPop = { handleBack() },
        ) { page ->
            SettingsScreen(
                state = state,
                page = page,
                navigation = null,
                snackbarHostState = null,
                onBack = { handleBack() },
                onEvent = ::onEvent,
                embedded = true,
            )
        }

        if (showSecurityDialog) {
            com.afsh.messages.ui.applock.SecuritySetupDialog(
                requiredHash = activity.config.appPasswordHash,
                showTabIndex = securityDialogTab,
                onDismiss = { showSecurityDialog = false },
                onResult = { hash, type, success ->
                    if (!success) {
                        showSecurityDialog = false
                        return@SecuritySetupDialog
                    }
                    val wasOn = activity.config.isAppPasswordProtectionOn
                    activity.config.isAppPasswordProtectionOn = !wasOn
                    activity.config.appPasswordHash = if (wasOn) "" else hash
                    activity.config.appProtectionType = type
                    state = readSettings()
                    showSecurityDialog = false
                    if (!wasOn) {
                        val message = if (type == PROTECTION_FINGERPRINT) {
                            com.afsh.commons.R.string.fingerprint_setup_successfully
                        } else {
                            com.afsh.commons.R.string.protection_setup_successfully
                        }
                        activity.inform(activity.getString(AppR.string.app_lock), activity.getString(message))
                    }
                },
            )
        }
    }

    private fun readSettings() = state.copy(
        theme = when {
            activity.config.isSystemThemeEnabled -> ThemeChoice.SYSTEM
            ColorUtils.calculateLuminance(activity.config.backgroundColor) < DARK_LUMINANCE -> ThemeChoice.DARK
            else -> ThemeChoice.LIGHT
        },
        fontSize = activity.config.fontSize,
        language = Locale.getDefault().displayLanguage,
        languagePicker = isTiramisuPlus(),
        useEnglish = activity.config.useEnglish.takeIf {
            !isTiramisuPlus() && (activity.config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        },
        dateFormat = activity.config.dateFormat,
        use24Hour = activity.config.use24HourFormat,
        sendOnEnter = activity.config.sendOnEnter,
        characterCounter = activity.config.showCharacterCounter,
        inboxUnreadCount = activity.config.showInboxUnreadCount,
        simpleCharacters = activity.config.useSimpleCharacters,
        deliveryReports = activity.config.enableDeliveryReports,
        longAsMms = activity.config.sendLongMessageMMS,
        groupAsMms = activity.config.sendGroupMessageMMS,
        mmsLimit = activity.config.mmsFileSizeLimit,
        alertsEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled(),
        lockScreen = activity.config.lockScreenVisibilitySetting,
        swipeLeft = activity.config.swipeLeftAction,
        swipeRight = activity.config.swipeRightAction,
        archiveAvailable = activity.config.isArchiveAvailable,
        keepArchived = activity.config.keepConversationsArchived,
        appLock = activity.config.isAppPasswordProtectionOn,
        useRecycleBin = activity.config.useRecycleBin,
        recentSearches = activity.config.recentSearches.size,
        exportSms = activity.config.exportSms,
        exportMms = activity.config.exportMms,
        version = activity.getString(R.string.app_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
    )

    private fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Open -> pages += event.page
            SettingsEvent.OpenBlockRules -> activity.startActivity(Intent(activity, BlockRulesActivity::class.java))
            is SettingsEvent.SetTheme -> setTheme(event.theme)
            is SettingsEvent.SetFontSize -> update { activity.config.fontSize = event.size }.also { onThemeChanged() }
            SettingsEvent.ChangeLanguage -> if (isTiramisuPlus()) activity.launchChangeAppLanguageIntent()
            is SettingsEvent.SetUseEnglish -> {
                activity.config.useEnglish = event.enabled
                exitProcess(0)
            }

            is SettingsEvent.SetDateFormat -> update { activity.config.dateFormat = event.format }.also { refreshConversations() }
            is SettingsEvent.SetUse24Hour -> update { activity.config.use24HourFormat = event.enabled }.also { refreshConversations() }
            is SettingsEvent.SetSendOnEnter -> update { activity.config.sendOnEnter = event.enabled }
            is SettingsEvent.SetCharacterCounter -> update { activity.config.showCharacterCounter = event.enabled }
            is SettingsEvent.SetInboxUnreadCount ->
                update { activity.config.showInboxUnreadCount = event.enabled }.also { NavBadges.refresh(activity) }
            is SettingsEvent.SetSimpleCharacters -> update { activity.config.useSimpleCharacters = event.enabled }
            is SettingsEvent.SetDeliveryReports -> update { activity.config.enableDeliveryReports = event.enabled }
            is SettingsEvent.SetLongAsMms -> update { activity.config.sendLongMessageMMS = event.enabled }
            is SettingsEvent.SetGroupAsMms -> update { activity.config.sendGroupMessageMMS = event.enabled }
            is SettingsEvent.SetMmsLimit -> update { activity.config.mmsFileSizeLimit = event.bytes }
            SettingsEvent.OpenAlertSettings -> openAppNotificationSettings()
            SettingsEvent.OpenSoundSettings -> activity.launchCustomizeNotificationsIntent()
            is SettingsEvent.SetLockScreen -> update { activity.config.lockScreenVisibilitySetting = event.visibility }
            is SettingsEvent.SetSwipe -> update {
                if (event.towardsRight) {
                    activity.config.swipeRightAction = event.action
                } else {
                    activity.config.swipeLeftAction = event.action
                }
            }

            is SettingsEvent.SetKeepArchived -> update { activity.config.keepConversationsArchived = event.enabled }
            SettingsEvent.ToggleAppLock -> toggleAppLock()
            is SettingsEvent.SetUseRecycleBin -> update { activity.config.useRecycleBin = event.enabled }
            SettingsEvent.EmptyRecycleBin -> activity.lifecycleScope.launch {
                withContext(Dispatchers.IO) { activity.emptyMessagesRecycleBin() }
                state = state.copy(recycleBinMessages = 0)
            }

            SettingsEvent.ClearRecentSearches -> update { activity.config.recentSearches = emptyList() }
            is SettingsEvent.SetExportSms -> update { activity.config.exportSms = event.enabled }
            is SettingsEvent.SetExportMms -> update { activity.config.exportMms = event.enabled }
            is SettingsEvent.SetExportName -> state = state.copy(exportName = event.name)
            SettingsEvent.CreateBackup -> startExport()
            SettingsEvent.ChooseBackup -> chooseBackup.launch(importTypes())
            is SettingsEvent.SetRestoreSms -> state.review?.let { state = state.copy(review = it.copy(restoreSms = event.enabled)) }
            is SettingsEvent.SetRestoreMms -> state.review?.let { state = state.copy(review = it.copy(restoreMms = event.enabled)) }
            SettingsEvent.Restore -> restore()
            SettingsEvent.OpenHelp -> activity.launchViewIntent(HELP_URL)
            SettingsEvent.CheckForUpdates -> activity.launchViewIntent(RELEASES_URL)
        }
    }

    private inline fun update(write: () -> Unit) {
        write()
        state = readSettings()
    }

    private fun setTheme(theme: ThemeChoice) {
        update {
            activity.config.isSystemThemeEnabled = theme == ThemeChoice.SYSTEM
            if (theme != ThemeChoice.SYSTEM) {
                val scheme = activity.afshColorScheme(dark = theme == ThemeChoice.DARK)
                activity.config.backgroundColor = scheme.surface.toArgb()
                activity.config.textColor = scheme.onSurface.toArgb()
            }
            activity.config.syncAccentWithTheme()
        }
        onThemeChanged()
    }

    private fun openAppNotificationSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
        )
    }

    private fun toggleAppLock() {
        securityDialogTab = if (activity.config.isAppPasswordProtectionOn) {
            activity.config.appProtectionType
        } else {
            SHOW_ALL_TABS
        }
        showSecurityDialog = true
    }

    private fun startExport() {
        val name = state.exportName.trim()
        if (!name.isAValidFilename()) {
            report(activity.getString(com.afsh.commons.R.string.invalid_name))
            return
        }
        createBackup.launch("$name.json")
    }

    private fun exportTo(uri: Uri) {
        state = state.copy(busy = true)
        activity.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                MessagesExporter(activity).export(uri, state.exportSms, state.exportMms)
            }
            state = state.copy(busy = false)
            report(
                activity.getString(
                    when (outcome) {
                        ExportOutcome.EXPORTED -> com.afsh.commons.R.string.exporting_successful
                        ExportOutcome.NOTHING_TO_EXPORT -> com.afsh.commons.R.string.no_entries_for_exporting
                        ExportOutcome.FAILED -> com.afsh.commons.R.string.exporting_failed
                    },
                ),
            )
        }
    }

    private fun importTypes() = buildList {
        add(JSON)
        add("application/xml")
        add("text/xml")
        if (!isQPlus()) add("application/octet-stream")
    }.toTypedArray()

    private fun openBackup(uri: Uri) {
        state = state.copy(busy = true)
        activity.lifecycleScope.launch {
            val importer = MessagesImporter(activity)
            val opened = withContext(Dispatchers.IO) { runCatching { importer.open(uri) } }
            val file = opened.getOrElse { error ->
                state = state.copy(busy = false)
                report(
                    activity.getString(
                        if (error is InvalidBackupException) {
                            com.afsh.commons.R.string.invalid_file_format
                        } else {
                            com.afsh.commons.R.string.importing_failed
                        },
                    ),
                )
                return@launch
            }
            when (file) {
                BackupFile.SmsXml -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { importer.restoreSmsXml(uri) }.getOrDefault(ImportResult.IMPORT_FAIL)
                    }
                    state = state.copy(busy = false)
                    report(activity.getString(result.message()))
                }

                is BackupFile.Messages -> {
                    backup = file
                    val name = DocumentFile.fromSingleUri(activity, uri)?.name ?: uri.lastPathSegment.orEmpty()
                    state = state.copy(
                        busy = false,
                        review = ImportReview(
                            fileName = name,
                            conversations = file.conversationCount,
                            sms = file.smsCount,
                            mms = file.mmsCount,
                            restoreSms = file.smsCount > 0 && activity.config.importSms,
                            restoreMms = file.mmsCount > 0 && activity.config.importMms,
                        ),
                    )
                    pages += SettingsPage.IMPORT_REVIEW
                }
            }
        }
    }

    private fun restore() {
        val file = backup ?: return
        val review = state.review ?: return
        activity.config.importSms = review.restoreSms
        activity.config.importMms = review.restoreMms
        state = state.copy(busy = true)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MessagesImporter(activity).restore(file, review.restoreSms, review.restoreMms) }
                    .getOrDefault(ImportResult.IMPORT_FAIL)
            }
            backup = null
            state = state.copy(busy = false, review = null)
            if (pages.last() == SettingsPage.IMPORT_REVIEW) handleBack()
            report(activity.getString(result.message()))
        }
    }

    private fun ImportResult.message() = when (this) {
        ImportResult.IMPORT_OK -> com.afsh.commons.R.string.importing_successful
        ImportResult.IMPORT_PARTIAL -> com.afsh.commons.R.string.importing_some_entries_failed
        ImportResult.IMPORT_FAIL -> com.afsh.commons.R.string.importing_failed
        ImportResult.IMPORT_NOTHING_NEW -> com.afsh.commons.R.string.no_items_found
    }

    private fun report(message: String) {
        activity.lifecycleScope.launch { snackbarHost.showSnackbar(message) }
    }

    private companion object {
        const val PAGES = "settings_pages"
        const val JSON = "application/json"
        const val DARK_LUMINANCE = 0.5
        const val HELP_URL = "https://github.com/AfshinJalili/Messages/issues"
        const val RELEASES_URL = "https://github.com/AfshinJalili/Messages/releases/latest"
    }
}

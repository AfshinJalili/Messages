package org.fossify.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.emptyMessagesRecycleBin
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.BackupFile
import org.fossify.messages.helpers.ExportOutcome
import org.fossify.messages.helpers.InvalidBackupException
import org.fossify.messages.helpers.MessagesExporter
import org.fossify.messages.helpers.MessagesImporter
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.ImportResult
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.settings.ImportReview
import org.fossify.messages.ui.settings.SettingsEvent
import org.fossify.messages.ui.settings.SettingsPage
import org.fossify.messages.ui.settings.SettingsScreen
import org.fossify.messages.ui.settings.SettingsUiState
import org.fossify.messages.ui.settings.ThemeChoice
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Settings (24) and its pages, kept as a stack so Back walks out page by page. Settings that
 * belong to Android (notifications, language) open the system screens.
 */
class SettingsActivity : ComposePageActivity() {
    private val pages = mutableStateListOf(SettingsPage.OVERVIEW)
    private var state by mutableStateOf(SettingsUiState())
    private var backup: BackupFile.Messages? = null

    private val chooseBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::openBackup)
    }
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let(::exportTo)
    }

    @Composable
    override fun Page() {
        SettingsScreen(
            state = state,
            page = pages.last(),
            navigation = PageNavigation(TopDestination.SETTINGS, ::navigateTo),
            snackbarHostState = snackbarHost,
            onBack = ::back,
            onEvent = ::onEvent,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(PAGES)?.let { saved ->
            pages.clear()
            pages += saved.mapNotNull { name -> SettingsPage.entries.firstOrNull { it.name == name } }
                // The opened backup does not survive recreation; its review page goes with it.
                .filter { it != SettingsPage.IMPORT_REVIEW }
                .ifEmpty { listOf(SettingsPage.OVERVIEW) }
        }
        state = state.copy(exportName = "${getString(R.string.messages)}_${getCurrentFormattedDateTime()}")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(PAGES, ArrayList(pages.map { it.name }))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        state = readSettings()
        lifecycleScope.launch {
            val recycled = withContext(Dispatchers.IO) { runCatching { messagesDB.getArchivedCount() }.getOrDefault(0) }
            state = state.copy(recycleBinMessages = recycled)
        }
    }

    private fun readSettings() = state.copy(
        theme = when {
            config.isSystemThemeEnabled -> ThemeChoice.SYSTEM
            ColorUtils.calculateLuminance(config.backgroundColor) < DARK_LUMINANCE -> ThemeChoice.DARK
            else -> ThemeChoice.LIGHT
        },
        fontSize = config.fontSize,
        language = Locale.getDefault().displayLanguage,
        languagePicker = isTiramisuPlus(),
        useEnglish = config.useEnglish.takeIf {
            !isTiramisuPlus() && (config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        },
        dateFormat = config.dateFormat,
        use24Hour = config.use24HourFormat,
        sendOnEnter = config.sendOnEnter,
        characterCounter = config.showCharacterCounter,
        simpleCharacters = config.useSimpleCharacters,
        deliveryReports = config.enableDeliveryReports,
        longAsMms = config.sendLongMessageMMS,
        groupAsMms = config.sendGroupMessageMMS,
        mmsLimit = config.mmsFileSizeLimit,
        alertsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        lockScreen = config.lockScreenVisibilitySetting,
        swipeLeft = config.swipeLeftAction,
        swipeRight = config.swipeRightAction,
        archiveAvailable = config.isArchiveAvailable,
        keepArchived = config.keepConversationsArchived,
        appLock = config.isAppPasswordProtectionOn,
        useRecycleBin = config.useRecycleBin,
        recentSearches = config.recentSearches.size,
        exportSms = config.exportSms,
        exportMms = config.exportMms,
        version = BuildConfig.VERSION_NAME,
    )

    private fun back() {
        if (pages.size > 1) pages.removeAt(pages.lastIndex) else finish()
    }

    override fun onBackPressedCompat(): Boolean {
        if (pages.size > 1) {
            back()
            return true
        }
        return super.onBackPressedCompat()
    }

    override fun navigateTo(destination: TopDestination) {
        if (destination == TopDestination.SETTINGS) {
            pages.removeRange(1, pages.size)
        } else {
            super.navigateTo(destination)
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per setting; each is a single write.
    private fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Open -> pages += event.page
            SettingsEvent.OpenBlockRules -> startActivity(Intent(this, BlockRulesActivity::class.java))
            is SettingsEvent.SetTheme -> setTheme(event.theme)
            is SettingsEvent.SetFontSize -> update { config.fontSize = event.size }.also { refreshTheme() }
            SettingsEvent.ChangeLanguage -> launchChangeAppLanguageIntent()
            is SettingsEvent.SetUseEnglish -> {
                config.useEnglish = event.enabled
                // The language applies from the next start, as it always has here.
                exitProcess(0)
            }

            is SettingsEvent.SetDateFormat -> update { config.dateFormat = event.format }.also { refreshConversations() }
            is SettingsEvent.SetUse24Hour -> update { config.use24HourFormat = event.enabled }.also { refreshConversations() }
            is SettingsEvent.SetSendOnEnter -> update { config.sendOnEnter = event.enabled }
            is SettingsEvent.SetCharacterCounter -> update { config.showCharacterCounter = event.enabled }
            is SettingsEvent.SetSimpleCharacters -> update { config.useSimpleCharacters = event.enabled }
            is SettingsEvent.SetDeliveryReports -> update { config.enableDeliveryReports = event.enabled }
            is SettingsEvent.SetLongAsMms -> update { config.sendLongMessageMMS = event.enabled }
            is SettingsEvent.SetGroupAsMms -> update { config.sendGroupMessageMMS = event.enabled }
            is SettingsEvent.SetMmsLimit -> update { config.mmsFileSizeLimit = event.bytes }
            SettingsEvent.OpenAlertSettings -> openAppNotificationSettings()
            SettingsEvent.OpenSoundSettings -> launchCustomizeNotificationsIntent()
            is SettingsEvent.SetLockScreen -> update { config.lockScreenVisibilitySetting = event.visibility }
            is SettingsEvent.SetSwipe -> update {
                if (event.towardsRight) config.swipeRightAction = event.action else config.swipeLeftAction = event.action
            }

            is SettingsEvent.SetKeepArchived -> update { config.keepConversationsArchived = event.enabled }
            SettingsEvent.ToggleAppLock -> toggleAppLock()
            is SettingsEvent.SetUseRecycleBin -> update { config.useRecycleBin = event.enabled }
            SettingsEvent.EmptyRecycleBin -> lifecycleScope.launch {
                withContext(Dispatchers.IO) { emptyMessagesRecycleBin() }
                state = state.copy(recycleBinMessages = 0)
            }

            SettingsEvent.ClearRecentSearches -> update { config.recentSearches = emptyList() }
            is SettingsEvent.SetExportSms -> update { config.exportSms = event.enabled }
            is SettingsEvent.SetExportMms -> update { config.exportMms = event.enabled }
            is SettingsEvent.SetExportName -> state = state.copy(exportName = event.name)
            SettingsEvent.CreateBackup -> startExport()
            SettingsEvent.ChooseBackup -> chooseBackup.launch(importTypes())
            is SettingsEvent.SetRestoreSms -> state.review?.let { state = state.copy(review = it.copy(restoreSms = event.enabled)) }
            is SettingsEvent.SetRestoreMms -> state.review?.let { state = state.copy(review = it.copy(restoreMms = event.enabled)) }
            SettingsEvent.Restore -> restore()
            SettingsEvent.OpenHelp -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL)))
        }
    }

    /** Writes one setting, then re-reads the page from the stored values. */
    private inline fun update(write: () -> Unit) {
        write()
        state = readSettings()
    }

    private fun setTheme(theme: ThemeChoice) {
        update {
            config.isSystemThemeEnabled = theme == ThemeChoice.SYSTEM
            if (theme != ThemeChoice.SYSTEM) {
                val dark = theme == ThemeChoice.DARK
                config.backgroundColor = getColor(
                    if (dark) org.fossify.commons.R.color.theme_dark_background_color else org.fossify.commons.R.color.theme_light_background_color,
                )
                config.textColor = getColor(
                    if (dark) org.fossify.commons.R.color.theme_dark_text_color else org.fossify.commons.R.color.theme_light_text_color,
                )
            }
        }
        refreshTheme()
    }

    private fun openAppNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    /** Commons' security dialog sets or removes the lock; its result decides the switch. */
    private fun toggleAppLock() {
        val tab = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
        SecurityDialog(this, config.appPasswordHash, tab) { hash, type, success ->
            if (!success) return@SecurityDialog
            val wasOn = config.isAppPasswordProtectionOn
            config.isAppPasswordProtectionOn = !wasOn
            config.appPasswordHash = if (wasOn) "" else hash
            config.appProtectionType = type
            state = readSettings()
            if (!wasOn) {
                val message = if (type == PROTECTION_FINGERPRINT) {
                    org.fossify.commons.R.string.fingerprint_setup_successfully
                } else {
                    org.fossify.commons.R.string.protection_setup_successfully
                }
                ConfirmationDialog(this, "", message, org.fossify.commons.R.string.ok, 0) {}
            }
        }
    }

    private fun startExport() {
        val name = state.exportName.trim()
        if (!name.isAValidFilename()) {
            report(getString(org.fossify.commons.R.string.invalid_name))
            return
        }
        createBackup.launch("$name.json")
    }

    private fun exportTo(uri: Uri) {
        state = state.copy(busy = true)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { MessagesExporter(this@SettingsActivity).export(uri, state.exportSms, state.exportMms) }
            state = state.copy(busy = false)
            report(
                getString(
                    when (outcome) {
                        ExportOutcome.EXPORTED -> org.fossify.commons.R.string.exporting_successful
                        ExportOutcome.NOTHING_TO_EXPORT -> org.fossify.commons.R.string.no_entries_for_exporting
                        ExportOutcome.FAILED -> org.fossify.commons.R.string.exporting_failed
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

    /** Opens the chosen file: the app's JSON goes to review (31); SMS XML restores at once, as before. */
    private fun openBackup(uri: Uri) {
        state = state.copy(busy = true)
        lifecycleScope.launch {
            val importer = MessagesImporter(this@SettingsActivity)
            val opened = withContext(Dispatchers.IO) { runCatching { importer.open(uri) } }
            val file = opened.getOrElse { error ->
                state = state.copy(busy = false)
                report(getString(if (error is InvalidBackupException) org.fossify.commons.R.string.invalid_file_format else org.fossify.commons.R.string.importing_failed))
                return@launch
            }
            when (file) {
                BackupFile.SmsXml -> {
                    val result = withContext(Dispatchers.IO) { runCatching { importer.restoreSmsXml(uri) }.getOrDefault(ImportResult.IMPORT_FAIL) }
                    state = state.copy(busy = false)
                    report(getString(result.message()))
                }

                is BackupFile.Messages -> {
                    backup = file
                    val name = DocumentFile.fromSingleUri(this@SettingsActivity, uri)?.name ?: uri.lastPathSegment.orEmpty()
                    state = state.copy(
                        busy = false,
                        review = ImportReview(
                            fileName = name,
                            conversations = file.conversationCount,
                            sms = file.smsCount,
                            mms = file.mmsCount,
                            restoreSms = file.smsCount > 0 && config.importSms,
                            restoreMms = file.mmsCount > 0 && config.importMms,
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
        config.importSms = review.restoreSms
        config.importMms = review.restoreMms
        state = state.copy(busy = true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MessagesImporter(this@SettingsActivity).restore(file, review.restoreSms, review.restoreMms) }
                    .getOrDefault(ImportResult.IMPORT_FAIL)
            }
            backup = null
            state = state.copy(busy = false, review = null)
            if (pages.last() == SettingsPage.IMPORT_REVIEW) back()
            report(getString(result.message()))
        }
    }

    private fun ImportResult.message() = when (this) {
        ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
        ImportResult.IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
        ImportResult.IMPORT_FAIL -> org.fossify.commons.R.string.importing_failed
        ImportResult.IMPORT_NOTHING_NEW -> org.fossify.commons.R.string.no_items_found
    }

    private fun report(message: String) {
        lifecycleScope.launch { snackbarHost.showSnackbar(message) }
    }

    private companion object {
        const val PAGES = "settings_pages"
        const val JSON = "application/json"
        const val DARK_LUMINANCE = 0.5
        const val HELP_URL = "https://github.com/AfshinJalili/Messages/issues"
    }
}

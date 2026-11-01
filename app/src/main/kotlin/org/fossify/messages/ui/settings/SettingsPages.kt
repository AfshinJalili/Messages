package org.fossify.messages.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.commons.helpers.DATE_FORMAT_EIGHT
import org.fossify.commons.helpers.DATE_FORMAT_FIVE
import org.fossify.commons.helpers.DATE_FORMAT_FOUR
import org.fossify.commons.helpers.DATE_FORMAT_ONE
import org.fossify.commons.helpers.DATE_FORMAT_SEVEN
import org.fossify.commons.helpers.DATE_FORMAT_SIX
import org.fossify.commons.helpers.DATE_FORMAT_THREE
import org.fossify.commons.helpers.DATE_FORMAT_TWO
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import org.fossify.messages.R
import org.fossify.messages.helpers.FILE_SIZE_100_KB
import org.fossify.messages.helpers.FILE_SIZE_1_MB
import org.fossify.messages.helpers.FILE_SIZE_200_KB
import org.fossify.messages.helpers.FILE_SIZE_2_MB
import org.fossify.messages.helpers.FILE_SIZE_300_KB
import org.fossify.messages.helpers.FILE_SIZE_600_KB
import org.fossify.messages.helpers.FILE_SIZE_NONE
import org.fossify.messages.helpers.LOCK_SCREEN_NOTHING
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER_MESSAGE
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.Choice
import org.fossify.messages.ui.components.ChoiceDialog
import org.fossify.messages.ui.components.ConfirmationDialog
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.NavigationSetting
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.SettingItem
import org.fossify.messages.ui.components.SwitchSetting

private val ThemeCardHeight = 98.dp
private val PreviewLineWidth = 62.dp
private val PreviewLineHeight = 8.dp
private val SwatchHeight = 28.dp
private val LockPreviewIconSize = 20.dp

private val dateFormats = listOf(
    DATE_FORMAT_ONE, DATE_FORMAT_TWO, DATE_FORMAT_THREE, DATE_FORMAT_FOUR,
    DATE_FORMAT_FIVE, DATE_FORMAT_SIX, DATE_FORMAT_SEVEN, DATE_FORMAT_EIGHT,
)

/** Which picker a page has open; saved so it survives rotation. */
private enum class Picker { FONT_SIZE, DATE_FORMAT, MMS_LIMIT, LOCK_SCREEN, EMPTY_BIN, CLEAR_SEARCHES }

@Composable
private fun cancel() = stringResource(org.fossify.commons.R.string.cancel)

/** 25 · Settings / appearance. */
@Composable
internal fun AppearancePage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val today = remember { System.currentTimeMillis() }
    SectionHeading(stringResource(R.string.settings_theme))
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeChoice.entries.forEach { theme ->
            ThemeCard(theme, selected = theme == state.theme, onClick = { onEvent(SettingsEvent.SetTheme(theme)) }, Modifier.weight(1f))
        }
    }
    SectionHeading(stringResource(R.string.settings_reading))
    NavigationSetting(
        stringResource(R.string.text_size),
        onClick = { picker = Picker.FONT_SIZE },
        icon = R.drawable.ic_lucide_type,
        value = stringResource(R.string.text_size_value, stringResource(fontSizeLabel(state.fontSize))),
    )
    if (state.languagePicker) {
        NavigationSetting(
            stringResource(org.fossify.commons.R.string.language),
            onClick = { onEvent(SettingsEvent.ChangeLanguage) },
            icon = R.drawable.ic_lucide_languages,
            value = state.language,
        )
    }
    state.useEnglish?.let { useEnglish ->
        SwitchSetting(
            stringResource(org.fossify.commons.R.string.use_english_language),
            checked = useEnglish,
            onCheckedChange = { onEvent(SettingsEvent.SetUseEnglish(it)) },
            icon = R.drawable.ic_lucide_languages,
        )
    }
    NavigationSetting(
        stringResource(R.string.date_and_time),
        onClick = { picker = Picker.DATE_FORMAT },
        icon = R.drawable.ic_lucide_calendar,
        value = formatSample(state.dateFormat, today),
    )
    SwitchSetting(
        stringResource(R.string.use_24_hour_time),
        checked = state.use24Hour,
        onCheckedChange = { onEvent(SettingsEvent.SetUse24Hour(it)) },
        icon = R.drawable.ic_lucide_clock,
    )
    when (picker) {
        Picker.FONT_SIZE -> ChoiceDialog(
            title = stringResource(R.string.text_size),
            choices = listOf(FONT_SIZE_SMALL, FONT_SIZE_MEDIUM, FONT_SIZE_LARGE, FONT_SIZE_EXTRA_LARGE)
                .map { Choice(it, stringResource(fontSizeLabel(it))) },
            selected = state.fontSize,
            dismissLabel = cancel(),
            onChoose = {
                picker = null
                onEvent(SettingsEvent.SetFontSize(it))
            },
            onDismiss = { picker = null },
        )

        Picker.DATE_FORMAT -> ChoiceDialog(
            title = stringResource(R.string.date_and_time),
            choices = dateFormats.map { Choice(it, formatSample(it, today)) },
            selected = state.dateFormat,
            dismissLabel = cancel(),
            onChoose = {
                picker = null
                onEvent(SettingsEvent.SetDateFormat(it))
            },
            onDismiss = { picker = null },
        )

        else -> Unit
    }
}

/** A pattern shown as the date it produces, which reads better than the pattern itself. */
private fun formatSample(pattern: String, millis: Long) = DateFormat.format(pattern, millis).toString()

private fun fontSizeLabel(size: Int) = when (size) {
    FONT_SIZE_SMALL -> org.fossify.commons.R.string.small
    FONT_SIZE_LARGE -> org.fossify.commons.R.string.large
    FONT_SIZE_EXTRA_LARGE -> org.fossify.commons.R.string.extra_large
    else -> org.fossify.commons.R.string.medium
}

/** A theme card: its name over a line in the colour it leads with; the chosen one is lilac. */
@Composable
private fun ThemeCard(theme: ThemeChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = ThemeCardHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(theme.label),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
        )
        ThemeSwatch(theme)
        if (selected) {
            Icon(painterResource(R.drawable.ic_lucide_check), contentDescription = null, tint = colors.primary)
        }
    }
}

/**
 * A small picture of the theme itself: a light page with a pine line, a dark page with a lime
 * line, or the two side by side for System. Showing only the line colour read reversed, since the
 * dark theme leads with the brighter colour.
 */
@Composable
private fun ThemeSwatch(theme: ThemeChoice) {
    @Composable
    fun Page(dark: Boolean, modifier: Modifier) = Box(
        modifier
            .fillMaxHeight()
            .background(colorResource(if (dark) R.color.surface_dark else R.color.surface_light))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PreviewLineHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (dark) OpenLine.colors.accent else colorResource(R.color.open_line_pine)),
        )
    }
    Row(
        Modifier
            .width(PreviewLineWidth)
            .height(SwatchHeight)
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
    ) {
        when (theme) {
            ThemeChoice.LIGHT -> Page(dark = false, Modifier.weight(1f))
            ThemeChoice.DARK -> Page(dark = true, Modifier.weight(1f))
            ThemeChoice.SYSTEM -> {
                Page(dark = false, Modifier.weight(1f))
                Page(dark = true, Modifier.weight(1f))
            }
        }
    }
}

/** 26 · Settings / writing and sending. Binary settings are switches (R5-36). */
@Composable
internal fun WritingPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    @Composable
    fun onOff(enabled: Boolean) = stringResource(if (enabled) R.string.setting_on else R.string.setting_off)
    SectionHeading(stringResource(R.string.settings_composer))
    SwitchSetting(
        stringResource(R.string.send_on_enter),
        state.sendOnEnter,
        { onEvent(SettingsEvent.SetSendOnEnter(it)) },
        icon = R.drawable.ic_lucide_corner_down_left,
        value = onOff(state.sendOnEnter),
    )
    SwitchSetting(
        stringResource(R.string.show_character_counter),
        state.characterCounter,
        { onEvent(SettingsEvent.SetCharacterCounter(it)) },
        icon = R.drawable.ic_lucide_hash,
        value = stringResource(if (state.characterCounter) R.string.character_counter_on else R.string.setting_off),
    )
    SwitchSetting(
        stringResource(R.string.use_simple_characters),
        state.simpleCharacters,
        { onEvent(SettingsEvent.SetSimpleCharacters(it)) },
        icon = R.drawable.ic_lucide_type,
        value = onOff(state.simpleCharacters),
    )
    SectionHeading(stringResource(R.string.settings_delivery))
    SwitchSetting(
        stringResource(R.string.enable_delivery_reports),
        state.deliveryReports,
        { onEvent(SettingsEvent.SetDeliveryReports(it)) },
        icon = R.drawable.ic_lucide_check_check,
        value = onOff(state.deliveryReports),
    )
    SwitchSetting(
        stringResource(R.string.send_long_message_mms),
        state.longAsMms,
        { onEvent(SettingsEvent.SetLongAsMms(it)) },
        icon = R.drawable.ic_lucide_message_square_text,
        value = onOff(state.longAsMms),
    )
    SwitchSetting(
        stringResource(R.string.group_message_mms),
        state.groupAsMms,
        { onEvent(SettingsEvent.SetGroupAsMms(it)) },
        icon = R.drawable.ic_lucide_users,
        value = onOff(state.groupAsMms),
    )
    val limits = listOf(
        FILE_SIZE_NONE to R.string.mms_file_size_limit_none,
        FILE_SIZE_2_MB to R.string.mms_file_size_limit_2mb,
        FILE_SIZE_1_MB to R.string.mms_file_size_limit_1mb,
        FILE_SIZE_600_KB to R.string.mms_file_size_limit_600kb,
        FILE_SIZE_300_KB to R.string.mms_file_size_limit_300kb,
        FILE_SIZE_200_KB to R.string.mms_file_size_limit_200kb,
        FILE_SIZE_100_KB to R.string.mms_file_size_limit_100kb,
    )
    NavigationSetting(
        stringResource(R.string.mms_file_size_limit),
        onClick = { picker = Picker.MMS_LIMIT },
        icon = R.drawable.ic_lucide_image,
        value = stringResource(limits.firstOrNull { it.first == state.mmsLimit }?.second ?: R.string.mms_file_size_limit_none),
    )
    if (picker == Picker.MMS_LIMIT) {
        ChoiceDialog(
            title = stringResource(R.string.mms_file_size_limit),
            choices = limits.map { Choice(it.first, stringResource(it.second)) },
            selected = state.mmsLimit,
            dismissLabel = cancel(),
            onChoose = {
                picker = null
                onEvent(SettingsEvent.SetMmsLimit(it))
            },
            onDismiss = { picker = null },
        )
    }
}

/** 27 · Settings / notifications. Alerts, sound and vibration live in Android's notification settings. */
@Composable
internal fun NotificationsPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val lockLabels = mapOf(
        LOCK_SCREEN_SENDER_MESSAGE to R.string.sender_and_message,
        LOCK_SCREEN_SENDER to R.string.sender_only,
        LOCK_SCREEN_NOTHING to org.fossify.commons.R.string.nothing,
    )
    SectionHeading(stringResource(R.string.settings_alerts))
    NavigationSetting(
        stringResource(R.string.new_message_alerts),
        onClick = { onEvent(SettingsEvent.OpenAlertSettings) },
        icon = R.drawable.ic_lucide_bell,
        value = stringResource(if (state.alertsEnabled) R.string.setting_on else R.string.setting_off),
    )
    NavigationSetting(
        stringResource(R.string.sound_and_vibration),
        onClick = { onEvent(SettingsEvent.OpenSoundSettings) },
        icon = R.drawable.ic_lucide_volume_2,
        value = stringResource(R.string.sound_and_vibration_value),
    )
    SectionHeading(stringResource(R.string.settings_lock_screen))
    LockScreenPreview(state.lockScreen)
    NavigationSetting(
        stringResource(R.string.lock_screen_visibility),
        onClick = { picker = Picker.LOCK_SCREEN },
        icon = R.drawable.ic_lucide_eye,
        value = stringResource(lockLabels[state.lockScreen] ?: org.fossify.commons.R.string.nothing),
    )
    SettingItem(
        stringResource(R.string.per_conversation_alerts),
        icon = R.drawable.ic_lucide_bell,
        value = stringResource(R.string.per_conversation_alerts_value),
    )
    if (picker == Picker.LOCK_SCREEN) {
        ChoiceDialog(
            title = stringResource(R.string.lock_screen_visibility),
            choices = lockLabels.map { (value, label) -> Choice(value, stringResource(label)) },
            selected = state.lockScreen,
            dismissLabel = cancel(),
            onChoose = {
                picker = null
                onEvent(SettingsEvent.SetLockScreen(it))
            },
            onDismiss = { picker = null },
        )
    }
}

/** What a locked phone shows for a new message with the current choice. */
@Composable
private fun LockScreenPreview(visibility: Int) {
    val colors = MaterialTheme.colorScheme
    val sender = stringResource(if (visibility == LOCK_SCREEN_NOTHING) R.string.lock_preview_app else R.string.lock_preview_sender)
    val body = stringResource(
        if (visibility == LOCK_SCREEN_SENDER_MESSAGE) R.string.lock_preview_message else R.string.lock_preview_hidden,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_lucide_message_circle),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(LockPreviewIconSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sender, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** 28 · Settings / privacy and lock. */
@Composable
internal fun PrivacyPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    SectionHeading(stringResource(R.string.settings_app_access))
    SwitchSetting(
        stringResource(R.string.app_lock),
        checked = state.appLock,
        onCheckedChange = { onEvent(SettingsEvent.ToggleAppLock) },
        icon = R.drawable.ic_lucide_lock_keyhole,
        value = stringResource(if (state.appLock) R.string.app_lock_on else R.string.app_lock_off),
    )
    SectionHeading(stringResource(R.string.settings_data))
    SwitchSetting(
        stringResource(org.fossify.commons.R.string.move_items_into_recycle_bin),
        checked = state.useRecycleBin,
        onCheckedChange = { onEvent(SettingsEvent.SetUseRecycleBin(it)) },
        icon = R.drawable.ic_lucide_trash_2,
        value = stringResource(R.string.recycle_bin_setting_value),
    )
    if (state.useRecycleBin) {
        NavigationSetting(
            stringResource(org.fossify.commons.R.string.empty_recycle_bin),
            onClick = { if (state.recycleBinMessages > 0) picker = Picker.EMPTY_BIN },
            icon = R.drawable.ic_lucide_trash_2,
            value = pluralStringResource(R.plurals.delete_messages, state.recycleBinMessages, state.recycleBinMessages),
            disclosure = false,
            destructive = state.recycleBinMessages > 0,
        )
    }
    NavigationSetting(
        stringResource(R.string.clear_recent_searches),
        onClick = { if (state.recentSearches > 0) picker = Picker.CLEAR_SEARCHES },
        icon = R.drawable.ic_lucide_search,
        value = pluralStringResource(R.plurals.recent_search_count, state.recentSearches, state.recentSearches),
        disclosure = false,
    )
    InfoPanel(
        title = stringResource(R.string.no_account_required),
        body = stringResource(R.string.no_account_required_text),
        modifier = Modifier.padding(top = 12.dp),
        prominent = true,
    )
    when (picker) {
        Picker.EMPTY_BIN -> ConfirmationDialog(
            title = stringResource(R.string.empty_bin_title),
            explanation = stringResource(R.string.empty_recycle_bin_messages_confirmation),
            confirmLabel = stringResource(R.string.recycle_bin_empty),
            dismissLabel = cancel(),
            onConfirm = {
                picker = null
                onEvent(SettingsEvent.EmptyRecycleBin)
            },
            onDismiss = { picker = null },
            destructive = true,
        )

        Picker.CLEAR_SEARCHES -> ConfirmationDialog(
            title = stringResource(R.string.clear_recent_searches_title),
            explanation = stringResource(R.string.clear_recent_searches_text),
            confirmLabel = stringResource(R.string.clear_recent_searches),
            dismissLabel = cancel(),
            onConfirm = {
                picker = null
                onEvent(SettingsEvent.ClearRecentSearches)
            },
            onDismiss = { picker = null },
        )

        else -> Unit
    }
}

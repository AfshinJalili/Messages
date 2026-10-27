package org.fossify.messages.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.SwitchSetting
import org.fossify.messages.ui.components.TextFieldSlab

private val ActionIconSize = 24.dp

/** 29 · Settings / backup and restore. */
@Composable
internal fun BackupPage(onEvent: (SettingsEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        BackupAction(
            icon = R.drawable.ic_lucide_download,
            title = stringResource(R.string.export_messages),
            detail = stringResource(R.string.export_messages_detail),
            onClick = { onEvent(SettingsEvent.Open(SettingsPage.EXPORT)) },
        )
        BackupAction(
            icon = R.drawable.ic_lucide_upload,
            title = stringResource(R.string.import_messages),
            detail = stringResource(R.string.import_messages_detail),
            onClick = { onEvent(SettingsEvent.ChooseBackup) },
        )
        InfoPanel(
            title = stringResource(R.string.keep_backup_private),
            body = stringResource(R.string.keep_backup_private_text),
            prominent = true,
        )
    }
}

/** A white tile with an icon, a heading and what it does. */
@Composable
private fun BackupAction(icon: Int, title: String, detail: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(ActionIconSize))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** 30 · Export / options: what goes into the file, its name, then the system's save dialog. */
@Composable
internal fun ExportPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    SwitchSetting(
        stringResource(R.string.export_sms),
        checked = state.exportSms,
        onCheckedChange = { onEvent(SettingsEvent.SetExportSms(it)) },
        icon = R.drawable.ic_lucide_messages_square,
        enabled = !state.busy,
    )
    SwitchSetting(
        stringResource(R.string.export_mms),
        checked = state.exportMms,
        onCheckedChange = { onEvent(SettingsEvent.SetExportMms(it)) },
        icon = R.drawable.ic_lucide_paperclip,
        value = stringResource(R.string.export_mms_detail),
        enabled = !state.busy,
    )
    SectionHeading(stringResource(R.string.file_name))
    TextFieldSlab(
        value = state.exportName,
        onValueChange = { onEvent(SettingsEvent.SetExportName(it)) },
        accessibilityLabel = stringResource(R.string.file_name),
    )
    Text(
        stringResource(R.string.export_destination),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
    OpenLineButton(
        stringResource(R.string.create_backup),
        onClick = { onEvent(SettingsEvent.CreateBackup) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy && (state.exportSms || state.exportMms) && state.exportName.isNotBlank(),
    )
}

/** 31 · Import / review backup: what the file holds, what to restore, then restore. */
@Composable
internal fun ImportReviewPage(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val review = state.review ?: return
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(review.fileName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = colors.onSurface)
        Text(
            stringResource(
                R.string.rules_summary,
                pluralStringResource(R.plurals.backup_conversations, review.conversations, String.format(locale, "%,d", review.conversations)),
                pluralStringResource(R.plurals.backup_messages, review.messages, String.format(locale, "%,d", review.messages)),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
    SectionHeading(stringResource(R.string.settings_review))
    SwitchSetting(
        stringResource(R.string.import_sms),
        checked = review.restoreSms,
        onCheckedChange = { onEvent(SettingsEvent.SetRestoreSms(it)) },
        icon = R.drawable.ic_lucide_messages_square,
        value = pluralStringResource(R.plurals.backup_messages, review.sms, String.format(locale, "%,d", review.sms)),
        enabled = !state.busy && review.sms > 0,
    )
    SwitchSetting(
        stringResource(R.string.import_mms),
        checked = review.restoreMms,
        onCheckedChange = { onEvent(SettingsEvent.SetRestoreMms(it)) },
        icon = R.drawable.ic_lucide_paperclip,
        value = pluralStringResource(R.plurals.backup_messages, review.mms, String.format(locale, "%,d", review.mms)),
        enabled = !state.busy && review.mms > 0,
    )
    InfoPanel(
        title = stringResource(R.string.nothing_is_removed),
        body = stringResource(R.string.nothing_is_removed_text),
        modifier = Modifier.padding(vertical = 12.dp),
        prominent = true,
    )
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
    OpenLineButton(
        stringResource(R.string.restore_messages),
        onClick = { onEvent(SettingsEvent.Restore) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy && (review.restoreSms || review.restoreMms),
    )
}

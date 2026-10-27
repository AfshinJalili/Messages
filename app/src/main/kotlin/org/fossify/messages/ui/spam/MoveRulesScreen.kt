package org.fossify.messages.ui.spam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageContentPadding
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold

/** 51 · Spam / move rule list: export this device's rules, or merge a file into them. */
@Composable
fun MoveRulesScreen(
    numbers: Int,
    phrases: Int,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PageScaffold(
        title = stringResource(R.string.move_block_rules),
        modifier = modifier,
        subtitle = stringResource(R.string.move_block_rules_subtitle),
        onBack = onBack,
        navigation = navigation,
        snackbarHostState = snackbarHostState,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.what_will_be_exported),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onSurface,
                )
                Text(
                    stringResource(
                        R.string.rules_summary,
                        pluralStringResource(R.plurals.blocked_number_count, numbers, numbers),
                        pluralStringResource(R.plurals.blocked_phrase_count, phrases, phrases),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.rules_export_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            OpenLineButton(
                stringResource(R.string.export_block_list),
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = numbers + phrases > 0,
            )
            InfoPanel(
                title = stringResource(R.string.have_a_block_list),
                body = stringResource(R.string.have_a_block_list_text),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClick = onImport),
                prominent = true,
                contentPadding = PaddingValues(16.dp),
            )
        }
    }
}

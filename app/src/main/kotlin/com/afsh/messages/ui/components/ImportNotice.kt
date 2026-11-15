package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.helpers.ImportProgress

/** The first import, in the inbox and search, on a Material 3 [androidx.compose.material3.Card]: what it is doing and how far it has got. */
@Composable
fun ImportNotice(progress: ImportProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val detail = when (progress.phase) {
        ImportProgress.Phase.CONVERSATIONS -> R.string.import_progress_conversations
        ImportProgress.Phase.MESSAGES -> R.string.import_progress_messages
    }
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier.semantics(mergeDescendants = true) {},
        spacing = 8.dp,
    ) {
        Text(
            stringResource(R.string.import_progress_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            stringResource(detail, progress.done, progress.total),
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primary,
            trackColor = colors.outlineVariant,
        )
    }
}

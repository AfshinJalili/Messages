package com.afsh.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.helpers.ImportProgress

/** The first import, in the inbox and search: what it is doing and how far it has got. */
@Composable
fun ImportNotice(progress: ImportProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val detail = when (progress.phase) {
        ImportProgress.Phase.CONVERSATIONS -> R.string.import_conversations
        ImportProgress.Phase.MESSAGES -> R.string.import_messages
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.secondaryContainer)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.import_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSecondaryContainer,
        )
        Text(
            stringResource(detail, progress.done, progress.total),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSecondaryContainer,
        )
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primary,
            trackColor = colors.outlineVariant,
        )
    }
}

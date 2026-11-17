package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.afsh.messages.R
import com.afsh.messages.helpers.ImportProgress
import com.afsh.messages.ui.Afsh

/**
 * The first-run setup card in the inbox and search: one fixed label and one progress row with a
 * tabular percentage. Conversation reconcile and message indexing share this card; the percent
 * only rises, and the bar width stays fixed because the percent column is sized for "100%".
 */
@Composable
fun ImportNotice(progress: ImportProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val percentStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val percentWidth = remember(percentStyle, density) {
        with(density) {
            textMeasurer.measure(text = "100%", style = percentStyle).size.width.toDp()
        }
    }
    PanelCard(
        colors.secondaryContainer,
        colors.onSecondaryContainer,
        modifier.semantics(mergeDescendants = true) {},
        spacing = Afsh.spacing.sm,
    ) {
        Text(
            stringResource(R.string.import_progress_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.weight(1f),
                color = colors.primary,
                trackColor = colors.outlineVariant,
            )
            Text(
                stringResource(R.string.import_progress_percent, progress.percent),
                modifier = Modifier.width(percentWidth),
                style = percentStyle,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

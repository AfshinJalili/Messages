package com.afsh.messages.ui.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.afsh.messages.R
import com.afsh.messages.helpers.SpamPatternGroup
import com.afsh.messages.helpers.SpamPatterns
import com.afsh.messages.helpers.plainFilterLabels
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.withContentFonts

/**
 * Lists the Persian marketing words and promo emojis the local filter looks for, as ordinary text
 * (not regex). Advanced editing stays on the Spam patterns page.
 */
@Composable
fun WhatFilterLooksForScreen(
    navigation: PageNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    patterns: SpamPatterns = SpamPatterns.DEFAULT,
) {
    val words = remember(patterns) { plainFilterLabels(patterns[SpamPatternGroup.MARKETING]) }
    val emojis = remember(patterns) {
        plainFilterLabels(patterns[SpamPatternGroup.PROMO_EMOJI]) +
            plainFilterLabels(patterns[SpamPatternGroup.PROMO_EMOJI_WEAK])
    }
    PageScaffold(
        title = stringResource(R.string.what_filter_looks_for),
        modifier = modifier,
        onBack = onBack,
        navigation = navigation,
    ) {
        LazyColumn(
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.filter_on_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "words-heading") { SectionHeading(stringResource(R.string.what_filter_words)) }
            items(words, key = { "w:$it" }) { word ->
                Text(
                    remember(word) { AnnotatedString(word).withContentFonts() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item(key = "emojis-heading") { SectionHeading(stringResource(R.string.what_filter_emojis)) }
            items(emojis, key = { "e:$it" }) { emoji ->
                Text(
                    emoji,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

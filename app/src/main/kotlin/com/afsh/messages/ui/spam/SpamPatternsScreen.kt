package com.afsh.messages.ui.spam

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.helpers.FilterResult
import com.afsh.messages.helpers.FilterRule
import com.afsh.messages.helpers.FilterVerdict
import com.afsh.messages.helpers.SpamPatternGroup
import com.afsh.messages.helpers.SpamPatterns
import com.afsh.messages.helpers.RuleBasedFilter
import com.afsh.messages.ui.components.AfshButton
import com.afsh.messages.ui.components.AttentionNotice
import com.afsh.messages.ui.components.ButtonKind
import com.afsh.messages.ui.components.ConfirmationDialog
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.TextFieldSlab
import com.afsh.messages.ui.components.TextInputDialog
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.withContentFonts

private val PatternRowHeight = 50.dp

@Immutable
data class SpamPatternsUiState(
    val patterns: SpamPatterns = SpamPatterns.DEFAULT,
    val filterEnabled: Boolean = true,
    val rechecking: Boolean = false,
)

/** Everything the Spam patterns page can ask its activity to do. */
sealed interface SpamPatternsEvent {
    data object Back : SpamPatternsEvent
    data class Save(val group: SpamPatternGroup, val index: Int?, val pattern: String) : SpamPatternsEvent
    data class Remove(val group: SpamPatternGroup, val index: Int) : SpamPatternsEvent
    data object Reset : SpamPatternsEvent
    data object Recheck : SpamPatternsEvent
}

@StringRes
private fun SpamPatternGroup.title() = when (this) {
    SpamPatternGroup.CODE_WORDS -> R.string.spam_patterns_code_words
    SpamPatternGroup.CODE -> R.string.spam_patterns_code
    SpamPatternGroup.UNSUBSCRIBE -> R.string.spam_patterns_unsubscribe
    SpamPatternGroup.LINKS -> R.string.spam_patterns_links
    SpamPatternGroup.MARKETING -> R.string.spam_patterns_marketing
}

@StringRes
private fun SpamPatternGroup.description() = when (this) {
    SpamPatternGroup.CODE_WORDS -> R.string.spam_patterns_code_words_text
    SpamPatternGroup.CODE -> R.string.spam_patterns_code_text
    SpamPatternGroup.UNSUBSCRIBE -> R.string.spam_patterns_unsubscribe_text
    SpamPatternGroup.LINKS -> R.string.spam_patterns_links_text
    SpamPatternGroup.MARKETING -> R.string.spam_patterns_marketing_text
}

/** The pattern being edited: [index] is null for a new one. */
private data class Editing(val group: SpamPatternGroup, val index: Int?, val current: String)

/**
 * Spam patterns: what the local rules look for, group by group, with a field that runs a message
 * through them and highlights what matched. For tuning the filter by hand.
 */
@Composable
fun SpamPatternsScreen(
    state: SpamPatternsUiState,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onEvent: (SpamPatternsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRecheck by remember { mutableStateOf(false) }
    PageScaffold(
        title = stringResource(R.string.spam_patterns),
        modifier = modifier,
        subtitle = stringResource(R.string.spam_patterns_subtitle),
        onBack = { onEvent(SpamPatternsEvent.Back) },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
    ) {
        LazyColumn(
            Modifier.imePadding(),
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.filterEnabled) {
                item(key = "off") {
                    AttentionNotice(
                        title = stringResource(R.string.spam_patterns_filter_off),
                        detail = stringResource(R.string.spam_patterns_filter_off_text),
                    )
                }
            }
            item(key = "try") { TryMessage(state.patterns) }
            SpamPatternGroup.entries.forEach { group ->
                item(key = "h:${group.key}") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeading(stringResource(group.title()))
                        Text(
                            stringResource(group.description()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(state.patterns[group], key = { index, _ -> "p:${group.key}:$index" }) { index, pattern ->
                    PatternRow(
                        pattern,
                        onEdit = { editing = Editing(group, index, pattern) },
                        onRemove = { onEvent(SpamPatternsEvent.Remove(group, index)) },
                    )
                }
                item(key = "a:${group.key}") { AddPattern { editing = Editing(group, null, "") } }
            }
            item(key = "actions") {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AfshButton(
                        stringResource(if (state.rechecking) R.string.spam_patterns_rechecking else R.string.spam_patterns_recheck),
                        onClick = { confirmRecheck = true },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.SECONDARY,
                        enabled = !state.rechecking,
                    )
                    AfshButton(
                        stringResource(R.string.spam_patterns_reset),
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.QUIET,
                        enabled = state.patterns != SpamPatterns.DEFAULT,
                    )
                }
            }
        }
    }
    editing?.let { current ->
        TextInputDialog(
            title = stringResource(if (current.index == null) R.string.spam_patterns_add else R.string.spam_patterns_edit),
            current = current.current,
            explanation = stringResource(R.string.spam_patterns_regex_hint),
            confirmLabel = stringResource(R.string.spam_patterns_save),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            error = { SpamPatterns.error(it) },
            onSave = {
                editing = null
                onEvent(SpamPatternsEvent.Save(current.group, current.index, it))
            },
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) {
        ConfirmationDialog(
            title = stringResource(R.string.spam_patterns_reset),
            explanation = stringResource(R.string.spam_patterns_reset_text),
            confirmLabel = stringResource(R.string.spam_patterns_reset),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmReset = false
                onEvent(SpamPatternsEvent.Reset)
            },
            onDismiss = { confirmReset = false },
            destructive = true,
        )
    }
    if (confirmRecheck) {
        ConfirmationDialog(
            title = stringResource(R.string.spam_patterns_recheck),
            explanation = stringResource(R.string.spam_patterns_recheck_text),
            confirmLabel = stringResource(R.string.spam_patterns_recheck),
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onConfirm = {
                confirmRecheck = false
                onEvent(SpamPatternsEvent.Recheck)
            },
            onDismiss = { confirmRecheck = false },
        )
    }
}

/** Runs a pasted message through the patterns as they are now and shows the verdict. */
@Composable
private fun TryMessage(patterns: SpamPatterns) {
    var body by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.spam_patterns_try))
        TextFieldSlab(
            value = body,
            onValueChange = { body = it },
            placeholder = stringResource(R.string.spam_patterns_try_message),
            singleLine = false,
            textStyle = MaterialTheme.typography.bodyLarge.forContent(body),
        )
        TextFieldSlab(
            value = sender,
            onValueChange = { sender = it },
            placeholder = stringResource(R.string.spam_patterns_try_sender),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.bodyLarge.forContent(sender),
        )
        if (body.isNotBlank()) {
            val result = remember(patterns, sender, body) { RuleBasedFilter.explain(patterns, sender, body) }
            val highlight = SpanStyle(
                background = if (result.verdict == FilterVerdict.ALLOW) colors.secondaryContainer else colors.errorContainer,
                color = if (result.verdict == FilterVerdict.ALLOW) colors.onSecondaryContainer else colors.onErrorContainer,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(result.label()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (result.verdict == FilterVerdict.BLOCK) colors.error else colors.onSurface,
                )
                Text(
                    remember(body, result, highlight) {
                        buildAnnotatedString {
                            append(body)
                            result.matches.forEach { addStyle(highlight, it.range.first, it.range.last + 1) }
                        }.withContentFonts()
                    },
                    style = MaterialTheme.typography.bodyLarge.forContent(body),
                    color = colors.onSurface,
                )
            }
        }
    }
}

@StringRes
private fun FilterResult.label() = when (rule) {
    FilterRule.ONE_TIME_CODE -> R.string.spam_patterns_result_code
    FilterRule.UNSUBSCRIBE -> R.string.spam_patterns_result_unsubscribe
    FilterRule.MARKETING -> R.string.spam_patterns_result_marketing
    null -> R.string.spam_patterns_result_none
}

@Composable
private fun PatternRow(pattern: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PatternRowHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.spam_patterns_edit), onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            remember(pattern) { AnnotatedString(pattern) },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                painterResource(R.drawable.ic_lucide_x),
                contentDescription = stringResource(R.string.spam_patterns_remove, pattern),
                tint = colors.error,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun AddPattern(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = PatternRowHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lucide_plus), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.spam_patterns_add),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
        )
    }
}

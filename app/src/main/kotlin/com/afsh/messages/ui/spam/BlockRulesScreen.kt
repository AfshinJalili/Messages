package com.afsh.messages.ui.spam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import kotlinx.coroutines.launch
import com.afsh.messages.R
import com.afsh.messages.ui.components.DestinationCard
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.SettingItem
import com.afsh.messages.ui.components.afshSwitchColors
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.withContentFonts

private val PhraseRowHeight = 50.dp
private const val PHRASES_HEADING_INDEX = 6

@Immutable
data class BlockRulesUiState(
    val alwaysSpamCount: Int = 0,
    val neverSpamCount: Int = 0,
    val androidBlockedCount: Int = 0,
    val phrases: List<String> = emptyList(),
    val filterEnabled: Boolean = true,
    val unknownNumbers: Boolean = false,
)

/** Everything 21 · Filter rules can ask its activity to do. */
sealed interface BlockRulesEvent {
    data object Back : BlockRulesEvent
    data object OpenNumbers : BlockRulesEvent
    data object MoveRules : BlockRulesEvent
    data object OpenPatterns : BlockRulesEvent
    data object OpenWhatFilterLooksFor : BlockRulesEvent
    data class SetFilter(val enabled: Boolean) : BlockRulesEvent
    data class SetUnknownNumbers(val enabled: Boolean) : BlockRulesEvent
    data object AddPhrase : BlockRulesEvent
    data object AddNumber : BlockRulesEvent
    data class EditPhrase(val phrase: String) : BlockRulesEvent
    data class RemovePhrase(val phrase: String) : BlockRulesEvent
}

/**
 * 21 · Filter rules: who and what is filtered, the plain-language local filter, and blocked phrases.
 */
@Composable
fun BlockRulesScreen(
    state: BlockRulesUiState,
    navigation: PageNavigation,
    onEvent: (BlockRulesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    PageScaffold(
        title = stringResource(R.string.filter_rules),
        modifier = modifier,
        onBack = { onEvent(BlockRulesEvent.Back) },
        navigation = navigation,
        actions = {
            PageAppBarAction(AfshIcon.Share, stringResource(R.string.move_filter_rules), onClick = {
                onEvent(BlockRulesEvent.MoveRules)
            })
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            item(key = "numbers") {
                val total = state.alwaysSpamCount + state.neverSpamCount + state.androidBlockedCount
                DestinationCard(
                    icon = AfshIcon.SpamShield,
                    label = stringResource(R.string.filter_senders),
                    description = pluralStringResource(R.plurals.blocked_senders, total, total),
                    onClick = { onEvent(BlockRulesEvent.OpenNumbers) },
                )
            }
            item(key = "phrases-card") {
                val phrases = state.phrases.size
                DestinationCard(
                    icon = AfshIcon.SpamShield,
                    label = stringResource(R.string.blocked_phrases),
                    description = pluralStringResource(R.plurals.blocked_phrase_count, phrases, phrases),
                    onClick = { scope.launch { listState.animateScrollToItem(PHRASES_HEADING_INDEX) } },
                )
            }
            item(key = "filter") {
                FilterToggle(state.filterEnabled) { onEvent(BlockRulesEvent.SetFilter(it)) }
            }
            item(key = "unknown") {
                UnknownNumbersToggle(state.unknownNumbers) { onEvent(BlockRulesEvent.SetUnknownNumbers(it)) }
            }
            item(key = "what") {
                DestinationCard(
                    icon = AfshIcon.SpamShield,
                    label = stringResource(R.string.what_filter_looks_for),
                    description = stringResource(R.string.what_filter_looks_for_card),
                    onClick = { onEvent(BlockRulesEvent.OpenWhatFilterLooksFor) },
                )
            }
            item(key = "patterns") {
                DestinationCard(
                    icon = AfshIcon.SpamShield,
                    label = stringResource(R.string.spam_patterns),
                    description = stringResource(R.string.spam_patterns_card),
                    onClick = { onEvent(BlockRulesEvent.OpenPatterns) },
                )
            }
            item(key = "phrases-heading") { SectionHeading(stringResource(R.string.blocked_phrases)) }
            items(state.phrases, key = { "p:$it" }) { phrase ->
                PhraseRow(
                    phrase,
                    onEdit = { onEvent(BlockRulesEvent.EditPhrase(phrase)) },
                    onRemove = { onEvent(BlockRulesEvent.RemovePhrase(phrase)) },
                )
            }
            item(key = "add") {
                AddRule(
                    onPhrase = { onEvent(BlockRulesEvent.AddPhrase) },
                    onNumber = { onEvent(BlockRulesEvent.AddNumber) },
                )
            }
        }
    }
}

/** Rule toggle: a tile with the setting, plain-language support, and its switch. */
@Composable
private fun FilterToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingItem(
        label = stringResource(R.string.filter_obvious_spam),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        value = stringResource(
            if (enabled) R.string.filter_on_text else R.string.filter_obvious_spam_off,
        ),
        bleed = false,
    ) {
        Switch(checked = enabled, onCheckedChange = null, colors = afshSwitchColors())
    }
}

@Composable
private fun UnknownNumbersToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingItem(
        label = stringResource(R.string.unknown_numbers),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        value = stringResource(R.string.unknown_numbers_text),
        bleed = false,
    ) {
        Switch(checked = enabled, onCheckedChange = null, colors = afshSwitchColors())
    }
}

@Composable
private fun PhraseRow(phrase: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PhraseRowHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.edit_phrase), onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            remember(phrase) { AnnotatedString(phrase).withContentFonts() },
            style = MaterialTheme.typography.bodyMedium.forContent(phrase),
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                AfshIcon.Close.painter(),
                contentDescription = stringResource(R.string.remove_phrase, phrase),
                tint = colors.error,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun AddRule(onPhrase: () -> Unit, onNumber: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier
                .heightIn(min = PhraseRowHeight)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { open = true }
                .padding(end = Afsh.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            Icon(
                AfshIcon.Add.painter(),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.add_phrase_or_number),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.primary,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_a_phrase)) },
                onClick = {
                    open = false
                    onPhrase()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_a_number)) },
                onClick = {
                    open = false
                    onNumber()
                },
            )
        }
    }
}

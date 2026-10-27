package org.fossify.messages.ui.spam

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fossify.messages.R
import org.fossify.messages.ui.components.DestinationCard
import org.fossify.messages.ui.components.PageAppBarAction
import org.fossify.messages.ui.components.PageContentPadding
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.SettingItem
import org.fossify.messages.ui.components.openLineSwitchColors
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.withContentFonts

private val PhraseRowHeight = 50.dp
private const val PHRASES_HEADING_INDEX = 3

@Immutable
data class BlockRulesUiState(
    val blockedNumbers: Int = 0,
    val phrases: List<String> = emptyList(),
    val filterEnabled: Boolean = true,
)

/** Everything 21 · Block rules can ask its activity to do. */
sealed interface BlockRulesEvent {
    data object Back : BlockRulesEvent
    data object OpenNumbers : BlockRulesEvent
    data object MoveRules : BlockRulesEvent
    data class SetFilter(val enabled: Boolean) : BlockRulesEvent
    data object AddPhrase : BlockRulesEvent
    data object AddNumber : BlockRulesEvent
    data class EditPhrase(val phrase: String) : BlockRulesEvent
    data class RemovePhrase(val phrase: String) : BlockRulesEvent
}

/**
 * 21 · Spam / rules: who and what is filtered, in one short list. The phrases card jumps to the
 * phrase list on the same page; the add row offers a phrase or a number.
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
        title = stringResource(R.string.block_rules),
        modifier = modifier,
        subtitle = stringResource(R.string.block_rules_subtitle),
        onBack = { onEvent(BlockRulesEvent.Back) },
        navigation = navigation,
        actions = {
            PageAppBarAction(R.drawable.ic_lucide_share_2, stringResource(R.string.move_block_rules), onClick = {
                onEvent(BlockRulesEvent.MoveRules)
            })
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "numbers") {
                val numbers = state.blockedNumbers
                DestinationCard(
                    icon = R.drawable.ic_lucide_shield_ban,
                    label = stringResource(org.fossify.commons.R.string.blocked_numbers),
                    description = pluralStringResource(R.plurals.blocked_senders, numbers, numbers),
                    onClick = { onEvent(BlockRulesEvent.OpenNumbers) },
                )
            }
            item(key = "phrases-card") {
                val phrases = state.phrases.size
                DestinationCard(
                    icon = R.drawable.ic_lucide_shield_ban,
                    label = stringResource(R.string.blocked_phrases),
                    description = pluralStringResource(R.plurals.blocked_phrase_count, phrases, phrases),
                    onClick = { scope.launch { listState.animateScrollToItem(PHRASES_HEADING_INDEX) } },
                )
            }
            item(key = "filter") { FilterToggle(state.filterEnabled) { onEvent(BlockRulesEvent.SetFilter(it)) } }
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

/** Rule toggle: a tile with the setting and its switch (R5-37). */
@Composable
private fun FilterToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingItem(
        label = stringResource(R.string.filter_obvious_spam),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 8.dp),
        value = stringResource(if (enabled) R.string.filter_obvious_spam_on else R.string.filter_obvious_spam_off),
    ) {
        Switch(checked = enabled, onCheckedChange = null, colors = openLineSwitchColors())
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
                painterResource(R.drawable.ic_lucide_x),
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
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_lucide_plus),
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

package com.afsh.messages.ui.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.InfoPanel
import com.afsh.messages.ui.components.NavigationSetting
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SwitchSetting

@Immutable
data class BlockRulesUiState(
    val alwaysSpamCount: Int = 0,
    val neverSpamCount: Int = 0,
    val androidBlockedCount: Int = 0,
    val phrases: List<String> = emptyList(),
    val filterEnabled: Boolean = true,
    val unknownNumbers: Boolean = false,
)

/** Everything the spam and blocking menu can ask its activity to do. */
sealed interface BlockRulesEvent {
    data object Back : BlockRulesEvent
    data object OpenNumbers : BlockRulesEvent
    data object OpenPhrases : BlockRulesEvent
    data object MoveRules : BlockRulesEvent
    data object OpenPatterns : BlockRulesEvent
    data class SetFilter(val enabled: Boolean) : BlockRulesEvent
    data class SetUnknownNumbers(val enabled: Boolean) : BlockRulesEvent
    data class AddPhrase(val phrase: String) : BlockRulesEvent
    data class EditPhrase(val phrase: String) : BlockRulesEvent
    data class RemovePhrase(val phrase: String) : BlockRulesEvent
}

/**
 * Spam and blocking: a one-sentence purpose, two switches, then a row for each list.
 * Unknown numbers keeps its own short line. The marketing filter does not grow a paragraph here.
 */
@Composable
fun BlockRulesScreen(
    state: BlockRulesUiState,
    navigation: PageNavigation?,
    onEvent: (BlockRulesEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** True inside the Settings stack, where the outer scaffold owns navigation and insets. */
    embedded: Boolean = false,
) {
    PageScaffold(
        title = stringResource(R.string.spam_rules),
        modifier = modifier,
        onBack = { onEvent(BlockRulesEvent.Back) },
        navigation = navigation.takeUnless { embedded },
        contentWindowInsets = if (embedded) WindowInsets(0) else null,
        actions = {
            PageAppBarAction(AfshIcon.Share, stringResource(R.string.move_filter_rules), onClick = {
                onEvent(BlockRulesEvent.MoveRules)
            })
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
        ) {
            InfoPanel(
                stringResource(R.string.spam_feature_summary),
                Modifier.padding(bottom = Afsh.spacing.sm),
            )
            SwitchSetting(
                stringResource(R.string.filter_obvious_spam),
                checked = state.filterEnabled,
                onCheckedChange = { onEvent(BlockRulesEvent.SetFilter(it)) },
            )
            SwitchSetting(
                stringResource(R.string.unknown_numbers),
                checked = state.unknownNumbers,
                onCheckedChange = { onEvent(BlockRulesEvent.SetUnknownNumbers(it)) },
                value = stringResource(R.string.unknown_numbers_summary),
            )
            NavigationSetting(
                stringResource(R.string.filter_senders),
                onClick = { onEvent(BlockRulesEvent.OpenNumbers) },
            )
            NavigationSetting(
                stringResource(R.string.blocked_phrases),
                onClick = { onEvent(BlockRulesEvent.OpenPhrases) },
            )
            NavigationSetting(
                stringResource(R.string.spam_patterns),
                onClick = { onEvent(BlockRulesEvent.OpenPatterns) },
                value = stringResource(R.string.spam_patterns_card),
            )
        }
    }
}

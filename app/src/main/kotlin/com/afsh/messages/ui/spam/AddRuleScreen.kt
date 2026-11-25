package com.afsh.messages.ui.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshButton
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.AttentionNotice
import com.afsh.messages.ui.components.ContentDirection
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.TextFieldSlab
import com.afsh.messages.ui.isolate

/** What 50 adds: a phrase, or a number (the same page pattern). */
enum class RuleKind { PHRASE, NUMBER }

/**
 * [example] is a message on the device that the rule would match, or null when none does.
 * [error] explains why the value cannot be added.
 */
@Immutable
data class AddRuleUiState(
    val kind: RuleKind,
    val value: String = "",
    val editing: Boolean = false,
    val example: String? = null,
    val error: Int? = null,
)

/** 50 · Spam / add phrase, and its number counterpart. */
@Composable
fun AddRuleScreen(
    state: AddRuleUiState,
    navigation: PageNavigation,
    onBack: () -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phrase = state.kind == RuleKind.PHRASE
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PageScaffold(
        title = stringResource(if (phrase) R.string.block_a_phrase else R.string.block_a_number),
        modifier = modifier,
        subtitle = stringResource(if (phrase) R.string.block_a_phrase_subtitle else R.string.block_a_number_subtitle),
        onBack = onBack,
        navigation = navigation,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            val fieldLabel = stringResource(if (phrase) R.string.phrase else R.string.phone_number_label)
            Text(
                fieldLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextFieldSlab(
                value = state.value,
                onValueChange = onValueChange,
                modifier = Modifier.focusRequester(focus),
                accessibilityLabel = fieldLabel,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (phrase) KeyboardType.Text else KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                isError = state.error != null,
            )
            state.error?.let { error ->
                AttentionNotice(
                    title = stringResource(error),
                    detail = stringResource(if (phrase) R.string.phrase_error_detail else R.string.number_error_detail),
                    icon = AfshIcon.Warning,
                )
            }
            if (phrase && state.value.isNotBlank()) {
                ExampleMatch(state.example)
            }
            Text(
                stringResource(if (phrase) R.string.phrase_guidance else R.string.number_guidance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AfshButton(
                stringResource(
                    when {
                        state.editing -> R.string.save_phrase
                        phrase -> R.string.add_phrase
                        else -> com.afsh.commons.R.string.block_number
                    },
                ),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.value.isNotBlank(),
            )
        }
    }
}

/** Example result: a message on the device this phrase matches, so the rule's reach is visible. */
@Composable
private fun ExampleMatch(example: String?) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(Afsh.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
        ) {
            Text(
                stringResource(R.string.example_match),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSecondaryContainer,
            )
            val text = example?.let { stringResource(R.string.quoted, isolate(it)) } ?: stringResource(R.string.no_current_match)
            ContentText(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Afsh.colors.onSecondaryContainerVariant,
                direction = ContentDirection.Inherit,
            )
        }
    }
}

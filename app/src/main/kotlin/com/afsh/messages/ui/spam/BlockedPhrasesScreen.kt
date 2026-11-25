package com.afsh.messages.ui.spam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SendButton
import com.afsh.messages.ui.components.TextFieldSlab
import com.afsh.messages.ui.components.fieldMinHeight

private val PhraseRowHeight = 50.dp

/** Blocked phrases, on their own page. Adding a number stays with the sender lists. */
@Composable
fun BlockedPhrasesScreen(
    phrases: List<String>,
    navigation: PageNavigation?,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Int? = null,
    onDraftChange: () -> Unit = {},
    /** True inside the Settings stack, where the outer scaffold owns navigation and insets. */
    embedded: Boolean = false,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var seen by rememberSaveable { mutableIntStateOf(phrases.size) }
    LaunchedEffect(phrases.size) {
        if (phrases.size > seen) draft = ""
        seen = phrases.size
    }
    val submit = {
        val phrase = draft.trim()
        if (phrase.isNotEmpty()) onAdd(phrase)
    }
    PageScaffold(
        title = stringResource(R.string.blocked_phrases),
        modifier = modifier,
        onBack = onBack,
        navigation = navigation.takeUnless { embedded },
        contentWindowInsets = if (embedded) WindowInsets(0) else null,
    ) {
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            item(key = "add") {
                PhraseEntry(draft, error, onDraftChange = {
                    draft = it
                    onDraftChange()
                }, onSubmit = submit)
            }
            if (phrases.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = AfshIcon.SpamShield,
                        title = stringResource(R.string.no_blocked_phrases),
                        text = stringResource(R.string.no_blocked_phrases_text),
                    )
                }
            } else {
                items(phrases, key = { it }) { phrase ->
                    PhraseRow(phrase, onEdit = { onEdit(phrase) }, onRemove = { onRemove(phrase) })
                }
            }
        }
    }
}

@Composable
private fun PhraseEntry(
    draft: String,
    error: Int?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            TextFieldSlab(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.phrase),
                accessibilityLabel = stringResource(R.string.phrase),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                textStyle = MaterialTheme.typography.bodyLarge,
                isError = error != null,
            )
            SendButton(
                AfshIcon.Add,
                stringResource(R.string.add_phrase),
                onClick = onSubmit,
                modifier = Modifier.size(fieldMinHeight()),
                enabled = draft.isNotBlank(),
            )
        }
        if (error != null) {
            Text(
                stringResource(error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
        ContentText(
            phrase,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                AfshIcon.Close.painter(),
                contentDescription = stringResource(R.string.remove_phrase, phrase),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

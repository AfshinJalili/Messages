package com.afsh.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.ui.components.AfshDialog
import com.afsh.messages.ui.components.DialogConfirm
import com.afsh.messages.ui.components.ScreenStack
import com.afsh.messages.ui.components.afshFieldColors
import com.afsh.messages.ui.components.fieldMinHeight
import com.afsh.messages.ui.spam.BlockRulesEvent
import com.afsh.messages.ui.spam.BlockRulesScreen
import com.afsh.messages.ui.spam.BlockRulesUiState
import com.afsh.messages.ui.spam.BlockedPhrasesScreen

private const val PHRASES_OPEN = "phrases_open"

private enum class BlockRulesPage { Menu, Phrases }

/** Spam and blocking: the menu, and the blocked-phrases page pushed on top of it. */
class BlockRulesActivity : ComposePageActivity() {
    private var state by mutableStateOf(BlockRulesUiState())
    private var phrasesOpen by mutableStateOf(false)
    private var phraseError by mutableStateOf<Int?>(null)
    private var editingPhrase by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        phrasesOpen = savedInstanceState?.getBoolean(PHRASES_OPEN) == true
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(PHRASES_OPEN, phrasesOpen)
    }

    override fun onBackPressedCompat(): Boolean {
        if (!phrasesOpen) return false
        phrasesOpen = false
        return true
    }

    @Composable
    override fun Page() {
        val page = if (phrasesOpen) BlockRulesPage.Phrases else BlockRulesPage.Menu
        ScreenStack(
            screen = page,
            depth = if (phrasesOpen) 2 else 1,
            previous = if (phrasesOpen) BlockRulesPage.Menu else null,
            onPop = { phrasesOpen = false },
        ) { current ->
            when (current) {
                BlockRulesPage.Phrases -> BlockedPhrasesScreen(
                    state.phrases,
                    settingsNavigation(),
                    onBack = { phrasesOpen = false },
                    onAdd = { onEvent(BlockRulesEvent.AddPhrase(it)) },
                    error = phraseError,
                    onDraftChange = { phraseError = null },
                    onEdit = { onEvent(BlockRulesEvent.EditPhrase(it)) },
                    onRemove = { onEvent(BlockRulesEvent.RemovePhrase(it)) },
                )
                BlockRulesPage.Menu -> BlockRulesScreen(state, settingsNavigation(), ::onEvent)
            }
        }
        editingPhrase?.let { original ->
            EditPhraseDialog(
                original = original,
                isDuplicate = { phrase ->
                    config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) && it != original }
                },
                onSave = { edited ->
                    saveEditedPhrase(original, edited)
                    editingPhrase = null
                },
                onDismiss = { editingPhrase = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        ensureBackgroundThread {
            val system = runCatching { getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
            val loaded = BlockRulesUiState(
                alwaysSpamCount = config.spamNumbers.size,
                neverSpamCount = config.allowedNumbers.size,
                androidBlockedCount = system.distinct().size,
                phrases = config.blockedKeywords.sortedWith(String.CASE_INSENSITIVE_ORDER),
                filterEnabled = config.ruleFilterEnabled,
                unknownNumbers = baseConfig.blockUnknownNumbers,
            )
            runOnUiThread { state = loaded }
        }
    }

    private fun onEvent(event: BlockRulesEvent) {
        when (event) {
            BlockRulesEvent.Back -> finish()
            BlockRulesEvent.OpenNumbers -> startActivity(Intent(this, BlockedNumbersActivity::class.java))
            BlockRulesEvent.OpenPhrases -> phrasesOpen = true
            BlockRulesEvent.MoveRules -> startActivity(Intent(this, MoveBlockRulesActivity::class.java))
            BlockRulesEvent.OpenPatterns -> startActivity(Intent(this, SpamPatternsActivity::class.java))
            is BlockRulesEvent.AddPhrase -> addPhrase(event.phrase)
            is BlockRulesEvent.EditPhrase -> editingPhrase = event.phrase
            is BlockRulesEvent.SetFilter -> {
                config.ruleFilterEnabled = event.enabled
                state = state.copy(filterEnabled = event.enabled)
            }
            is BlockRulesEvent.SetUnknownNumbers -> {
                baseConfig.blockUnknownNumbers = event.enabled
                state = state.copy(unknownNumbers = event.enabled)
            }
            is BlockRulesEvent.RemovePhrase -> {
                config.removeBlockedKeyword(event.phrase)
                state = state.copy(phrases = state.phrases - event.phrase)
            }
        }
    }

    private fun addPhrase(raw: String) {
        val phrase = raw.trim()
        if (phrase.isEmpty()) return
        if (config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) }) {
            phraseError = R.string.phrase_already_blocked
            return
        }
        config.addBlockedKeyword(phrase)
        phraseError = null
        state = state.copy(phrases = (state.phrases + phrase).sortedWith(String.CASE_INSENSITIVE_ORDER))
    }

    private fun saveEditedPhrase(original: String, raw: String) {
        val phrase = raw.trim()
        if (phrase.isEmpty()) return
        val taken = config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) && it != original }
        if (taken) return
        config.removeBlockedKeyword(original)
        config.addBlockedKeyword(phrase)
        state = state.copy(
            phrases = (state.phrases - original + phrase).sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }
}

/**
 * Edit a blocked phrase in place: normal text field, sentences capitalization, duplicate check
 * matching [AddBlockRuleActivity] save for phrases.
 */
@Composable
private fun EditPhraseDialog(
    original: String,
    isDuplicate: (String) -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(original) }
    val focus = remember { FocusRequester() }
    val trimmed = value.trim()
    val problem = when {
        trimmed.isEmpty() -> null
        isDuplicate(trimmed) -> stringResource(R.string.phrase_already_blocked)
        else -> null
    }
    val canSave = trimmed.isNotEmpty() && problem == null
    val save = { if (canSave) onSave(trimmed) }
    val title = stringResource(R.string.edit_phrase)
    AfshDialog(
        title = title,
        onDismiss = onDismiss,
        dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
        confirm = DialogConfirm(stringResource(R.string.save_phrase), { save() }, enabled = canSave),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = fieldMinHeight())
                .focusRequester(focus)
                .semantics { contentDescription = title },
            isError = problem != null,
            supportingText = { problem?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = afshFieldColors(),
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Always-spam senders plus those in Android's block list, counted once each. */
internal fun android.content.Context.blockedSenderCount(): Int {
    val system = runCatching { getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
    return (config.spamNumbers + system).distinct().size
}

package com.afsh.messages.ui.spam

import android.content.Intent
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
import com.afsh.messages.activities.BlockedNumbersActivity
import com.afsh.messages.activities.MoveBlockRulesActivity
import com.afsh.messages.activities.SimpleActivity
import com.afsh.messages.activities.SpamPatternsActivity
import com.afsh.messages.extensions.config
import com.afsh.messages.ui.ContentFontVisualTransformation
import com.afsh.messages.ui.components.AfshDialog
import com.afsh.messages.ui.components.DialogConfirm
import com.afsh.messages.ui.components.afshFieldColors
import com.afsh.messages.ui.components.fieldMinHeight
import com.afsh.messages.ui.forFieldContent

/**
 * State and actions of Filter Rules and its blocked-phrases page. Two hosts share it: the Settings
 * stack in MainActivity, and BlockRulesActivity (reached from the blocked-messages screen). The host
 * owns navigation between the two pages; [openPhrases] and [back] are how this reports it.
 */
class BlockRulesController(
    private val activity: SimpleActivity,
    private val openPhrases: () -> Unit,
    private val back: () -> Unit,
) {
    var state by mutableStateOf(BlockRulesUiState())
        private set
    var phraseError by mutableStateOf<Int?>(null)
        private set
    private var editingPhrase by mutableStateOf<String?>(null)

    fun reload() {
        ensureBackgroundThread {
            val system = runCatching { activity.getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
            val loaded = BlockRulesUiState(
                alwaysSpamCount = activity.config.spamNumbers.size,
                neverSpamCount = activity.config.allowedNumbers.size,
                androidBlockedCount = system.distinct().size,
                phrases = activity.config.blockedKeywords.sortedWith(String.CASE_INSENSITIVE_ORDER),
                filterEnabled = activity.config.ruleFilterEnabled,
                unknownNumbers = activity.baseConfig.blockUnknownNumbers,
            )
            activity.runOnUiThread { state = loaded }
        }
    }

    fun clearPhraseError() {
        phraseError = null
    }

    fun onEvent(event: BlockRulesEvent) {
        when (event) {
            BlockRulesEvent.Back -> back()
            BlockRulesEvent.OpenNumbers -> activity.startActivity(Intent(activity, BlockedNumbersActivity::class.java))
            BlockRulesEvent.OpenPhrases -> openPhrases()
            BlockRulesEvent.MoveRules -> activity.startActivity(Intent(activity, MoveBlockRulesActivity::class.java))
            BlockRulesEvent.OpenPatterns -> activity.startActivity(Intent(activity, SpamPatternsActivity::class.java))
            is BlockRulesEvent.AddPhrase -> addPhrase(event.phrase)
            is BlockRulesEvent.EditPhrase -> editingPhrase = event.phrase
            is BlockRulesEvent.SetFilter -> {
                activity.config.ruleFilterEnabled = event.enabled
                state = state.copy(filterEnabled = event.enabled)
            }
            is BlockRulesEvent.SetUnknownNumbers -> {
                activity.baseConfig.blockUnknownNumbers = event.enabled
                state = state.copy(unknownNumbers = event.enabled)
            }
            is BlockRulesEvent.RemovePhrase -> {
                activity.config.removeBlockedKeyword(event.phrase)
                state = state.copy(phrases = state.phrases - event.phrase)
            }
        }
    }

    /** The phrase editor, shown over whichever page raised it. */
    @Composable
    fun Dialogs() {
        editingPhrase?.let { original ->
            EditPhraseDialog(
                original = original,
                isDuplicate = { phrase ->
                    activity.config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) && it != original }
                },
                onSave = { edited ->
                    saveEditedPhrase(original, edited)
                    editingPhrase = null
                },
                onDismiss = { editingPhrase = null },
            )
        }
    }

    private fun addPhrase(raw: String) {
        val phrase = raw.trim()
        if (phrase.isEmpty()) return
        if (activity.config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) }) {
            phraseError = R.string.phrase_already_blocked
            return
        }
        activity.config.addBlockedKeyword(phrase)
        phraseError = null
        state = state.copy(phrases = (state.phrases + phrase).sortedWith(String.CASE_INSENSITIVE_ORDER))
    }

    private fun saveEditedPhrase(original: String, raw: String) {
        val phrase = raw.trim()
        if (phrase.isEmpty()) return
        val taken = activity.config.blockedKeywords.any { it.equals(phrase, ignoreCase = true) && it != original }
        if (taken) return
        activity.config.removeBlockedKeyword(original)
        activity.config.addBlockedKeyword(phrase)
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
            textStyle = MaterialTheme.typography.bodyLarge.forFieldContent(value),
            visualTransformation = ContentFontVisualTransformation,
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


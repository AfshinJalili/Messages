package org.fossify.messages.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.ui.spam.AddRuleScreen
import org.fossify.messages.ui.spam.AddRuleUiState
import org.fossify.messages.ui.spam.RuleKind

/**
 * 50 · Block a phrase, and Block a number on the same pattern. A phrase shows a message on the
 * device it would match, so its reach is visible before saving.
 */
class AddBlockRuleActivity : ComposePageActivity() {
    private lateinit var kind: RuleKind
    private var original: String? = null
    private var state by mutableStateOf(AddRuleUiState(RuleKind.PHRASE))
    private var exampleJob: Job? = null

    @Composable
    override fun Page() {
        AddRuleScreen(state, settingsNavigation(), onBack = ::finish, onValueChange = ::onValueChange, onSave = ::save)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        kind = RuleKind.entries.getOrNull(intent.getIntExtra(KIND, 0)) ?: RuleKind.PHRASE
        original = intent.getStringExtra(ORIGINAL)
        val value = savedInstanceState?.getString(VALUE) ?: original.orEmpty()
        state = AddRuleUiState(kind, value = value, editing = original != null)
        super.onCreate(savedInstanceState)
        findExample(value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(VALUE, state.value)
        super.onSaveInstanceState(outState)
    }

    private fun onValueChange(value: String) {
        state = state.copy(value = value, error = null)
        findExample(value)
    }

    private fun findExample(value: String) {
        exampleJob?.cancel()
        if (kind != RuleKind.PHRASE) return
        val phrase = value.trim()
        if (phrase.isEmpty()) {
            state = state.copy(example = null)
            return
        }
        exampleJob = lifecycleScope.launch {
            delay(EXAMPLE_DEBOUNCE_MS)
            val example = withContext(Dispatchers.IO) {
                runCatching { messagesDB.getMessagesWithText("%$phrase%").firstOrNull()?.body }.getOrNull()
            }
            state = state.copy(example = example)
        }
    }

    private fun save() {
        val value = state.value.trim()
        if (value.isEmpty()) return
        when (kind) {
            RuleKind.PHRASE -> {
                val taken = config.blockedKeywords.any { it.equals(value, ignoreCase = true) && it != original }
                if (taken) {
                    state = state.copy(error = R.string.phrase_already_blocked)
                    return
                }
                original?.let { config.removeBlockedKeyword(it) }
                config.addBlockedKeyword(value)
            }

            RuleKind.NUMBER -> {
                if (value.none { it.isLetterOrDigit() }) {
                    state = state.copy(error = R.string.number_invalid)
                    return
                }
                config.addSpamNumber(value)
            }
        }
        finish()
    }

    companion object {
        private const val KIND = "kind"
        private const val ORIGINAL = "original"
        private const val VALUE = "value"
        private const val EXAMPLE_DEBOUNCE_MS = 250L

        /** Opens the add page for [kind]; [original] edits that phrase instead of adding one. */
        fun intent(context: Context, kind: RuleKind, original: String? = null): Intent =
            Intent(context, AddBlockRuleActivity::class.java)
                .putExtra(KIND, kind.ordinal)
                .putExtra(ORIGINAL, original)
    }
}

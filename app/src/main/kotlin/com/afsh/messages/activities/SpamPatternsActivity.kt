package com.afsh.messages.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.SpamPatternGroup
import com.afsh.messages.helpers.SpamPatterns
import com.afsh.messages.helpers.SpamRecheck
import com.afsh.messages.ui.spam.SpamPatternsEvent
import com.afsh.messages.ui.spam.SpamPatternsScreen
import com.afsh.messages.ui.spam.SpamPatternsUiState

/** Spam patterns: view and edit what the local rules look for, and recheck Spam with them. */
class SpamPatternsActivity : ComposePageActivity() {
    private var state by mutableStateOf(SpamPatternsUiState())

    @Composable
    override fun Page() {
        SpamPatternsScreen(state, settingsNavigation(), snackbarHost, ::onEvent)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        state = state.copy(patterns = config.spamPatterns, filterEnabled = config.ruleFilterEnabled)
    }

    private fun onEvent(event: SpamPatternsEvent) {
        when (event) {
            SpamPatternsEvent.Back -> finish()
            is SpamPatternsEvent.Save -> update(event.group) { list ->
                if (event.index == null) list + event.pattern else list.toMutableList().apply { set(event.index, event.pattern) }
            }

            is SpamPatternsEvent.Remove -> update(event.group) { list -> list.filterIndexed { index, _ -> index != event.index } }
            SpamPatternsEvent.Reset -> save(SpamPatterns.DEFAULT)
            SpamPatternsEvent.Recheck -> recheck()
        }
    }

    private fun update(group: SpamPatternGroup, change: (List<String>) -> List<String>) {
        save(state.patterns.with(group, change(state.patterns[group])))
    }

    private fun save(patterns: SpamPatterns) {
        config.spamPatterns = patterns
        state = state.copy(patterns = patterns)
    }

    private fun recheck() {
        state = state.copy(rechecking = true)
        lifecycleScope.launch {
            val moved = withContext(Dispatchers.IO) { runCatching { SpamRecheck.run(applicationContext) }.getOrDefault(0) }
            state = state.copy(rechecking = false)
            snackbarHost.showSnackbar(resources.getQuantityString(R.plurals.spam_patterns_rechecked, moved, moved))
        }
    }
}

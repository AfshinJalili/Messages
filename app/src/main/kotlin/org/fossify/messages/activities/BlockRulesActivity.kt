package org.fossify.messages.activities

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.ui.spam.BlockRulesEvent
import org.fossify.messages.ui.spam.BlockRulesScreen
import org.fossify.messages.ui.spam.BlockRulesUiState
import org.fossify.messages.ui.spam.RuleKind

/** 21 · Block rules: the counts, the local filter switch and the blocked phrases. */
class BlockRulesActivity : ComposePageActivity() {
    private var state by mutableStateOf(BlockRulesUiState())

    @Composable
    override fun Page() {
        BlockRulesScreen(state, settingsNavigation(), ::onEvent)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        ensureBackgroundThread {
            val numbers = blockedSenderCount()
            val loaded = BlockRulesUiState(
                blockedNumbers = numbers,
                phrases = config.blockedKeywords.sortedWith(String.CASE_INSENSITIVE_ORDER),
                filterEnabled = config.ruleFilterEnabled,
            )
            runOnUiThread { state = loaded }
        }
    }

    private fun onEvent(event: BlockRulesEvent) {
        when (event) {
            BlockRulesEvent.Back -> finish()
            BlockRulesEvent.OpenNumbers -> startActivity(Intent(this, BlockedNumbersActivity::class.java))
            BlockRulesEvent.MoveRules -> startActivity(Intent(this, MoveBlockRulesActivity::class.java))
            BlockRulesEvent.AddPhrase -> startActivity(AddBlockRuleActivity.intent(this, RuleKind.PHRASE))
            BlockRulesEvent.AddNumber -> startActivity(AddBlockRuleActivity.intent(this, RuleKind.NUMBER))
            is BlockRulesEvent.EditPhrase ->
                startActivity(AddBlockRuleActivity.intent(this, RuleKind.PHRASE, original = event.phrase))
            is BlockRulesEvent.SetFilter -> {
                config.ruleFilterEnabled = event.enabled
                state = state.copy(filterEnabled = event.enabled)
            }

            is BlockRulesEvent.RemovePhrase -> {
                config.removeBlockedKeyword(event.phrase)
                state = state.copy(phrases = state.phrases - event.phrase)
            }
        }
    }
}

/** Senders blocked in the app plus those in Android's block list, counted once each. */
internal fun android.content.Context.blockedSenderCount(): Int {
    val system = runCatching { getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
    return (config.spamNumbers + system).distinct().size
}

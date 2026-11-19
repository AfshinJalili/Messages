package com.afsh.messages.activities

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.ui.spam.BlockRulesEvent
import com.afsh.messages.ui.spam.BlockRulesScreen
import com.afsh.messages.ui.spam.BlockRulesUiState
import com.afsh.messages.ui.spam.RuleKind

/** 21 · Filter rules: the sender lists, the local filter switch and the blocked phrases. */
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
            BlockRulesEvent.MoveRules -> startActivity(Intent(this, MoveBlockRulesActivity::class.java))
            BlockRulesEvent.OpenPatterns -> startActivity(Intent(this, SpamPatternsActivity::class.java))
            BlockRulesEvent.OpenWhatFilterLooksFor ->
                startActivity(Intent(this, WhatFilterLooksForActivity::class.java))
            BlockRulesEvent.AddPhrase -> startActivity(AddBlockRuleActivity.intent(this, RuleKind.PHRASE))
            BlockRulesEvent.AddNumber -> startActivity(AddBlockRuleActivity.intent(this, RuleKind.NUMBER))
            is BlockRulesEvent.EditPhrase ->
                startActivity(AddBlockRuleActivity.intent(this, RuleKind.PHRASE, original = event.phrase))
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
}

/** Always-spam senders plus those in Android's block list, counted once each. */
internal fun android.content.Context.blockedSenderCount(): Int {
    val system = runCatching { getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
    return (config.spamNumbers + system).distinct().size
}

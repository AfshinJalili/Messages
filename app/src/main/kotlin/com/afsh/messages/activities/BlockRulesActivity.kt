package com.afsh.messages.activities

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afsh.messages.extensions.config
import com.afsh.messages.ui.components.ScreenStack
import com.afsh.messages.ui.spam.BlockRulesController
import com.afsh.messages.ui.spam.BlockRulesEvent
import com.afsh.messages.ui.spam.BlockRulesScreen
import com.afsh.messages.ui.spam.BlockedPhrasesScreen
import com.afsh.commons.extensions.getBlockedNumbers

private const val PHRASES_OPEN = "phrases_open"

private enum class BlockRulesPage { Menu, Phrases }

/**
 * Filter Rules as a standalone page, for entry points outside Settings (blocked messages). Inside
 * Settings the same screens run in the Settings stack through [BlockRulesController].
 */
class BlockRulesActivity : ComposePageActivity() {
    private var phrasesOpen by mutableStateOf(false)
    private val rules = BlockRulesController(
        this,
        openPhrases = { phrasesOpen = true },
        back = ::finish,
    )

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
                    rules.state.phrases,
                    settingsNavigation(),
                    onBack = { phrasesOpen = false },
                    onAdd = { rules.onEvent(BlockRulesEvent.AddPhrase(it)) },
                    error = rules.phraseError,
                    onDraftChange = rules::clearPhraseError,
                    onEdit = { rules.onEvent(BlockRulesEvent.EditPhrase(it)) },
                    onRemove = { rules.onEvent(BlockRulesEvent.RemovePhrase(it)) },
                )
                BlockRulesPage.Menu -> BlockRulesScreen(rules.state, settingsNavigation(), rules::onEvent)
            }
        }
        rules.Dialogs()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() = rules.reload()
}

/** Always-spam senders plus those in Android's block list, counted once each. */
internal fun android.content.Context.blockedSenderCount(): Int {
    val system = runCatching { getBlockedNumbers().map { it.number } }.getOrDefault(emptyList())
    return (config.spamNumbers + system).distinct().size
}

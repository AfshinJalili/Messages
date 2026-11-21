package com.afsh.messages.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afsh.commons.extensions.deleteBlockedNumber
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getNameAndPhotoFromPhoneNumber
import com.afsh.messages.ui.spam.BlockedNumbersScreen
import com.afsh.messages.ui.spam.BlockedNumbersUiState
import com.afsh.messages.ui.spam.BlockedSender
import com.afsh.messages.ui.spam.FilterSenderKind
import com.afsh.messages.ui.spam.RuleKind

/**
 * 38 · Filter senders: always-spam, never-spam and Android's block list, in three sections.
 */
class BlockedNumbersActivity : ComposePageActivity() {
    private var state by mutableStateOf(BlockedNumbersUiState())

    @Composable
    override fun Page() {
        BlockedNumbersScreen(
            state,
            settingsNavigation(),
            onBack = ::finish,
            onRemove = ::remove,
            onBlockNumber = { startActivity(AddBlockRuleActivity.intent(this, RuleKind.NUMBER)) },
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        ensureBackgroundThread {
            val system = runCatching { getBlockedNumbers() }.getOrDefault(arrayListOf()).map {
                BlockedSender(
                    it.number,
                    it.contactName?.takeUnless(String::isBlank),
                    FilterSenderKind.AndroidBlocked,
                )
            }
            val always = config.spamNumbers.map { number ->
                val name = getNameAndPhotoFromPhoneNumber(number).name.takeUnless { it.isBlank() || it == number }
                BlockedSender(number, name, FilterSenderKind.AlwaysSpam)
            }
            val never = config.allowedNumbers.map { number ->
                val name = getNameAndPhotoFromPhoneNumber(number).name.takeUnless { it.isBlank() || it == number }
                BlockedSender(number, name, FilterSenderKind.NeverSpam)
            }
            val senders = (always + never + system)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.number })
            runOnUiThread { state = BlockedNumbersUiState(loading = false, senders = senders) }
        }
    }

    private fun remove(sender: BlockedSender) {
        state = state.copy(senders = state.senders - sender)
        ensureBackgroundThread {
            when (sender.kind) {
                FilterSenderKind.AndroidBlocked -> deleteBlockedNumber(sender.number)
                FilterSenderKind.AlwaysSpam -> config.removeSpamNumber(sender.number)
                FilterSenderKind.NeverSpam -> config.removeAllowedNumber(sender.number)
            }
            runOnUiThread { reload() }
        }
    }
}

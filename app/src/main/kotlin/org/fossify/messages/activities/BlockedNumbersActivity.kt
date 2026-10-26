package org.fossify.messages.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.extensions.deleteBlockedNumber
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.ui.spam.BlockedNumbersScreen
import org.fossify.messages.ui.spam.BlockedNumbersUiState
import org.fossify.messages.ui.spam.BlockedSender
import org.fossify.messages.ui.spam.RuleKind

/**
 * 38 · Blocked numbers: the app's own block list and Android's, in one list. Unblocking removes
 * the sender from whichever list holds it.
 */
class BlockedNumbersActivity : ComposePageActivity() {
    private var state by mutableStateOf(BlockedNumbersUiState())

    @Composable
    override fun Page() {
        BlockedNumbersScreen(
            state,
            settingsNavigation(),
            onBack = ::finish,
            onUnblock = ::unblock,
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
                BlockedSender(it.number, it.contactName?.takeUnless(String::isBlank), inSystemList = true)
            }
            val app = config.spamNumbers.map { number ->
                val name = getNameAndPhotoFromPhoneNumber(number).name.takeUnless { it.isBlank() || it == number }
                BlockedSender(number, name, inSystemList = false)
            }
            val senders = (app + system)
                .distinctBy { it.number }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.number })
            runOnUiThread { state = BlockedNumbersUiState(loading = false, senders = senders) }
        }
    }

    private fun unblock(sender: BlockedSender) {
        state = state.copy(senders = state.senders - sender)
        ensureBackgroundThread {
            if (sender.inSystemList) deleteBlockedNumber(sender.number) else config.removeSpamNumber(sender.number)
            runOnUiThread { reload() }
        }
    }
}

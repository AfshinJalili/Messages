package org.fossify.messages.activities

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.BlockRules
import org.fossify.messages.helpers.BlockRulesFile
import org.fossify.messages.ui.spam.MoveRulesScreen

/**
 * 51 · Move block rules. Exports the app's blocked numbers and phrases to a file the user picks,
 * and merges a picked file into them. Android's own block list stays with the system.
 */
class MoveBlockRulesActivity : ComposePageActivity() {
    private var numbers by mutableIntStateOf(0)
    private var phrases by mutableIntStateOf(0)

    private val exportTo = registerForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let(::export)
    }
    private val importFrom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::import)
    }

    @Composable
    override fun Page() {
        MoveRulesScreen(
            numbers = numbers,
            phrases = phrases,
            navigation = settingsNavigation(),
            snackbarHostState = snackbarHost,
            onBack = ::finish,
            onExport = { exportTo.launch(getString(R.string.block_rules_file_name)) },
            onImport = { importFrom.launch(arrayOf(JSON, "text/plain")) },
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun reload() {
        numbers = config.spamNumbers.size
        phrases = config.blockedKeywords.size
    }

    private fun export(uri: Uri) = lifecycleScope.launch {
        val rules = BlockRules(config.spamNumbers, config.blockedKeywords)
        val written = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { BlockRulesFile.write(rules, it) } != null
            }.getOrDefault(false)
        }
        report(getString(if (written) R.string.block_rules_exported else org.fossify.commons.R.string.exporting_failed))
    }

    private fun import(uri: Uri) = lifecycleScope.launch {
        val rules = withContext(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)?.use(BlockRulesFile::read) }.getOrNull()
        }
        if (rules == null || rules.numbers.isEmpty() && rules.phrases.isEmpty()) {
            report(getString(org.fossify.commons.R.string.importing_failed))
            return@launch
        }
        rules.numbers.forEach(config::addSpamNumber)
        rules.phrases.forEach(config::addBlockedKeyword)
        reload()
        report(
            getString(
                R.string.rules_summary,
                resources.getQuantityString(R.plurals.blocked_number_count, rules.numbers.size, rules.numbers.size),
                resources.getQuantityString(R.plurals.blocked_phrase_count, rules.phrases.size, rules.phrases.size),
            ).let { getString(R.string.block_rules_imported, it) },
        )
    }

    private suspend fun report(message: String) {
        snackbarHost.showSnackbar(message)
    }

    private companion object {
        const val JSON = "application/json"
    }
}

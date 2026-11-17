package com.afsh.messages.activities

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
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.BlockRules
import com.afsh.messages.helpers.BlockRulesFile
import com.afsh.messages.ui.spam.MoveRulesScreen

/**
 * 51 · Move filter rules. Exports always-spam numbers, phrases and the never-spam allowlist to a
 * file the user picks, and merges a picked file into them. Android's own block list stays with
 * the system.
 */
class MoveBlockRulesActivity : ComposePageActivity() {
    private var numbers by mutableIntStateOf(0)
    private var phrases by mutableIntStateOf(0)
    private var allowed by mutableIntStateOf(0)

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
            allowed = allowed,
            navigation = settingsNavigation(),
            snackbarHostState = snackbarHost,
            onBack = ::finish,
            onExport = { exportTo.launch(getString(R.string.filter_rules_file_name)) },
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
        allowed = config.allowedNumbers.size
    }

    private fun export(uri: Uri) = lifecycleScope.launch {
        val rules = BlockRules(config.spamNumbers, config.blockedKeywords, config.allowedNumbers)
        val written = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { BlockRulesFile.write(rules, it) } != null
            }.getOrDefault(false)
        }
        report(getString(if (written) R.string.filter_rules_exported else org.fossify.commons.R.string.exporting_failed))
    }

    private fun import(uri: Uri) = lifecycleScope.launch {
        val rules = withContext(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)?.use(BlockRulesFile::read) }.getOrNull()
        }
        if (rules == null || rules.numbers.isEmpty() && rules.phrases.isEmpty() && rules.allowed.isEmpty()) {
            report(getString(org.fossify.commons.R.string.importing_failed))
            return@launch
        }
        rules.numbers.forEach(config::addSpamNumber)
        rules.phrases.forEach(config::addBlockedKeyword)
        rules.allowed.forEach(config::addAllowedNumber)
        reload()
        report(
            getString(
                R.string.rules_summary,
                resources.getQuantityString(R.plurals.blocked_number_count, rules.numbers.size, rules.numbers.size),
                resources.getQuantityString(R.plurals.blocked_phrase_count, rules.phrases.size, rules.phrases.size),
                resources.getQuantityString(R.plurals.never_spam_sender_count, rules.allowed.size, rules.allowed.size),
            ).let { getString(R.string.filter_rules_imported, it) },
        )
    }

    private suspend fun report(message: String) {
        snackbarHost.showSnackbar(message)
    }

    private companion object {
        const val JSON = "application/json"
    }
}

package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityStarredMessagesBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.messageSearchResult
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.setupSurfaceAppBar
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.SearchResult

class StarredMessagesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityStarredMessagesBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.starredList))
        setupMaterialScrollListener(scrollingView = binding.starredList, topAppBar = binding.starredAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupSurfaceAppBar(binding.starredAppbar)
        loadStarredMessages()
    }

    private fun loadStarredMessages() {
        ensureBackgroundThread {
            val results = config.starredMessages.mapNotNull { it.removePrefix("mms:").toLongOrNull() }
                .distinct()
                .chunked(ROOM_MAX_BIND_ARGS)
                .flatMap { messagesDB.getMessagesWithIds(it) }
                .filter { config.isMessageStarred(it.id, it.isMMS) }
                .sortedByDescending { it.date }
                .mapTo(ArrayList()) { messageSearchResult(it) }
            runOnUiThread { showResults(results) }
        }
    }

    private fun showResults(results: ArrayList<SearchResult>) {
        binding.noStarredPlaceholder.beVisibleIf(results.isEmpty())
        val adapter = binding.starredList.adapter as? SearchResultsAdapter
        if (adapter == null) {
            binding.starredList.adapter = SearchResultsAdapter(this, results, binding.starredList, "") {
                openMessage(it as SearchResult)
            }
        } else {
            adapter.updateItems(results, "")
        }
    }

    private fun openMessage(result: SearchResult) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, result.threadId)
            putExtra(THREAD_TITLE, result.title)
            putExtra(SEARCHED_MESSAGE_ID, result.messageId)
            startActivity(this)
        }
    }

    companion object {
        // SQLite caps bound parameters at 999
        private const val ROOM_MAX_BIND_ARGS = 900
    }
}

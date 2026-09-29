package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.BlockedMessagesAdapter
import org.fossify.messages.databinding.ActivityBlockedMessagesBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.getSpamThreads
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.setupEmptyStateAction
import org.fossify.messages.extensions.setupSurfaceAppBar
import org.fossify.messages.helpers.InboxRepository
import org.fossify.messages.helpers.OPEN_SPAM
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.models.BlockedMessagesThread

class BlockedMessagesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityBlockedMessagesBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.blockedList))
        setupMaterialScrollListener(
            scrollingView = binding.blockedList,
            topAppBar = binding.blockedAppbar
        )
    }

    override fun refreshAfterDeletion() { loadBlockedMessages() }

    override fun onResume() {
        super.onResume()
        setupSurfaceAppBar(binding.blockedAppbar)
        loadBlockedMessages()
    }

    private fun setupOptionsMenu() {
        binding.blockedToolbar.inflateMenu(R.menu.blocked_messages_menu)
        binding.blockedToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.clear_spam -> clearAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun loadBlockedMessages() {
        ensureBackgroundThread {
            val threads = try {
                getSpamThreads().filter { thread -> thread.messageIds.any { it !in UndoDeletion.spam } }
            } catch (e: Exception) {
                emptyList()
            }

            runOnUiThread {
                showItems(ArrayList(threads))
            }
        }
    }

    private fun showItems(items: ArrayList<BlockedMessagesThread>) {
        binding.noBlockedPlaceholder.beVisibleIf(items.isEmpty())
        binding.noBlockedPlaceholder2.beVisibleIf(items.isEmpty())
        setupEmptyStateAction(binding.noBlockedPlaceholder2) { finish() }
        binding.blockedFastscroller.beGoneIf(items.isEmpty())
        binding.blockedToolbar.menu.findItem(R.id.clear_spam).isVisible = items.isNotEmpty()

        getOrCreateAdapter().updateThreads(items)
        if (areSystemAnimationsEnabled) {
            binding.blockedList.scheduleLayoutAnimation()
        }
    }

    private fun getOrCreateAdapter(): BlockedMessagesAdapter {
        var currAdapter = binding.blockedList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = BlockedMessagesAdapter(
                activity = this,
                recyclerView = binding.blockedList,
                onRefresh = { loadBlockedMessages() },
                itemClick = { openThread(it as BlockedMessagesThread) },
                restoreThreads = { unmarkThreads(it, allowSender = false) },
                deleteThreads = { deleteThreads(it) },
                allowThreads = { unmarkThreads(it, allowSender = true) }
            )
            binding.blockedList.adapter = currAdapter
        }

        return currAdapter as BlockedMessagesAdapter
    }

    private fun openThread(thread: BlockedMessagesThread) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, thread.threadId)
            putExtra(THREAD_TITLE, thread.title)
            putExtra(OPEN_SPAM, true)
            startActivity(this)
        }
    }

    private fun unmarkThreads(threads: List<BlockedMessagesThread>, allowSender: Boolean) {
        ensureBackgroundThread {
            threads.forEach { thread ->
                if (allowSender) config.addAllowedNumber(thread.address)
                messagesDB.deleteThreadSpamMarkers(thread.threadId)
            }
            InboxRepository.refreshUnreadCounts(this, threads.map { it.threadId })
            runOnUiThread {
                toast(if (allowSender) R.string.sender_allowed else R.string.message_restored)
                loadBlockedMessages()
            }
        }
    }

    private fun deleteThreads(threads: List<BlockedMessagesThread>) {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.delete_spam_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            deleteSpamWithUndo(threads)
        }
    }

    private fun clearAll() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.clear_spam_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            deleteSpamWithUndo(getOrCreateAdapter().currentList)
        }
    }

    // Deletes only the spam SMS; real messages in the same threads stay.
    private fun deleteSpamWithUndo(threads: List<BlockedMessagesThread>) {
        val ids = threads.flatMap { it.messageIds }.toSet()
        UndoDeletion.spam.addAll(ids)
        showItems(ArrayList(getOrCreateAdapter().currentList.filter { thread -> thread.messageIds.any { it !in ids } }))
        UndoDeletion.offer(this,
            undo = { UndoDeletion.spam.removeAll(ids); loadBlockedMessages() },
            commit = {
                ids.forEach { deleteMessage(it, isMMS = false) }
                // Drops the rows of threads that are now empty and refreshes the rest.
                InboxRepository.scheduleProviderReconcile(this, immediate = true)
            },
            completed = { UndoDeletion.spam.removeAll(ids); loadBlockedMessages() })
    }
}

package org.fossify.messages.activities

import org.fossify.messages.helpers.UndoDeletion
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
import org.fossify.messages.extensions.blockedMessagesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.setupEmptyStateAction
import org.fossify.messages.extensions.setupSurfaceAppBar
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.extensions.restoreBlockedMessage
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.BlockedMessage
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
            val items = try {
                blockedMessagesDB.getAll()
            } catch (e: Exception) {
                emptyList()
            }
            val threads = buildThreads(items)

            runOnUiThread {
                showItems(threads)
            }
        }
    }

    private fun buildThreads(items: List<BlockedMessage>): ArrayList<BlockedMessagesThread> {
        return items.filter { it.id !in UndoDeletion.spam }.groupBy { it.address }.map { (address, messages) ->
            val sortedMessages = messages.sortedByDescending { it.date }
            val latestMessage = sortedMessages.first()
            val namePhoto = getNameAndPhotoFromPhoneNumber(address)
            BlockedMessagesThread(
                address = address,
                title = namePhoto.name,
                photoUri = namePhoto.photoUri.orEmpty(),
                snippet = latestMessage.body,
                date = latestMessage.date,
                count = sortedMessages.size,
                messages = sortedMessages
            )
        }.sortedByDescending { it.date }.toMutableList() as ArrayList<BlockedMessagesThread>
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
                restoreThreads = { restoreThreads(it) },
                deleteThreads = { deleteThreads(it) },
                allowThreads = { allowThreads(it) }
            )
            binding.blockedList.adapter = currAdapter
        }

        return currAdapter as BlockedMessagesAdapter
    }

    private fun openThread(thread: BlockedMessagesThread) {
        Intent(this, BlockedMessagesThreadActivity::class.java).apply {
            putExtra(THREAD_NUMBER, thread.address)
            putExtra(THREAD_TITLE, thread.title)
            startActivity(this)
        }
    }

    private fun allowThreads(threads: List<BlockedMessagesThread>) {
        ensureBackgroundThread {
            var failed = false
            threads.forEach { thread ->
                config.addAllowedNumber(thread.address)
                thread.messages.forEach {
                    if (!restoreBlockedMessage(it)) {
                        failed = true
                    }
                }
            }
            runOnUiThread {
                if (failed) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                } else {
                    toast(R.string.sender_allowed)
                }
                loadBlockedMessages()
            }
        }
    }

    private fun restoreThreads(threads: List<BlockedMessagesThread>) {
        ensureBackgroundThread {
            val failed = threads.flatMap { it.messages }.any { !restoreBlockedMessage(it) }
            runOnUiThread {
                if (failed) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                } else {
                    toast(R.string.message_restored)
                }
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
            deleteSpamWithUndo(threads.flatMap { it.messages })
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
            deleteSpamWithUndo(getOrCreateAdapter().currentList.flatMap { it.messages })
        }
    }
    private fun deleteSpamWithUndo(items: List<BlockedMessage>) {
        val ids = items.map { it.id }.toSet()
        UndoDeletion.spam.addAll(ids)
        showItems(ArrayList(getOrCreateAdapter().currentList.filter { thread -> thread.messages.any { it.id !in ids } }))
        UndoDeletion.offer(this,
            undo = { UndoDeletion.spam.removeAll(ids); loadBlockedMessages() },
            commit = { ids.forEach { blockedMessagesDB.delete(it) } },
            completed = { UndoDeletion.spam.removeAll(ids); loadBlockedMessages() })
    }

}

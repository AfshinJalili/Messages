package org.fossify.messages.activities

import org.fossify.messages.helpers.UndoDeletion
import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.databinding.ActivityThreadBinding
import org.fossify.messages.extensions.blockedMessagesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.setupSurfaceAppBar
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.extensions.restoreBlockedMessage
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.BlockedMessage
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadDateTime

class BlockedMessagesThreadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThreadBinding::inflate)
    private var address = ""
    private var messages = ArrayList<Message>()
    private var blockedMessages = ArrayList<BlockedMessage>()
    private var threadItems = ArrayList<ThreadItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        address = intent.getStringExtra(THREAD_NUMBER).orEmpty()
        if (address.isBlank()) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        binding.threadToolbar.title = intent.getStringExtra(THREAD_TITLE) ?: address
        binding.messageHolder.root.beGone()
        binding.shortCodeHolder.root.beGone()
        binding.threadAddContacts.beGone()
        setupOptionsMenu()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.threadMessagesList))
        setupMaterialScrollListener(null, binding.threadAppbar)
        loadMessages()
    }

    override fun refreshAfterDeletion() { loadMessages() }

    override fun onResume() {
        super.onResume()
        setupSurfaceAppBar(binding.threadAppbar)
    }

    private fun setupOptionsMenu() {
        binding.threadToolbar.menu.clear()
        binding.threadToolbar.inflateMenu(R.menu.blocked_thread_menu)
        binding.threadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.restore -> restoreAll()
                R.id.allow_sender -> allowSender()
                R.id.delete -> deleteAll()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun loadMessages() {
        ensureBackgroundThread {
            blockedMessages = blockedMessagesDB.getAll()
                .filter { it.address == address }
                .sortedBy { it.date }
                .toMutableList() as ArrayList<BlockedMessage>
            if (blockedMessages.isEmpty()) {
                runOnUiThread { finish() }
                return@ensureBackgroundThread
            }

            val namePhoto = getNameAndPhotoFromPhoneNumber(address)
            val senderName = namePhoto.name
            val photoUri = namePhoto.photoUri.orEmpty()
            val participant = SimpleContact(
                rawId = address.hashCode(),
                contactId = address.hashCode(),
                name = senderName,
                photoUri = photoUri,
                phoneNumbers = arrayListOf(PhoneNumber(address, 0, "", address)),
                birthdays = ArrayList(),
                anniversaries = ArrayList()
            )

            messages = blockedMessages.mapTo(ArrayList()) { it.toMessage(participant) }
            threadItems = getThreadItems()

            runOnUiThread {
                getOrCreateThreadAdapter().updateMessages(threadItems, threadItems.lastIndex)
            }
        }
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        var currAdapter = binding.threadMessagesList.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = binding.threadMessagesList,
                itemClick = {},
                isRecycleBin = true, // Reuse the restore/delete actions for spam.
                deleteMessages = { selectedMessages, _, restore ->
                    handleSelectedMessages(selectedMessages, restore)
                }
            )
            binding.threadMessagesList.adapter = currAdapter
        }
        return currAdapter as ThreadAdapter
    }

    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        var previousDate = 0
        messages.sortBy { it.date }
        messages.filter { it.id !in UndoDeletion.spam }.forEach { message ->
            if (message.date - previousDate > MIN_DATE_TIME_DIFF_SECS) {
                items.add(ThreadDateTime(message.date, "?"))
                previousDate = message.date
            }
            items.add(message)
        }
        return items
    }

    private fun handleSelectedMessages(selectedMessages: List<Message>, restore: Boolean) {
        if (!restore) {
            runOnUiThread {
                val ids = selectedMessages.map { it.id }.toSet()
                UndoDeletion.spam.addAll(ids)
                threadItems = getThreadItems()
                getOrCreateThreadAdapter().apply { updateMessages(threadItems); finishActMode() }
                UndoDeletion.offer(this,
                    undo = {
                        UndoDeletion.spam.removeAll(ids)
                        threadItems = getThreadItems()
                        getOrCreateThreadAdapter().updateMessages(threadItems)
                    },
                    commit = { ids.forEach { blockedMessagesDB.delete(it) } },
                    completed = { UndoDeletion.spam.removeAll(ids); loadMessages() })
            }
            return
        }
        val deletePosition = selectedMessages.firstOrNull()?.let { threadItems.indexOf(it) } ?: -1
        val ids = selectedMessages.map { it.id }.toSet()
        messages.removeAll { ids.contains(it.id) }
        threadItems = getThreadItems()

        ensureBackgroundThread {
            var failed = false
            blockedMessages.filter { ids.contains(it.id) }.forEach {
                if (restore) {
                    if (!restoreBlockedMessage(it)) {
                        failed = true
                    }
                } else {
                    blockedMessagesDB.delete(it.id)
                }
            }

            runOnUiThread {
                if (failed) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                if (messages.isEmpty()) {
                    finish()
                } else {
                    getOrCreateThreadAdapter().apply {
                        updateMessages(threadItems, deletePosition)
                        finishActMode()
                    }
                }
            }
        }
    }

    private fun restoreAll() {
        handleSelectedMessages(messages.toList(), restore = true)
    }

    private fun allowSender() {
        ensureBackgroundThread {
            config.addAllowedNumber(address)
            val failed = blockedMessages.any { !restoreBlockedMessage(it) }
            runOnUiThread {
                if (failed) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                } else {
                    toast(R.string.sender_allowed)
                    finish()
                }
            }
        }
    }

    private fun deleteAll() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.delete_spam_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            handleSelectedMessages(messages.toList(), restore = false)
        }
    }

    companion object {
        private const val MIN_DATE_TIME_DIFF_SECS = 300
    }
}

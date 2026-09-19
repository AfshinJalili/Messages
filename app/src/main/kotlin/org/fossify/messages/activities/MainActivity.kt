package org.fossify.messages.activities

import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.helpers.designFloat
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.search.SearchView
import org.fossify.commons.extensions.setSystemBarsAppearance
import org.fossify.messages.databinding.ItemInboxFilterChipBinding
import org.fossify.messages.databinding.ItemRecentSearchChipBinding
import org.fossify.messages.extensions.messageSearchResult
import org.fossify.messages.extensions.setupEmptyStateAction
import org.fossify.messages.helpers.INBOX_FILTER
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.helpers.sortedForInbox
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.ConversationSwipeCallback
import org.fossify.messages.helpers.InboxRepository
import org.fossify.messages.helpers.SWIPE_UNDO_DURATION_MS
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true

    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var inboxConversations = arrayListOf<Conversation>()
    private var providerReconcileActive = false
    private var inboxFilter = InboxFilter.ALL
    private var bus: EventBus? = null
    private var inboxDeletionVersion = UndoDeletion.version
    private val pendingSwipes = mutableListOf<PendingSwipe>()
    private val searchHandler = Handler(Looper.getMainLooper())

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        inboxFilter = InboxFilter.entries.getOrNull(savedInstanceState?.getInt(INBOX_FILTER) ?: 0) ?: InboxFilter.ALL
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        setupSearch()
        setupInboxFilters()
        binding.inboxSpam.setOnClickListener { launchSpamFolder() }
        refreshMenuItems()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList, binding.searchResultsList))

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSearchColors()
        refreshMenuItems()

        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        updateFilterChipColors()

        val properPrimaryColor = getProperPrimaryColor()
        binding.inboxSpam.setTextColor(properPrimaryColor)
        setupEmptyStateAction(binding.noConversationsPlaceholder2) { launchNewConversation() }
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(resources.designFloat(R.dimen.opacity_outline))
        checkShortcut()
        InboxRepository.refreshUnreadCounts(this)
        InboxRepository.scheduleProviderReconcile(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(INBOX_FILTER, inboxFilter.ordinal)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the inbox is the point of no return: commit rather than silently drop the swipe.
        pendingSwipes.toList().forEach { commitSwipe(it) }
    }

    override fun onDestroy() {
        InboxRepository.setReconcileListener(null)
        searchHandler.removeCallbacksAndMessages(null)
        bus?.unregister(this)
        super.onDestroy()
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainSearchView.isShowing) {
            binding.mainSearchView.hide()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainSearchBar.inflateMenu(R.menu.menu_main)
        binding.mainSearchBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.show_archived -> launchArchivedConversations()
                R.id.show_starred -> launchStarredMessages()
                R.id.settings -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupSearch() {
        binding.mainSearchView.editText.doAfterTextChanged { text ->
            // Each query is a LIKE over every cached message, so wait for a pause in typing.
            searchHandler.removeCallbacksAndMessages(null)
            searchHandler.postDelayed({ searchTextChanged(text?.toString().orEmpty()) }, SEARCH_DEBOUNCE_MS)
        }

        binding.mainSearchView.addTransitionListener { _, _, newState ->
            when (newState) {
                SearchView.TransitionState.SHOWING -> searchTextChanged(binding.mainSearchView.text.toString())
                SearchView.TransitionState.HIDDEN -> {
                    searchHandler.removeCallbacksAndMessages(null)
                    binding.mainSearchView.clearText()
                }

                else -> Unit
            }
        }
    }

    private fun setupInboxFilters() {
        InboxFilter.entries.forEach { filter ->
            ItemInboxFilterChipBinding.inflate(layoutInflater, binding.inboxFilters, false).root.apply {
                id = View.generateViewId()
                tag = filter
                setText(filter.label)
                isChecked = filter == inboxFilter
                binding.inboxFilters.addView(this)
            }
        }

        binding.inboxFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            val checked = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            inboxFilter = group.findViewById<Chip>(checked).tag as InboxFilter
            // a selection made under one filter must not act on rows the next filter hides
            getOrCreateConversationsAdapter().finishActMode()
            setupConversations(inboxConversations)
        }
    }

    private fun updateFilterChipColors() {
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val background = ColorStateList(states, intArrayOf(primaryColor.adjustAlpha(resources.designFloat(R.dimen.opacity_selected_tint)), Color.TRANSPARENT))
        val content = ColorStateList(states, intArrayOf(primaryColor, textColor))
        val stroke = ColorStateList(states, intArrayOf(Color.TRANSPARENT, textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_outline))))
        binding.inboxFilters.children.filterIsInstance<Chip>().forEach {
            it.chipBackgroundColor = background
            it.chipStrokeColor = stroke
            it.setTextColor(content)
        }
    }

    private fun updateSearchColors() {
        val backgroundColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        val hintColor = textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_hint))
        window.setSystemBarsAppearance(backgroundColor)
        binding.mainAppbar.setBackgroundColor(backgroundColor)
        binding.mainSearchBar.apply {
            backgroundTintList = ColorStateList.valueOf(textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_surface_tint)))
            textView.setTextColor(textColor)
            textView.setHintTextColor(hintColor)
            navigationIcon?.applyColorFilter(textColor)
            overflowIcon?.applyColorFilter(textColor)
        }
        updateMenuItemColors(binding.mainSearchBar.menu, baseColor = backgroundColor)

        // SearchView has no background setter; its surface comes from theme attrs that ignore Fossify's colors
        binding.mainSearchView.apply {
            findViewById<View>(com.google.android.material.R.id.open_search_view_background)?.setBackgroundColor(backgroundColor)
            editText.setTextColor(textColor)
            editText.setHintTextColor(hintColor)
            toolbar.navigationIcon?.applyColorFilter(textColor)
        }
    }

    fun onInboxSelectionChanged(selecting: Boolean) {
        // Keep the header's space so drag-select does not move another row under the finger.
        binding.inboxHeader.visibility = if (selecting) View.INVISIBLE else View.VISIBLE
        binding.conversationsFab.beGoneIf(selecting)
    }

    private fun refreshMenuItems() {
        binding.mainSearchBar.menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        storeStateVariables()
        InboxRepository.setReconcileListener { active ->
            providerReconcileActive = active
            if (!isDestroyed && !isFinishing) {
                showOrHideProgress(active && inboxConversations.isEmpty())
            }
        }
        conversationsDB.observeNonArchivedWithLatestSnippet().observe(this) { rows ->
            if (isDestroyed || isFinishing) return@observe
            setupConversations(ArrayList(rows.map { it.toConversation() }))
        }
        InboxRepository.scheduleProviderReconcile(this, immediate = true)
        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            setupSwipeActions(currAdapter)
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupSwipeActions(adapter: ConversationsAdapter) {
        val callback = ConversationSwipeCallback(
            context = this,
            isSwipeEnabled = { !adapter.isSelecting },
            onSwipe = { position, action -> handleSwipe(position, action) }
        )
        ItemTouchHelper(callback).attachToRecyclerView(binding.conversationsList)
    }

    private fun handleSwipe(position: Int, action: SwipeAction) {
        val adapter = getOrCreateConversationsAdapter()
        val conversation = adapter.currentList.getOrNull(position) ?: return
        when (action) {
            SwipeAction.NONE -> adapter.notifyItemChanged(position)
            SwipeAction.MUTE -> toggleMute(conversation)
            SwipeAction.ARCHIVE -> deferSwipe(conversation, action)
            SwipeAction.DELETE -> adapter.deleteWithUndo(listOf(conversation)) { id ->
                deleteConversation(id)
                notificationManager.cancel(id.hashCode())
            }
        }
    }

    /** Muting is harmless and instantly reversible, so it applies at once and Undo flips it back. */
    private fun toggleMute(conversation: Conversation) {
        val threadId = conversation.threadId
        val muted = !config.isConversationMuted(threadId)
        config.setConversationMuted(threadId, muted)
        notifyConversationChanged(threadId)
        binding.conversationsList.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val message = if (muted) R.string.conversation_muted else R.string.conversation_unmuted
        Snackbar.make(binding.mainCoordinator, message, SWIPE_UNDO_DURATION_MS)
            .setAction(org.fossify.commons.R.string.undo) {
                config.setConversationMuted(threadId, !muted)
                notifyConversationChanged(threadId)
            }
            .show()
    }

    private fun notifyConversationChanged(threadId: Long) {
        val adapter = getOrCreateConversationsAdapter()
        val position = adapter.currentList.indexOfFirst { it.threadId == threadId }
        if (position != -1) {
            adapter.notifyItemChanged(position)
        }
    }

    /**
     * The action is deferred until the undo Snackbar goes away, because deleting a conversation
     * hits the telephony provider and cannot be reversed once it has run.
     */
    private fun deferSwipe(conversation: Conversation, action: SwipeAction) {
        val pending = PendingSwipe(conversation, action)
        pendingSwipes.add(pending)
        binding.conversationsList.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setupConversations(inboxConversations)

        val message = if (action == SwipeAction.ARCHIVE) {
            R.string.conversation_archived
        } else {
            R.string.conversation_deleted
        }

        Snackbar.make(binding.mainCoordinator, message, SWIPE_UNDO_DURATION_MS)
            .setAction(org.fossify.commons.R.string.undo) { undoSwipe(pending) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        commitSwipe(pending)
                    }
                }
            })
            .show()
    }

    private fun undoSwipe(pending: PendingSwipe) {
        if (pendingSwipes.remove(pending)) {
            setupConversations(inboxConversations)
        }
    }

    private fun commitSwipe(pending: PendingSwipe) {
        if (!pendingSwipes.remove(pending)) {
            return
        }

        val threadId = pending.conversation.threadId
        inboxConversations.removeAll { it.threadId == threadId }
        notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            when (pending.action) {
                SwipeAction.ARCHIVE -> updateConversationArchivedStatus(threadId, true)
                SwipeAction.DELETE -> deleteConversation(threadId)
                else -> Unit
            }
        }
    }

    /** Identity-based on purpose, so each swipe is committed or undone exactly once. */
    private class PendingSwipe(val conversation: Conversation, val action: SwipeAction)

    private fun setupConversations(conversations: ArrayList<Conversation>) {
        inboxDeletionVersion = UndoDeletion.version
        inboxConversations = conversations
        val swipedAway = pendingSwipes.map { it.conversation.threadId }.toSet()
        val visibleConversations = conversations
            .filter { it.threadId !in swipedAway && it.threadId !in UndoDeletion.threads && inboxFilter.matches(it) }
            .sortedForInbox(config.pinnedConversations)

        if (providerReconcileActive && conversations.isEmpty()) {
            showOrHideProgress(true)
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(visibleConversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(visibleConversations) {
                    showOrHidePlaceholder(currentList.isEmpty())
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.setText(
            when (inboxFilter) {
                InboxFilter.ALL -> R.string.no_conversations_found
                InboxFilter.UNREAD -> R.string.no_unread_conversations
                else -> R.string.no_filtered_conversations
            }
        )
        binding.noConversationsPlaceholder2.beVisibleIf(show && inboxFilter == InboxFilter.ALL)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String) {
        lastSearchedText = text
        val recentSearches = config.recentSearches
        val showRecent = text.isEmpty() && recentSearches.isNotEmpty()
        binding.recentSearchesHolder.beVisibleIf(showRecent)
        if (showRecent) {
            showRecentSearches(recentSearches)
        }

        val isQuery = text.length >= 2
        binding.searchPlaceholder2.beVisibleIf(!isQuery && !showRecent)
        if (!isQuery) {
            binding.searchPlaceholder.beGone()
            binding.searchResultsList.beGone()
            return
        }

        ensureBackgroundThread {
            val searchQuery = "%$text%"
            val messages = messagesDB.getMessagesWithText(searchQuery)
            val conversations = conversationsDB.getConversationsWithText(searchQuery)
            if (text == lastSearchedText) {
                showSearchResults(messages, conversations, text)
            }
        }
    }

    private fun showRecentSearches(recentSearches: List<String>) {
        val textColor = getProperTextColor()
        binding.recentSearches.removeAllViews()
        recentSearches.forEach { query ->
            ItemRecentSearchChipBinding.inflate(layoutInflater, binding.recentSearches, false).root.apply {
                text = query
                setTextColor(textColor)
                setOnClickListener {
                    binding.mainSearchView.setText(query)
                    binding.mainSearchView.editText.setSelection(query.length)
                }
                binding.recentSearches.addView(this)
            }
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            searchResults.add(messageSearchResult(message))
        }

        runOnUiThread {
            if (isDestroyed || isFinishing || searchedText != lastSearchedText || !binding.mainSearchView.isShowing) {
                return@runOnUiThread
            }

            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    openSearchResult(it as SearchResult)
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun openSearchResult(result: SearchResult) {
        // only queries that led somewhere are worth offering again
        config.addRecentSearch(lastSearchedText)
        hideKeyboard()
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, result.threadId)
            putExtra(THREAD_TITLE, result.title)
            putExtra(SEARCHED_MESSAGE_ID, result.messageId)
            startActivity(this)
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchStarredMessages() {
        hideKeyboard()
        startActivity(Intent(applicationContext, StarredMessagesActivity::class.java))
    }

    private fun launchSpamFolder() {
        hideKeyboard()
        startActivity(Intent(applicationContext, BlockedMessagesActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        InboxRepository.refreshUnreadCounts(this)
        InboxRepository.scheduleProviderReconcile(this)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}

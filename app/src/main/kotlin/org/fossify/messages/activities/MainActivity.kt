package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.search.SearchView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.setSystemBarsAppearance
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.databinding.ItemRecentSearchChipBinding
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.getAllDrafts
import org.fossify.messages.extensions.getUnreadSpamCounts
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.messageSearchResult
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.INBOX_FILTER
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.InboxRepository
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SWIPE_UNDO_DURATION_MS
import org.fossify.messages.helpers.SpamBackfill
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.helpers.designFloat
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.sortedForInbox
import org.fossify.messages.helpers.swipeAction
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.inbox.InboxAction
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxUiState
import org.fossify.messages.ui.inbox.LibraryDestination

class MainActivity : SimpleActivity(), UndoDeletion.UndoHost {
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var lastSearchedText = ""
    private var inboxConversations = listOf<Conversation>()
    private var providerReconcileActive = false
    private var inboxFilter = InboxFilter.ALL
    private var drafts: Map<Long, String> = emptyMap()
    private val pendingSwipes = mutableListOf<PendingSwipe>()
    // Committed archives stay hidden until Room stops listing them, or a refresh would bring them back.
    private val archiving = mutableSetOf<Long>()
    private var undoJob: Job? = null
    private var undoSettle: (() -> Unit)? = null
    private val searchHandler = Handler(Looper.getMainLooper())

    private var inbox by mutableStateOf(InboxUiState())
    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    @VisibleForTesting
    internal val snackbarHost = SnackbarHostState()

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @VisibleForTesting
    internal val inboxRows get() = inbox.rows

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        inboxFilter = InboxFilter.entries.getOrNull(savedInstanceState?.getInt(INBOX_FILTER) ?: 0) ?: InboxFilter.ALL
        savedInstanceState?.getLongArray(INBOX_SELECTION)?.let { inbox = inbox.copy(selected = it.toSet()) }
        appLaunched(BuildConfig.APPLICATION_ID)
        setupSearch()
        refreshInboxTheme()
        binding.inboxCompose.setContent {
            OpenLineTheme(dark = darkTheme, textScale = textScale) {
                InboxScreen(
                    state = inbox,
                    snackbarHostState = snackbarHost,
                    onOpen = { openConversation(it.conversation) },
                    onToggleSelection = ::toggleSelection,
                    onSelectAll = { inbox = inbox.copy(selected = inbox.rows.map { it.threadId }.toSet()) },
                    onClearSelection = ::clearSelection,
                    onAction = ::handleAction,
                    onSwipe = ::handleSwipe,
                    onFilter = ::setFilter,
                    onSearch = { binding.mainSearchView.show() },
                    onNewMessage = ::launchNewConversation,
                    onLibrary = ::openLibrary,
                    onSettings = ::launchSettings,
                )
            }
        }

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.searchResultsList))

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInboxTheme()
        updateSearchColors()
        applySystemBars()
        refreshSpamBadge()
        refreshDrafts()
        publish()
        checkShortcut()
        // Debounced: give ThreadActivity read flushes time to reach Room before reconciling.
        InboxRepository.refreshInbox(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(INBOX_FILTER, inboxFilter.ordinal)
        outState.putLongArray(INBOX_SELECTION, inbox.selected.toLongArray())
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the inbox is the point of no return: commit rather than silently drop the swipe. The
        // snackbar goes too, so a later Undo tap cannot claim to reverse a write that already ran.
        undoJob?.cancel()
        undoSettle = null
        pendingSwipes.toList().forEach { commitSwipe(it) }
    }

    override fun onDestroy() {
        InboxRepository.setReconcileListener(null)
        searchHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressedCompat(): Boolean {
        return when {
            binding.mainSearchView.isShowing -> {
                binding.mainSearchView.hide()
                true
            }

            inbox.selecting -> {
                clearSelection()
                true
            }

            else -> {
                appLockManager.lock()
                false
            }
        }
    }

    /** The Open Line palette follows the app's light/dark choice, not the Commons accent. */
    private fun refreshInboxTheme() {
        darkTheme = ColorUtils.calculateLuminance(getProperBackgroundColor()) < DARK_LUMINANCE
        textScale = getTextSize() / resources.getDimension(org.fossify.commons.R.dimen.normal_text_size)
    }

    private fun applySystemBars() {
        if (binding.mainSearchView.isShowing) {
            window.setSystemBarsAppearance(getProperBackgroundColor())
            return
        }

        // The bottom bar paints its own surface; a system scrim on top would double it.
        if (isQPlus()) window.isNavigationBarContrastEnforced = false
        // Status icons sit on the pine identity band in both themes.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkTheme
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
                SearchView.TransitionState.SHOWING -> {
                    applySystemBars()
                    searchTextChanged(binding.mainSearchView.text.toString())
                }

                SearchView.TransitionState.HIDDEN -> {
                    searchHandler.removeCallbacksAndMessages(null)
                    binding.mainSearchView.clearText()
                    applySystemBars()
                }

                else -> Unit
            }
        }
    }

    private fun updateSearchColors() {
        val backgroundColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        val hintColor = textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_hint))
        // SearchView has no background setter; its surface comes from theme attrs that ignore Fossify's colors
        binding.mainSearchView.apply {
            findViewById<View>(com.google.android.material.R.id.open_search_view_background)?.setBackgroundColor(backgroundColor)
            editText.setTextColor(textColor)
            editText.setHintTextColor(hintColor)
            toolbar.navigationIcon?.applyColorFilter(textColor)
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
        InboxRepository.setReconcileListener { active ->
            providerReconcileActive = active
            if (!isDestroyed && !isFinishing) {
                publish()
            }
        }
        conversationsDB.observeNonArchivedWithLatestSnippet().observe(this) { rows ->
            if (isDestroyed || isFinishing) return@observe
            inboxConversations = rows.map { it.toConversation() }
            archiving.retainAll(inboxConversations.map { it.threadId }.toSet())
            publish()
            // Marker changes also invalidate this query, so new spam updates the badge here.
            refreshSpamBadge()
        }
        InboxRepository.scheduleProviderReconcile(this, immediate = true)
        SpamBackfill.run(this)
    }

    /** Rebuilds the screen state from the Room snapshot plus everything only this activity knows. */
    private fun publish() {
        val hidden = pendingSwipes.map { it.conversation.threadId }.toSet() + UndoDeletion.threads + archiving
        val present = inboxConversations.filter { it.threadId !in hidden }
        val pinned = config.pinnedConversations
        val rows = present
            .filter { inboxFilter.matches(it) }
            .sortedForInbox(pinned)
            .map {
                InboxRow(
                    conversation = it,
                    draft = drafts[it.threadId],
                    pinned = it.threadId.toString() in pinned,
                    muted = config.isConversationMuted(it.threadId),
                )
            }
        val visibleIds = rows.map { it.threadId }.toSet()
        inbox = inbox.copy(
            rows = rows,
            filter = inboxFilter,
            unreadMessages = present.filter { !it.read }.sumOf { it.unreadCount.coerceAtLeast(1) },
            loading = providerReconcileActive && inboxConversations.isEmpty(),
            // A selection must not act on rows the current filter or an undo window hides. Before the
            // first snapshot arrives, keep a selection restored from saved state.
            selected = if (inboxConversations.isEmpty()) inbox.selected else inbox.selected intersect visibleIds,
            archiveAvailable = config.isArchiveAvailable,
            recycleBinAvailable = config.useRecycleBin,
            swipeLeft = config.swipeAction(towardsRight = false),
            swipeRight = config.swipeAction(towardsRight = true),
        )
    }

    private fun refreshDrafts() {
        ensureBackgroundThread {
            val fresh = getAllDrafts()
            runOnUiThread {
                if (fresh != drafts) {
                    drafts = fresh
                    publish()
                }
            }
        }
    }

    private fun setFilter(filter: InboxFilter) {
        inboxFilter = filter
        clearSelection()
        publish()
    }

    private fun toggleSelection(row: InboxRow) {
        val selected = inbox.selected
        inbox = inbox.copy(selected = if (row.threadId in selected) selected - row.threadId else selected + row.threadId)
    }

    private fun clearSelection() {
        inbox = inbox.copy(selected = emptySet())
    }

    private fun handleSwipe(row: InboxRow, action: SwipeAction) {
        val conversation = row.conversation
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (action) {
            SwipeAction.NONE -> Unit
            SwipeAction.MUTE -> toggleMute(conversation, muted = !row.muted)
            SwipeAction.ARCHIVE -> deferArchive(conversation)
            SwipeAction.DELETE -> deleteWithUndo(listOf(conversation))
        }
    }

    /** Muting is harmless and instantly reversible, so it applies at once and Undo flips it back. */
    private fun toggleMute(conversation: Conversation, muted: Boolean) {
        setMuted(listOf(conversation), muted)
        val message = if (muted) R.string.conversation_muted else R.string.conversation_unmuted
        showUndo(message, onUndo = { setMuted(listOf(conversation), !muted) })
    }

    private fun setMuted(conversations: List<Conversation>, muted: Boolean) {
        conversations.forEach { config.setConversationMuted(it.threadId, muted) }
        publish()
    }

    /**
     * Archiving is deferred until the undo snackbar goes away, because the provider write cannot be
     * reversed once it has run.
     */
    private fun deferArchive(conversation: Conversation) {
        val pending = PendingSwipe(conversation, SwipeAction.ARCHIVE)
        pendingSwipes.add(pending)
        publish()
        showUndo(R.string.conversation_archived, onUndo = { undoSwipe(pending) }, onTimeout = { commitSwipe(pending) })
    }

    private fun showUndo(message: Int, onUndo: () -> Unit, onTimeout: () -> Unit = {}) {
        launchUndo(message, undoTimeoutMs(), onUndo, onTimeout)
    }

    /**
     * Deletion Undo from [UndoDeletion]. Its own timer normally commits; if another action's Undo
     * replaces this snackbar, the deletion commits then instead of lingering without an Undo.
     */
    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        val job = launchUndo(message, durationMs.toLong(), onUndo, onTimeout = { UndoDeletion.commitNow(this) })
        val settle = undoSettle
        // UndoDeletion dismisses its own snackbar when batches merge, time out or re-show; that is not
        // a replacement by another action, so the deletion must not commit early.
        return {
            job.cancel()
            if (undoSettle === settle) undoSettle = null
        }
    }

    /**
     * Every undo shares one host. A new one dismisses the current one, which settles it through its
     * timeout path. Cancelling the job hides the snackbar without running either callback.
     */
    private fun launchUndo(message: Int, durationMs: Long, onUndo: () -> Unit, onTimeout: () -> Unit): Job {
        // Settle and cancel the previous offer before showing this one. Only dismissing it let a second
        // showSnackbar queue behind the first, so Undo "reshowed" a snackbar instead of undoing.
        undoJob?.cancel()
        undoSettle?.invoke()
        undoSettle = onTimeout
        return lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs) {
                snackbarHost.showSnackbar(
                    message = getString(message),
                    actionLabel = getString(org.fossify.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            undoSettle = null
            if (result == SnackbarResult.ActionPerformed) onUndo() else onTimeout()
        }.also { undoJob = it }
    }

    /** Honours the system "time to take action" accessibility setting on API 29+. */
    private fun undoTimeoutMs(): Long {
        val base = SWIPE_UNDO_DURATION_MS
        if (!isQPlus()) return base.toLong()
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
        return getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(base, flags).toLong()
    }

    private fun undoSwipe(pending: PendingSwipe) {
        if (pendingSwipes.remove(pending)) {
            publish()
        }
    }

    private fun commitSwipe(pending: PendingSwipe) {
        if (!pendingSwipes.remove(pending)) {
            return
        }

        val threadId = pending.conversation.threadId
        archiving += threadId
        publish()
        notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            if (pending.action == SwipeAction.ARCHIVE) {
                updateConversationArchivedStatus(threadId, true)
            }
        }
    }

    /** Identity-based on purpose, so each swipe is committed or undone exactly once. */
    private class PendingSwipe(val conversation: Conversation, val action: SwipeAction)

    @VisibleForTesting
    internal fun deleteWithUndo(conversations: List<Conversation>) {
        // A repeated swipe or tap must not queue a second deletion of the same thread.
        val ids = conversations.map { it.threadId }.toSet() - UndoDeletion.threads
        if (ids.isEmpty()) return
        UndoDeletion.threads.addAll(ids)
        clearSelection()
        publish()
        refreshConversations()
        UndoDeletion.offer(
            this,
            undo = {
                UndoDeletion.threads.removeAll(ids)
                publish()
            },
            commit = {
                ids.forEach {
                    deleteConversation(it)
                    notificationManager.cancel(it.hashCode())
                }
            },
            completed = {
                UndoDeletion.threads.removeAll(ids)
                refreshConversations()
            },
        )
    }

    private fun handleAction(action: InboxAction) {
        val selected = inbox.selectedRows.map { it.conversation }
        val first = selected.firstOrNull() ?: return
        when (action) {
            InboxAction.ARCHIVE -> confirm(R.string.archive_confirmation, selected.size) { archive(selected) }
            InboxAction.DELETE -> confirm(org.fossify.commons.R.string.deletion_confirmation, selected.size) {
                deleteWithUndo(selected)
            }

            InboxAction.MARK_READ -> updateInBackground { selected.filter { !it.read }.forEach { markThreadMessagesRead(it.threadId) } }
            InboxAction.MARK_UNREAD -> updateInBackground { selected.filter { it.read }.forEach { markThreadMessagesUnread(it.threadId) } }
            InboxAction.MUTE, InboxAction.UNMUTE -> {
                setMuted(selected, muted = action == InboxAction.MUTE)
                clearSelection()
            }

            InboxAction.PIN, InboxAction.UNPIN -> {
                if (action == InboxAction.PIN) config.addPinnedConversations(selected) else config.removePinnedConversations(selected)
                clearSelection()
                publish()
            }

            InboxAction.DIAL -> dialNumber(first.phoneNumber) { clearSelection() }
            InboxAction.ADD_TO_CONTACT -> Intent().apply {
                this.action = Intent.ACTION_INSERT_OR_EDIT
                type = "vnd.android.cursor.item/contact"
                putExtra(KEY_PHONE, first.phoneNumber)
                launchActivityIntent(this)
            }

            InboxAction.COPY_NUMBER -> {
                copyToClipboard(first.phoneNumber)
                clearSelection()
            }

            InboxAction.RENAME -> RenameConversationDialog(this, first) { title ->
                updateInBackground { renameConversation(first, newTitle = title) }
            }

            InboxAction.DETAILS -> launchConversationDetails(first.threadId)
            InboxAction.BLOCK -> {
                val numbers = TextUtils.join(", ", selected.map { it.phoneNumber }.distinct())
                ConfirmationDialog(this, getString(org.fossify.commons.R.string.block_confirmation, numbers)) {
                    // App-level block: new SMS from these numbers are stored and go to Spam. Existing messages stay where they are.
                    selected.forEach { config.addSpamNumber(it.phoneNumber) }
                    clearSelection()
                }
            }
        }
    }

    private fun confirm(question: Int, count: Int, onConfirm: () -> Unit) {
        val items = resources.getQuantityString(R.plurals.delete_conversations, count, count)
        ConfirmationDialog(this, getString(question, items)) { onConfirm() }
    }

    private fun archive(conversations: List<Conversation>) = updateInBackground {
        conversations.forEach {
            updateConversationArchivedStatus(it.threadId, true)
            notificationManager.cancel(it.threadId.hashCode())
        }
    }

    private fun updateInBackground(work: () -> Unit) {
        ensureBackgroundThread {
            work()
            runOnUiThread {
                refreshConversations()
                clearSelection()
            }
        }
    }

    private fun openConversation(conversation: Conversation) {
        Intent(this, ThreadActivity::class.java).apply {
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

    private fun openLibrary(destination: LibraryDestination) {
        hideKeyboard()
        val target = when (destination) {
            LibraryDestination.STARRED -> StarredMessagesActivity::class.java
            LibraryDestination.ARCHIVE -> ArchivedConversationsActivity::class.java
            LibraryDestination.SPAM -> BlockedMessagesActivity::class.java
            LibraryDestination.RECYCLE_BIN -> RecycleBinConversationsActivity::class.java
        }
        startActivity(Intent(applicationContext, target))
    }

    private fun refreshSpamBadge() {
        ensureBackgroundThread {
            val unread = getUnreadSpamCounts().values.sum()
            runOnUiThread {
                inbox = inbox.copy(unreadSpam = unread)
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 200L
        private const val DARK_LUMINANCE = 0.5
        private const val INBOX_SELECTION = "inbox_selection"
    }
}

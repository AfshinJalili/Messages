package com.afsh.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.view.HapticFeedbackConstants
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.ComposeView
import com.afsh.messages.helpers.ConversationRepository
import com.afsh.messages.helpers.SearchRepository
import com.afsh.messages.ui.search.SearchUiState
import com.afsh.messages.ui.search.SearchFilter
import com.afsh.messages.ui.search.SEARCH_DEBOUNCE_MS
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.afsh.commons.extensions.applyColorFilter
import com.afsh.commons.extensions.appLaunched
import com.afsh.commons.extensions.appLockManager
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.convertToBitmap
import com.afsh.commons.extensions.copyToClipboard
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.isSystemInDarkMode
import com.afsh.commons.extensions.launchActivityIntent
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.extensions.openNotificationSettings
import com.afsh.commons.extensions.syncGlobalConfig
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.DAY_SECONDS
import com.afsh.commons.helpers.KEY_PHONE
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.helpers.PERMISSION_READ_SMS
import com.afsh.commons.helpers.PERMISSION_SEND_SMS
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.helpers.isQPlus
import com.afsh.commons.R as CommonsR
import com.afsh.messages.App
import com.afsh.messages.BuildConfig
import com.afsh.messages.R
import com.afsh.messages.ui.components.LocalListPane
import com.afsh.messages.helpers.DraftsRepository
import com.afsh.messages.helpers.RecycleBinRepository
import com.afsh.messages.helpers.ListDetail
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.afsh.messages.ui.components.confirm
import com.afsh.messages.ui.components.showRenameDialog
import com.afsh.messages.extensions.clearAllMessagesIfNeeded
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.dialNumber
import com.afsh.messages.helpers.NavBadges
import com.afsh.messages.helpers.unreadMessageCount
import com.afsh.messages.extensions.launchConversationDetails
import com.afsh.messages.helpers.INBOX_FILTER
import com.afsh.messages.helpers.InboxFilter
import com.afsh.messages.helpers.ImportProgress
import com.afsh.messages.helpers.InboxImport
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.ReconcileStatus
import com.afsh.messages.helpers.SEARCHED_MESSAGE_ID
import com.afsh.messages.helpers.SEARCHED_MESSAGE_IS_MMS
import com.afsh.messages.helpers.SWIPE_UNDO_DURATION_MS
import com.afsh.messages.helpers.SpamBackfill
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.testUndoDurationMs
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.blockNumbersOnPhone
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.sortedForInbox
import com.afsh.messages.helpers.swipeAction
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.SearchResult
import com.afsh.messages.ui.AfshSystemBars
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.afshTextScale
import com.afsh.messages.ui.inbox.InboxAction
import com.afsh.messages.ui.inbox.InboxRow
import com.afsh.messages.ui.firstrun.FirstRunEvent
import com.afsh.messages.ui.firstrun.FirstRunScreen
import com.afsh.messages.ui.firstrun.FirstRunStep
import com.afsh.messages.ui.firstrun.firstRunStep
import com.afsh.messages.ui.inbox.InboxScreen
import com.afsh.messages.ui.inbox.InboxUiState
import com.afsh.messages.ui.components.TopDestination
import com.afsh.messages.ui.inbox.LibraryDestination

class MainActivity : SimpleActivity(), UndoDeletion.UndoHost {
    // Task root: launcher open keeps the system motion (AppTheme.Afsh.Root_*).
    override val isTaskRootTheme = true

    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var search by mutableStateOf(SearchUiState())
    private var searchJob: Job? = null
    private val searchRepository by lazy { SearchRepository(this) }
    private val conversationRepository by lazy { ConversationRepository.from(this) }
    private var inboxConversations = listOf<Conversation>()
    private var roomSnapshotReceived = false
    private var splashReady = false
    private var resumedBefore = false
    // 73: the last provider refresh failed; cleared by the next one that succeeds.
    private var refreshFailed = false
    private var importProgress: ImportProgress? = null
    private var importObserved = false
    private var inboxFilter = InboxFilter.ALL
    private var drafts: Map<Long, String> = emptyMap()
    private val pendingSwipes = mutableListOf<PendingSwipe>()
    // Committed archives stay hidden until Room stops listing them, or a refresh would bring them back.
    private val archiving = mutableSetOf<Long>()
    private var undoJob: Job? = null
    private var undoSettle: (() -> Unit)? = null

    private var inbox by mutableStateOf(InboxUiState())
    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    private var topDestination by mutableStateOf(TopDestination.INBOX)
    // 32 or 33 while the app cannot work yet; null once the inbox can load.
    private var firstRun by mutableStateOf<FirstRunStep?>(null)
    @VisibleForTesting
    internal val snackbarHost = SnackbarHostState()
    // Constructed here so Activity Result contracts register before onCreate finishes.
    private val settings = com.afsh.messages.ui.settings.SettingsController(this, snackbarHost, ::refreshInboxTheme)

    private val root by lazy { ComposeView(this) }

    @VisibleForTesting
    internal val inboxRows get() = inbox.rows

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !splashReady }
        // Same theme sync SplashActivity used to do before handing off to MainActivity.
        syncGlobalConfig {
            baseConfig.apply {
                if (isSystemThemeEnabled) {
                    val isUsingSystemDarkTheme = isSystemInDarkMode()
                    textColor = resources.getColor(
                        if (isUsingSystemDarkTheme) CommonsR.color.m3_on_surface_dark
                        else CommonsR.color.m3_on_surface,
                        theme
                    )
                    backgroundColor = resources.getColor(
                        if (isUsingSystemDarkTheme) CommonsR.color.m3_surface_dark
                        else CommonsR.color.m3_surface,
                        theme
                    )
                }
            }
        }
        super.onCreate(savedInstanceState)
        setContentView(root)
        inboxFilter = InboxFilter.entries.getOrNull(savedInstanceState?.getInt(INBOX_FILTER) ?: 0) ?: InboxFilter.ALL
        savedInstanceState?.getLongArray(INBOX_SELECTION)?.let { inbox = inbox.copy(selected = it.toSet()) }
        appLaunched(BuildConfig.APPLICATION_ID)
        topDestination = savedInstanceState?.topDestination()
            ?: intent.topDestination()
            ?: TopDestination.INBOX
        settings.onCreate(savedInstanceState)
        search = SearchUiState(
            open = savedInstanceState?.getBoolean(SEARCH_OPEN) ?: false,
            query = savedInstanceState?.getString(SEARCH_QUERY).orEmpty(),
            filter = SearchFilter.entries.getOrNull(savedInstanceState?.getInt(SEARCH_FILTER) ?: 0) ?: SearchFilter.ALL,
            recent = config.recentSearches,
        )
        if (search.open) loadSearchResults()
        refreshInboxTheme()
        root.setContent {
            // A split starts or ends with a configuration change, so this is read again then.
            val listPane = remember(LocalConfiguration.current) { ListDetail.isPane(this) }
            CompositionLocalProvider(LocalListPane provides listPane) {
                AfshTheme(dark = darkTheme, textScale = textScale) {
                    AfshSystemBars(window)
                    val step = firstRun
                    if (step != null) FirstRunScreen(step, ::onFirstRun) else InboxScreen(
                        state = inbox,
                        snackbarHostState = snackbarHost,
                        onOpen = { openConversation(it.conversation) },
                        onToggleSelection = ::toggleSelection,
                        onSelectAll = { inbox = inbox.copy(selected = inbox.rows.map { it.threadId }.toSet()) },
                        onClearSelection = ::clearSelection,
                        onAction = ::handleAction,
                        onSwipe = ::handleSwipe,
                        onFilter = ::setFilter,
                        onSearch = ::openSearch,
                        onNewMessage = ::launchNewConversation,
                        onLibrary = ::openLibrary,
                        selectedTab = topDestination,
                        onSelectTab = ::selectTab,
                        settingsContent = { settings.Content() },
                        search = search,
                        onSearchQuery = ::searchTextChanged,
                        onSearchFilter = ::setSearchFilter,
                        onSearchResult = ::openSearchResult,
                        onSearchBack = ::closeSearch,
                        onSearchRetry = ::loadSearchResults,
                        onRetryRefresh = { InboxRepository.retry(this) },
                    )
                }
            }
        }

        setupEdgeToEdge()
        // The bottom bar paints its own surface; a system scrim on top would double it.
        if (isQPlus()) window.isNavigationBarContrastEnforced = false

        purgeRecycleBinIfDue()
        clearAllMessagesIfNeeded {
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInboxTheme()
        settings.onResume()
        if (search.open) loadSearchResults()
        refreshSpamBadge()
        refreshDrafts()
        publish()
        checkShortcut()
        // The first resume follows onCreate, whose initMessenger already reconciles and then
        // refreshes the counts; a second full run right behind it only delayed the first screen.
        if (resumedBefore) {
            // Debounced: give ThreadActivity read flushes time to reach Room before reconciling.
            InboxRepository.refreshInbox(this)
        }
        resumedBefore = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SEARCH_OPEN, search.open)
        outState.putString(SEARCH_QUERY, search.query)
        outState.putInt(SEARCH_FILTER, search.filter.ordinal)
        outState.putString(TOP_DESTINATION, topDestination.name)
        settings.onSaveInstanceState(outState)
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
        searchJob?.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.topDestination()?.let(::selectTab)
    }

    override fun onBackPressedCompat(): Boolean {
        if (topDestination == TopDestination.SETTINGS && settings.handleBack()) return true
        if (topDestination != TopDestination.INBOX && !search.open) {
            selectTab(TopDestination.INBOX)
            return true
        }
        return when {
            search.open -> {
                closeSearch()
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

    /** The AFSH palette follows the app's light/dark choice, not the Commons accent. */
    private fun refreshInboxTheme() {
        darkTheme = afshDark()
        textScale = afshTextScale()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // Declining Android's prompt leaves 32 up, so the person can read why and try again.
        if (requestCode == MAKE_DEFAULT_APP_REQUEST && isDefaultSmsApp()) {
            firstRun = FirstRunStep.PERMISSIONS
            // DefaultSmsChangedReceiver records this too, but its broadcast can land after onResume's
            // reconcile has read the flag. The next reconcile from here runs forced.
            InboxRepository.requestForceReconcile(this)
        }
    }

    /** 32 until the app is the SMS app; 33 while SMS access is missing; otherwise straight to the inbox. */
    private fun loadMessages() {
        if (isQPlus() && getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_SMS) != true) {
            toast(com.afsh.commons.R.string.unknown_error_occurred)
            finish()
            return
        }
        firstRun = firstRunStep(isDefaultSmsApp(), hasSmsPermissions())
        if (firstRun != null) splashReady = true
        if (firstRun == null) askPermissions()
    }

    private fun hasSmsPermissions(): Boolean = hasPermission(PERMISSION_READ_SMS) && hasPermission(PERMISSION_SEND_SMS)

    private fun isDefaultSmsApp(): Boolean = if (isQPlus()) {
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
    } else {
        Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

    private fun onFirstRun(event: FirstRunEvent) {
        when (event) {
            FirstRunEvent.BecomeDefault -> requestDefaultSmsApp()
            FirstRunEvent.Continue -> askPermissions()
            FirstRunEvent.Back -> if (firstRun == FirstRunStep.PERMISSIONS && !isDefaultSmsApp()) {
                firstRun = FirstRunStep.DEFAULT_APP
            } else {
                finish()
            }
        }
    }

    private fun requestDefaultSmsApp() {
        val intent = if (isQPlus()) {
            getSystemService(RoleManager::class.java)!!.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
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
                                    confirm(
                                        title = getString(R.string.notifications_off_title),
                                        explanation = getString(R.string.notifications_off_text),
                                        confirmLabel = getString(R.string.open_settings),
                                    ) { openNotificationSettings() }
                                }
                            }

                            firstRun = null
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
        // Contacts permission may have just been granted; the inbox's contact cache needs its observer.
        (application as App).watchContacts()
        InboxRepository.setReconcileListener { status ->
            when (status) {
                ReconcileStatus.FAILED -> refreshFailed = true
                ReconcileStatus.DONE -> refreshFailed = false
                ReconcileStatus.RUNNING -> Unit
            }
            if (!isDestroyed && !isFinishing) {
                publish()
            }
        }
        conversationsDB.observeNonArchivedWithLatestSnippet().observe(this) { rows ->
            if (isDestroyed || isFinishing) return@observe
            roomSnapshotReceived = true
            splashReady = true
            inboxConversations = rows.map { it.toConversation() }
            archiving.retainAll(inboxConversations.map { it.threadId }.toSet())
            publish()
            // Marker changes also invalidate this query, so new spam updates the badge here.
            refreshSpamBadge()
        }
        // Paint Room first; full Telephony reconcile only when the cheap digest says it is dirty.
        InboxRepository.scheduleLaunchReconcile(this)
        SpamBackfill.run(this)
        if (!importObserved) {
            importObserved = true
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    InboxImport.progress.collect { progress ->
                        val finished = importProgress != null && progress == null
                        importProgress = progress
                        search = search.copy(importProgress = progress)
                        publish()
                        // Search reads Room, so results found mid-import may be missing threads.
                        if (finished && search.open) loadSearchResults()
                    }
                }
            }
        }
    }

    /** Rebuilds the screen state from the Room snapshot plus everything only this activity knows. */
    private fun publish() {
        val hidden = pendingSwipes.map { it.conversation.threadId }.toSet() + UndoDeletion.threads + archiving
        val present = inboxConversations.filter { it.threadId !in hidden }
        val pinned = config.pinnedConversations
        val muted = config.mutedConversations
        val rows = present
            .filter { inboxFilter.matches(it) }
            .sortedForInbox(pinned)
            .map {
                InboxRow(
                    conversation = it,
                    draft = drafts[it.threadId],
                    pinned = it.threadId.toString() in pinned,
                    muted = it.threadId.toString() in muted,
                )
            }
        val visibleIds = rows.map { it.threadId }.toSet()
        inbox = inbox.copy(
            rows = rows,
            filter = inboxFilter,
            unreadMessages = present.unreadMessageCount(),
            inboxCountShown = config.showInboxUnreadCount,
            // Only the first import: Room has answered and still has no rows. Do not block on Telephony.
            loading = roomSnapshotReceived &&
                inboxConversations.isEmpty() &&
                importProgress == null &&
                !config.messagesImported,
            refreshFailed = refreshFailed,
            importProgress = importProgress,
            // A selection must not act on rows the current filter or an undo window hides. Before the
            // first snapshot arrives, keep a selection restored from saved state.
            selected = if (inboxConversations.isEmpty()) inbox.selected else inbox.selected intersect visibleIds,
            archiveAvailable = config.isArchiveAvailable,
            recycleBinAvailable = config.useRecycleBin,
            swipeLeft = config.swipeAction(towardsRight = false),
            swipeRight = config.swipeAction(towardsRight = true),
            spamNumbers = config.spamNumbers,
            allowedNumbers = config.allowedNumbers,
        )
        NavBadges.publish(inbox.navCounts)
    }

    /** Once a day at most, erase messages that have sat in the recycle bin past its retention. */
    private fun purgeRecycleBinIfDue() {
        val now = System.currentTimeMillis()
        if (!config.useRecycleBin || config.lastRecycleBinCheck >= now - DAY_SECONDS * 1000) return
        config.lastRecycleBinCheck = now
        ensureBackgroundThread { RecycleBinRepository.from(this).purgeExpired() }
    }

    private fun refreshDrafts() {
        ensureBackgroundThread {
            val fresh = DraftsRepository.from(this).getAll()
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

    @VisibleForTesting
    internal fun handleSwipe(row: InboxRow, action: SwipeAction) {
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
        showMuteUndo(listOf(conversation), muted)
    }

    private fun showMuteUndo(conversations: List<Conversation>, muted: Boolean) {
        val count = conversations.size.coerceAtLeast(1)
        val message = resources.getQuantityString(
            if (muted) R.plurals.conversation_muted else R.plurals.conversation_unmuted,
            count,
            count,
        )
        showUndo(message, onUndo = { setMuted(conversations, !muted) })
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
        showUndo(getString(message), onUndo, onTimeout)
    }

    private fun showUndo(message: String, onUndo: () -> Unit, onTimeout: () -> Unit = {}) {
        launchUndo(message, undoTimeoutMs(), onUndo, onTimeout)
    }

    /**
     * Deletion Undo from [UndoDeletion]. Its own timer normally commits; if another action's Undo
     * replaces this snackbar, the deletion commits then instead of lingering without an Undo.
     */
    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        val job = launchUndo(getString(message), durationMs.toLong(), onUndo, onTimeout = { UndoDeletion.commitNow(this) })
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
    private fun launchUndo(message: String, durationMs: Long, onUndo: () -> Unit, onTimeout: () -> Unit): Job {
        // Settle and cancel the previous offer before showing this one. Only dismissing it let a second
        // showSnackbar queue behind the first, so Undo "reshowed" a snackbar instead of undoing.
        undoJob?.cancel()
        undoSettle?.invoke()
        undoSettle = onTimeout
        return lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs) {
                snackbarHost.showSnackbar(
                    message = message,
                    actionLabel = getString(com.afsh.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            undoSettle = null
            if (result == SnackbarResult.ActionPerformed) onUndo() else onTimeout()
        }.also { undoJob = it }
    }

    /** Honours the system "time to take action" accessibility setting on API 29+. */
    private fun undoTimeoutMs(): Long {
        // Allow tests to override the undo duration to avoid racing the window on large inboxes
        val base = testUndoDurationMs ?: SWIPE_UNDO_DURATION_MS
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
                conversationRepository.setArchived(threadId, true)
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
        MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
        UndoDeletion.offer(
            this,
            undo = {
                UndoDeletion.threads.removeAll(ids)
                publish()
            },
            commit = {
                ids.forEach {
                    conversationRepository.delete(it)
                    notificationManager.cancel(it.hashCode())
                }
            },
            completed = {
                UndoDeletion.threads.removeAll(ids)
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
            },
        )
    }

    private fun handleAction(action: InboxAction) {
        val selected = inbox.selectedRows.map { it.conversation }
        val first = selected.firstOrNull() ?: return
        when (action) {
            InboxAction.ARCHIVE -> confirm(
                title = resources.getQuantityString(R.plurals.archive_conversations_title, selected.size, selected.size),
                explanation = getString(R.string.archive_conversations_text),
                confirmLabel = getString(R.string.archive),
            ) { archive(selected) }

            InboxAction.DELETE -> confirmDelete(selected)

            InboxAction.MARK_READ -> updateInBackground { selected.filter { !it.read }.forEach { conversationRepository.markThreadRead(it.threadId) } }
            InboxAction.MARK_UNREAD -> updateInBackground { selected.filter { it.read }.forEach { conversationRepository.markThreadUnread(it.threadId) } }
            InboxAction.MUTE, InboxAction.UNMUTE -> {
                val muted = action == InboxAction.MUTE
                setMuted(selected, muted = muted)
                clearSelection()
                showMuteUndo(selected, muted)
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

            InboxAction.RENAME -> showRenameDialog(first) { title ->
                updateInBackground { conversationRepository.rename(first, newTitle = title) }
            }

            InboxAction.DETAILS -> launchConversationDetails(first.threadId)
            InboxAction.ALWAYS_SPAM -> {
                val count = selected.size.coerceAtLeast(1)
                val title = if (count == 1) {
                    resources.getQuantityString(R.plurals.always_spam_title, 1, selected.first().title)
                } else {
                    resources.getQuantityString(R.plurals.always_spam_title, count, count)
                }
                confirm(
                    title = title,
                    explanation = resources.getQuantityString(R.plurals.always_spam_text, count),
                    confirmLabel = resources.getQuantityString(R.plurals.always_spam, count),
                ) {
                    selected.forEach { config.addSpamNumber(it.phoneNumber) }
                    clearSelection()
                }
            }

            InboxAction.NEVER_SPAM -> {
                val count = selected.size.coerceAtLeast(1)
                val title = if (count == 1) {
                    resources.getQuantityString(R.plurals.never_spam_title, 1, selected.first().title)
                } else {
                    resources.getQuantityString(R.plurals.never_spam_title, count, count)
                }
                confirm(
                    title = title,
                    explanation = resources.getQuantityString(R.plurals.never_spam_text, count),
                    confirmLabel = resources.getQuantityString(R.plurals.never_spam, count),
                ) {
                    selected.forEach { config.addAllowedNumber(it.phoneNumber) }
                    clearSelection()
                }
            }

            InboxAction.BLOCK_ON_PHONE -> {
                val count = selected.size.coerceAtLeast(1)
                val title = if (count == 1) {
                    resources.getQuantityString(R.plurals.block_on_phone_title, 1, selected.first().title)
                } else {
                    resources.getQuantityString(R.plurals.block_on_phone_title, count, count)
                }
                confirm(
                    title = title,
                    explanation = resources.getQuantityString(R.plurals.block_on_phone_text, count),
                    confirmLabel = resources.getQuantityString(R.plurals.block_on_phone, count),
                    destructive = true,
                ) {
                    blockNumbersOnPhone(selected.map { it.phoneNumber })
                    clearSelection()
                }
            }
        }
    }

    /** 82: into the recycle bin when it is on, otherwise for good; either way with Undo after. */
    private fun confirmDelete(conversations: List<Conversation>) {
        val count = conversations.size
        val toRecycleBin = config.useRecycleBin
        confirm(
            title = resources.getQuantityString(R.plurals.delete_conversations_title, count, count),
            explanation = resources.getQuantityString(
                if (toRecycleBin) R.plurals.delete_conversations_to_bin_text else R.plurals.delete_conversations_forever_text,
                count,
            ),
            confirmLabel = getString(if (toRecycleBin) com.afsh.commons.R.string.delete else R.string.delete_forever),
            destructive = !toRecycleBin,
        ) { deleteWithUndo(conversations) }
    }

    private fun archive(conversations: List<Conversation>) = updateInBackground {
        conversations.forEach {
            conversationRepository.setArchived(it.threadId, true)
            notificationManager.cancel(it.threadId.hashCode())
        }
    }

    private fun updateInBackground(work: () -> Unit) {
        ensureBackgroundThread {
            work()
            runOnUiThread {
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
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
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to update dynamic shortcuts", e)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, R.drawable.shortcut_new_conversation)

        (drawable as LayerDrawable).findDrawableByLayerId(
            R.id.shortcut_new_conversation_background
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

    private fun openSearch() {
        search = SearchUiState(open = true, recent = config.recentSearches, importProgress = importProgress)
    }

    private fun closeSearch() {
        if (!search.open) return
        searchJob?.cancel()
        search = SearchUiState(importProgress = importProgress)
        hideKeyboard()
    }

    private fun setSearchFilter(filter: SearchFilter) {
        search = search.copy(filter = filter)
        if (search.entry) loadSearchResults()
    }

    private fun searchTextChanged(query: String) {
        searchJob?.cancel()
        val filter = if (query.isBlank()) SearchFilter.ALL else search.filter
        search = search.copy(
            query = query,
            filter = filter,
            matches = emptyList(),
            failed = false,
            loading = query.isNotBlank(),
        )
        loadSearchResults()
    }

    private fun loadSearchResults() {
        searchJob?.cancel()
        if (search.showingHistory) {
            search = search.copy(matches = emptyList(), loading = false, failed = false)
            return
        }
        val query = search.query
        val includeMessages = query.isNotBlank() || search.filter != SearchFilter.PEOPLE
        search = search.copy(loading = true, failed = false)
        searchJob = lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            try {
                val matches = searchRepository.search(query, includeMessageResults = includeMessages)
                search = search.copy(matches = matches, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                search = search.copy(loading = false, failed = true)
            }
        }
    }

    private fun openSearchResult(result: SearchResult) {
        // only queries that led somewhere are worth offering again
        if (search.query.isNotBlank()) {
            config.addRecentSearch(search.query.trim())
            search = search.copy(recent = config.recentSearches)
        }
        hideKeyboard()
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, result.threadId)
            putExtra(THREAD_TITLE, result.title)
            putExtra(SEARCHED_MESSAGE_ID, result.messageId)
            putExtra(SEARCHED_MESSAGE_IS_MMS, result.isMms)
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
            val unread = conversationRepository.unreadSpamCounts().values.sum()
            runOnUiThread {
                inbox = inbox.copy(unreadSpam = unread)
                NavBadges.publish(inbox.navCounts)
            }
        }
    }

    private fun selectTab(destination: TopDestination) {
        closeSearch()
        hideKeyboard()
        if (destination != TopDestination.SETTINGS) settings.resetToOverview()
        topDestination = destination
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val TOP_DESTINATION = "top_destination"
        private const val SEARCH_OPEN = "search_open"
        private const val SEARCH_QUERY = "search_query"
        private const val SEARCH_FILTER = "search_filter"
        private const val INBOX_SELECTION = "inbox_selection"

        /** Brings the existing inbox task forward on [destination] instead of stacking another one. */
        fun topLevelIntent(context: Context, destination: TopDestination): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(TOP_DESTINATION, destination.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun Intent.topDestination(): TopDestination? = when {
            action == Intent.ACTION_APPLICATION_PREFERENCES -> TopDestination.SETTINGS
            else -> getStringExtra(TOP_DESTINATION)?.let { name ->
                TopDestination.entries.firstOrNull { it.name == name }
            }
        }

        private fun Bundle.topDestination() = getString(TOP_DESTINATION)?.let { name ->
            TopDestination.entries.firstOrNull { it.name == name }
        }
    }
}

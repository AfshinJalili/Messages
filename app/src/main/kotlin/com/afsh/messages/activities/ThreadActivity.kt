package com.afsh.messages.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.telephony.SmsMessage
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afsh.messages.BuildConfig
import com.afsh.messages.R
import com.afsh.messages.extensions.clearExpiredScheduledMessages
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.createTemporaryThread
import com.afsh.messages.extensions.deleteConversation
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.dialNumber
import com.afsh.messages.extensions.filterNotInByKey
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.fileSize
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.extensions.isGifMimeType
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.extensions.isVCardMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.extensions.launchConversationDetails
import com.afsh.messages.extensions.launchViewIntent
import com.afsh.messages.extensions.markMessageRead
import com.afsh.messages.extensions.markThreadMessagesRead
import com.afsh.messages.extensions.markThreadMessagesUnread
import com.afsh.messages.extensions.messagesDB
import com.afsh.messages.extensions.removeDiacriticsIfNeeded
import com.afsh.messages.extensions.renameConversation
import com.afsh.messages.extensions.toPickerPerson
import com.afsh.messages.extensions.updateConversationArchivedStatus
import com.afsh.messages.extensions.updateLastConversationMessage
import com.afsh.messages.extensions.updateScheduledMessagesThreadId
import com.afsh.messages.helpers.CAPTURE_AUDIO_INTENT
import com.afsh.messages.helpers.CAPTURE_PHOTO_INTENT
import com.afsh.messages.helpers.CAPTURE_VIDEO_INTENT
import com.afsh.messages.helpers.EXTRA_VCARD_URI
import com.afsh.messages.helpers.FILE_SIZE_NONE
import com.afsh.messages.helpers.IS_LAUNCHED_FROM_SHORTCUT
import com.afsh.messages.helpers.IS_RECYCLE_BIN
import com.afsh.messages.helpers.ImageCompressor
import com.afsh.messages.helpers.ConversationPreview
import com.afsh.messages.helpers.DraftsRepository
import com.afsh.messages.helpers.RecycleBinRepository
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.MessageSendCoordinator
import com.afsh.messages.helpers.ParticipantResolver
import com.afsh.messages.helpers.MESSAGES_LIMIT
import com.afsh.messages.helpers.OPEN_SPAM
import com.afsh.messages.helpers.OPEN_THREAD_SEARCH
import com.afsh.messages.helpers.PICK_CONTACT_INTENT
import com.afsh.messages.helpers.PICK_DOCUMENT_INTENT
import com.afsh.messages.helpers.PICK_PHOTO_INTENT
import com.afsh.messages.helpers.PICK_SAVE_DIR_INTENT
import com.afsh.messages.helpers.PICK_SAVE_FILE_INTENT
import com.afsh.messages.helpers.PICK_VIDEO_INTENT
import com.afsh.messages.helpers.SEARCHED_MESSAGE_ID
import com.afsh.messages.helpers.SEARCHED_MESSAGE_IS_MMS
import com.afsh.messages.helpers.TelephonyReader
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URI
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URIS
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.helpers.THREAD_TEXT
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.affectsThread
import com.afsh.messages.helpers.blockNumbersOnPhone
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.helpers.contactAsText
import com.afsh.messages.helpers.generateRandomId
import com.afsh.messages.helpers.insertBlock
import com.afsh.messages.messaging.cancelScheduleSendPendingIntent
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.AfshSystemBars
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.afshTextScale
import com.afsh.messages.ui.components.confirm
import com.afsh.messages.ui.components.showAfshDialog
import com.afsh.messages.ui.components.AfshSheetItem
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.inform
import com.afsh.messages.ui.components.showRenameDialog
import com.afsh.messages.ui.newmessage.NewMessageUiState
import com.afsh.messages.ui.newmessage.Recipient
import com.afsh.messages.ui.schedule.ScheduleTarget
import com.afsh.messages.ui.thread.AttachOption
import com.afsh.messages.ui.thread.ComposerEvent
import com.afsh.messages.ui.thread.InitialScroll
import com.afsh.messages.ui.thread.MessageAction
import com.afsh.messages.ui.thread.PeopleEvent
import com.afsh.messages.ui.thread.ThreadEvent
import com.afsh.messages.ui.thread.ThreadFacts
import com.afsh.messages.ui.thread.ThreadMenuAction
import com.afsh.messages.ui.thread.ThreadMessages
import com.afsh.messages.ui.thread.ThreadRoute
import com.afsh.messages.ui.thread.ThreadStateHolder
import com.afsh.messages.ui.thread.cardSource
import com.afsh.messages.ui.thread.landing
import com.afsh.messages.ui.thread.mediaItems
import com.afsh.messages.ui.thread.messageFacts
import com.afsh.messages.ui.thread.senderLine
import com.afsh.messages.ui.thread.starKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.afsh.commons.extensions.copyToClipboard
import com.afsh.commons.extensions.formatDate
import com.afsh.commons.extensions.getFilenameFromUri
import com.afsh.commons.extensions.getMyFileUri
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.launchActivityIntent
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.extensions.openRequestExactAlarmSettings
import com.afsh.commons.extensions.shareTextIntent
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.ContactsHelper
import com.afsh.commons.helpers.KEY_PHONE
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import org.joda.time.DateTime
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * The conversation screen's host: lifecycle, intents, permissions, and the provider and settings the
 * conversation reads and writes. The screen's state and the logic that needs no Android live in [holder];
 * the loaded messages and what the timeline derives from them live in [data].
 */
class ThreadActivity : SimpleActivity(), UndoDeletion.UndoHost {
    private var threadId = 0L
    @Volatile
    private var isActivityVisible = false
    private var markReadInProgress = false
    private val updateScrollPosition = Runnable {
        if (isActivityVisible && !isDestroyed) {
            markThreadReadIfAtBottom()
        }
    }
    /** Text and attachments from before a recreation; they win over the intent extras. */
    private var restoredText: String? = null
    private var restoredAttachments: List<Uri> = emptyList()
    private val imageCompressor by lazy { ImageCompressor(this) }
    private val drafts by lazy { DraftsRepository.from(this) }
    private val recycleBin by lazy { RecycleBinRepository.from(this) }
    private val telephony by lazy { TelephonyReader(this) }

    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    @VisibleForTesting
    internal val snackbarHost = SnackbarHostState()
    @VisibleForTesting
    internal val timelineItems get() = holder.ui.items
    @VisibleForTesting
    internal val timelineSettledAtBottom get() = holder.listAtBottom && !holder.listScrolling
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            holder.back()
        }
    }

    private val holder = ThreadStateHolder(HolderHost())
    private val data = ThreadMessages(
        isManuallyUnread = { config.isManuallyUnread(threadId, it.id, it.isMMS) },
        isHidden = { it.getStableId() in UndoDeletion.messages || threadId in UndoDeletion.threads },
    )

    private var refreshPendingWhileStopped = false
    private var threadItems = ArrayList<ThreadItem>()
    private var conversation: Conversation? = null
    private val people by lazy { ParticipantResolver.from(this) }
    private val sender by lazy { MessageSendCoordinator.from(this, people, SendHost()) }
    private var addContactFieldReady = false
    private var capturedImageUri: Uri? = null
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false
    private var openSpam = false

    private val sendLater = registerForActivityResult(SendLaterActivity.Contract()) { millis ->
        if (millis != null) onSendTimeChosen(millis)
    }
    private val editScheduled = registerForActivityResult(ScheduledMessageActivity.Contract()) { result ->
        result?.let { (messageId, decision) -> onScheduledDecision(messageId, decision) }
    }

    /** The whole screen is one Compose tree; this view also posts the scroll-position checks. */
    private val root by lazy { ComposeView(this) }

    /** What [holder] cannot do alone. */
    private inner class HolderHost : ThreadStateHolder.Host {
        override fun onEvent(event: ThreadEvent) = onThreadEvent(event)
        override fun onComposerEvent(event: ComposerEvent) = onComposerAction(event)

        override fun onPeopleEvent(event: PeopleEvent) {
            when (event) {
                is PeopleEvent.Add -> addPerson(event.recipient)
                is PeopleEvent.Remove -> removePerson(event.recipient)
                PeopleEvent.Done -> applyManagePeople()
                PeopleEvent.AllowContacts -> allowContacts()
            }
        }

        override fun hideKeyboard() = this@ThreadActivity.hideKeyboard()

        override fun viewportChanged() {
            root.removeCallbacks(updateScrollPosition)
            root.post(updateScrollPosition)
        }

        override fun backEnabledChanged(enabled: Boolean) {
            backCallback.isEnabled = enabled
        }

        override fun simChosen(subscriptionId: Int) = people.chooseSim(subscriptionId)

        override fun isMms(text: String, hasAttachments: Boolean) = sender.isMms(text, hasAttachments)

        override fun smsLength(text: String): Pair<Int, Int>? {
            if (!config.showCharacterCounter) return null
            val length = SmsMessage.calculateLength(text.removeDiacriticsIfNeeded(config.useSimpleCharacters), false)
            return length[0] to length[2]
        }
    }

    /** What [sender] needs from the conversation screen. */
    private inner class SendHost : MessageSendCoordinator.Host {
        override val threadId get() = this@ThreadActivity.threadId
        override val conversation get() = this@ThreadActivity.conversation
        override fun loaded(): List<Message> = data.list

        override fun moveToThread(threadId: Long) {
            this@ThreadActivity.threadId = threadId
            syncActiveConversationThread()
        }

        override fun showStored(message: Message) {
            data.upsert(message)
            val newItems = getThreadItems()
            runOnUiThread {
                threadItems = newItems
                publishItems(newItems)
                holder.scrollToBottom()
                if (sender.needsRefresh) {
                    ensureBackgroundThread { reloadMessages() }
                }
            }
        }

        override fun reload() = reloadMessages()
        override fun sent() = holder.clearMessage()

        override fun scheduled() = runOnUiThread {
            holder.clearMessage()
            holder.clearSchedule()
        }

        override fun retryFailed(message: Message) = holder.retryFailed(message)

        override fun timeInPast(retryMillis: Long?) {
            toast(R.string.must_pick_time_in_the_future)
            if (retryMillis != null) launchScheduleSendDialog(retryMillis)
        }

        override fun askForExactAlarms() = confirm(
            title = getString(com.afsh.commons.R.string.permission_required),
            explanation = getString(com.afsh.commons.R.string.allow_alarm_scheduled_messages),
            confirmLabel = getString(R.string.open_settings),
        ) {
            openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
        }

        override fun attachmentsSaved() = toast(com.afsh.commons.R.string.file_saved)
        override fun failed(error: Exception) = showErrorToast(error)
        override fun failed(message: String?) =
            showErrorToast(message ?: getString(com.afsh.commons.R.string.unknown_error_occurred))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredText = savedInstanceState?.getString(SAVED_COMPOSER_TEXT)
        restoredAttachments = savedInstanceState?.getStringArrayList(SAVED_COMPOSER_ATTACHMENTS).orEmpty().map { it.toUri() }
        data.newOnOpen = savedInstanceState?.getLongArray(SAVED_NEW_ON_OPEN)?.toSet()
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        refreshAfshTheme()
        root.setContent {
            AfshTheme(dark = darkTheme, textScale = textScale) {
                AfshSystemBars(window)
                ThreadRoute(holder, snackbarHost, sendOnEnter = config.sendOnEnter)
            }
        }
        // Keeps the side padding for display cutouts; the composer pads itself for the keyboard and navigation bar.
        setupEdgeToEdge()

        val extras = intent.extras
        if (extras == null) {
            toast(com.afsh.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let { holder.showTitle(it) }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)
        openSpam = intent.getBooleanExtra(OPEN_SPAM, false)

        observeMessagingEvents()
        loadConversation()
        refreshMenuItems()
    }

    override fun onStart() {
        super.onStart()
        if (refreshPendingWhileStopped) {
            refreshPendingWhileStopped = false
            ensureBackgroundThread { reloadMessages() }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAfshTheme()
        // mute and starring can change on other screens
        refreshMenuItems()
        publishItems()

        isActivityVisible = true
        // Registering active presence also cancels any existing tray notification for this thread
        // (unless locked or viewing Recycle Bin).
        MessagingEvents.onConversationActive(this, threadId, isRecycleBin)
        root.post(updateScrollPosition)

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread { refreshMenuItems() }
            }

            val smsDraft = drafts.get(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread { holder.setText(smsDraft) }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isActivityVisible) root.post(updateScrollPosition)
    }

    override fun onPause() {
        super.onPause()
        saveDraftMessage()
        flushThreadReadState()
        MessagingEvents.publish(MessagingEvent.ConversationsChanged(threadId = threadId, reconcile = false))
        isActivityVisible = false
        MessagingEvents.onConversationInactive(this)
        root.removeCallbacks(updateScrollPosition)
    }

    override fun onStop() {
        super.onStop()
        saveDraftMessage()
    }

    override fun refreshAfterDeletion() {
        if (isRecycleBin) setupCachedMessages {}
    }

    override fun onDestroy() {
        super.onDestroy()
        MessagingEvents.onConversationInactive(this)
        // Clear any pending page load completion to prevent awaits that never resume
        data.pageLoadCompletion?.complete(Unit)
        data.pageLoadCompletion = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_COMPOSER_TEXT, holder.text)
        // Compressed copies live in the cache; the originals are re-added and compressed again.
        outState.putStringArrayList(SAVED_COMPOSER_ATTACHMENTS, ArrayList(holder.attachments.map { it.id }))
        data.newOnOpen?.let { outState.putLongArray(SAVED_NEW_ON_OPEN, it.toLongArray()) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK) return
        val data = resultData?.data

        if (requestCode == CAPTURE_PHOTO_INTENT && capturedImageUri != null) {
            addAttachment(capturedImageUri!!)
        } else if (data != null) {
            when (requestCode) {
                CAPTURE_VIDEO_INTENT,
                PICK_DOCUMENT_INTENT,
                CAPTURE_AUDIO_INTENT,
                PICK_PHOTO_INTENT,
                PICK_VIDEO_INTENT -> addAttachment(data)

                PICK_CONTACT_INTENT -> addContactAsText(data)
                PICK_SAVE_FILE_INTENT -> saveAttachments(resultData)
                PICK_SAVE_DIR_INTENT -> saveAttachments(resultData)
            }
        }
    }

    private fun saveDraftMessage() {
        val draftMessage = holder.text
        val hasAttachments = holder.attachments.isNotEmpty()
        ensureBackgroundThread {
            drafts.store(threadId, draftMessage, hasAttachments)
        }
    }

    private fun refreshAfshTheme() {
        darkTheme = afshDark()
        textScale = afshTextScale()
    }

    // region Screen events

    private fun onThreadEvent(event: ThreadEvent) {
        when (event) {
            ThreadEvent.Back -> onBackPressedDispatcher.onBackPressed()
            ThreadEvent.OpenDetails -> if (conversation != null && !isRecycleBin) launchConversationDetails(threadId)
            ThreadEvent.Dial -> if (people.participants.isNotEmpty()) dialNumber()
            is ThreadEvent.Menu -> if (people.participants.isNotEmpty()) handleMenuItemAction(event.action)
            is ThreadEvent.SearchSubmit -> jumpToNextMatch(event.query)
            is ThreadEvent.Act -> onMessageAction(event.action, event.messages)
            // Until the provider shows it leave the failed state, a second tap would transmit it again.
            is ThreadEvent.Retry -> if (holder.startRetry(event.message)) sender.resend(event.message, holder.sendSubscriptionId)
            is ThreadEvent.OpenAttachment -> openAttachment(event.message, event.attachment)
            is ThreadEvent.CopyText -> copyToClipboard(event.text)
            ThreadEvent.JumpToLatest -> {
                notificationManager.cancel(threadId.hashCode())
                holder.scrollToBottom()
            }
            ThreadEvent.LoadOlder -> loadMoreMessages()
            // The rest is the holder's own.
            else -> Unit
        }
    }

    private fun onComposerAction(event: ComposerEvent) {
        when (event) {
            ComposerEvent.Send -> sendMessage()
            ComposerEvent.Dictate -> launchDictation()
            is ComposerEvent.Attach -> attach(event.option)
            ComposerEvent.Schedule -> launchScheduleSendDialog(holder.scheduledMillis)
            is ComposerEvent.OpenAttachment -> event.attachment.let {
                if (it.mimetype.isVCardMimeType()) {
                    startActivity(Intent(this, VCardViewerActivity::class.java).putExtra(EXTRA_VCARD_URI, it.uri))
                } else {
                    launchViewIntent(it.uri, it.mimetype, it.filename)
                }
            }
            ComposerEvent.ReplyInfo -> inform(getString(R.string.cannot_reply_title), getString(R.string.invalid_short_code_desc))
            // The rest is the holder's own.
            else -> Unit
        }
    }

    private fun handleMenuItemAction(action: ThreadMenuAction) {
        when (action) {
            ThreadMenuAction.SEARCH -> holder.openSearch()
            ThreadMenuAction.ALWAYS_SPAM -> alwaysSpamSender()
            ThreadMenuAction.NEVER_SPAM -> neverSpamSender()
            ThreadMenuAction.BLOCK_ON_PHONE -> blockOnThisPhone()
            ThreadMenuAction.DELETE -> askConfirmDelete()
            ThreadMenuAction.RESTORE -> askConfirmRestoreAll()
            ThreadMenuAction.ARCHIVE -> setArchived(true)
            ThreadMenuAction.UNARCHIVE -> setArchived(false)
            ThreadMenuAction.RENAME -> renameConversation()
            ThreadMenuAction.DETAILS -> launchConversationDetails(threadId)
            ThreadMenuAction.ADD_TO_CONTACT -> addNumberToContact()
            ThreadMenuAction.COPY_NUMBER -> copyNumberToClipboard()
            ThreadMenuAction.ADD_PERSON -> managePeople()
            ThreadMenuAction.MARK_UNREAD -> markAsUnread()
            ThreadMenuAction.MUTE -> setConversationMuted(true)
            ThreadMenuAction.UNMUTE -> setConversationMuted(false)
        }
    }

    private fun onMessageAction(action: MessageAction, selected: List<Message>) {
        if (selected.isEmpty()) return
        val first = selected.first()
        when (action) {
            MessageAction.COPY -> copyMessages(selected)
            MessageAction.FORWARD -> forwardMessage(first)
            MessageAction.STAR, MessageAction.UNSTAR -> {
                selected.forEach { config.setMessageStarred(it.id, it.isMMS, action == MessageAction.STAR) }
                holder.clearSelection()
                publishItems()
            }
            MessageAction.DELETE -> confirmDeleteMessages(selected, isRecycleBin) { toDelete, toRecycleBin ->
                deleteMessages(toDelete, toRecycleBin, false)
            }
            MessageAction.SHARE -> shareTextIntent(first.body)
            MessageAction.SELECT_TEXT -> if (first.body.isNotBlank()) startActivity(SelectTextActivity.intent(this, senderLine(first), first.body))
            MessageAction.DETAILS -> startActivity(MessageDetailsActivity.intent(this, messageFacts(first, people.simLabels)))
            MessageAction.EDIT_SCHEDULED -> editScheduled.launch(
                ScheduledMessageActivity.Request(first.id, ScheduleTarget(first.body, scheduleRecipient(first.subscriptionId)), first.millis()),
            )
            MessageAction.SEND_NOW -> sendScheduledNow(first)
            MessageAction.SAVE_AS -> saveMMS(selected.flatMap { it.attachment?.attachments.orEmpty() })
            MessageAction.NOT_SPAM -> {
                holder.clearSelection()
                askNotSpam(selected)
            }
            MessageAction.RESTORE -> confirmRestoreMessages(selected) { deleteMessages(it, false, true) }
            // The holder selects all itself.
            MessageAction.SELECT_ALL -> Unit
        }
    }

    // endregion

    // region Header and timeline

    /** Tells the holder what the conversation is: the header, its menu and the composer follow from it. */
    private fun refreshMenuItems() {
        val participants = people.participants
        val single = participants.size == 1
        holder.showFacts(
            ThreadFacts(
                title = conversation?.title?.takeIf { it.isNotEmpty() } ?: participants.getThreadTitle(),
                photoUri = conversation?.photoUri?.takeIf { single } ?: participants.singleOrNull()?.photoUri.orEmpty(),
                participantCount = participants.size,
                firstNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value,
                firstName = participants.firstOrNull()?.name,
                hasConversation = conversation != null,
                archived = conversation?.isArchived,
                archiveAvailable = config.isArchiveAvailable,
                hasItems = threadItems.isNotEmpty(),
                isRecycleBin = isRecycleBin,
                senderBlocked = single && config.spamNumbers.containsNumber(participants.getAddresses().first()),
                senderAllowed = single && config.allowedNumbers.containsNumber(participants.getAddresses().first()),
                muted = config.isConversationMuted(threadId),
                specialNumber = people.isSpecialNumber,
                hasSpam = data.list.any { data.isSpam(it) },
                deletePending = threadId in UndoDeletion.threads,
                groupSubtitle = if (participants.size > 1) resources.getQuantityString(R.plurals.thread_people, participants.size, participants.size) else "",
                specialSubtitle = getString(R.string.thread_automated_sender),
            ),
        )
    }

    /** Pushes [items] and everything derived from them to the Compose timeline. Main thread only. */
    private fun publishItems(items: List<ThreadItem> = threadItems) {
        val messageItems = items.filterIsInstance<Message>()
        holder.showItems(
            items = items,
            starred = messageItems.filter { config.isMessageStarred(it.id, it.isMMS) }.mapTo(HashSet()) { it.starKey() },
            spamReasons = data.spamReasons,
            simLabels = people.simLabels,
            empty = data.providerReady && !isRecycleBin && data.isEmpty,
        )
    }

    private fun getThreadItems(): ArrayList<ThreadItem> = if (isFinishing) ArrayList() else data.items()

    private fun setupAdapter() {
        loadSpamReasons()
        threadItems = getThreadItems()

        val items = threadItems
        runOnUiThread {
            refreshMenuItems()
            // Inbound refresh preserves the viewport. Sending has its own explicit scroll.
            publishItems(items)
            // Cached rows can be stale about read state, so the landing position waits for the provider.
            holder.land(data.providerReady || isRecycleBin, ::landingTarget)
        }
    }

    private fun landingTarget(): InitialScroll? {
        if (openSpam && !isRecycleBin) {
            markSpamRead(threadItems.filterIsInstance<Message>().filter { data.isSpam(it) }.map { it.id })
        }
        return landing(isRecycleBin, openSpam, threadItems, data::isSpam)
    }

    /**
     * Deletion Undo from [UndoDeletion], shown in the timeline's snackbar host above the composer.
     * UndoDeletion's own timer commits; this only offers the Undo.
     */
    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        val job = lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs.toLong()) {
                snackbarHost.showSnackbar(
                    message = getString(message),
                    actionLabel = getString(com.afsh.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
        return { job.cancel() }
    }

    // endregion

    // region Opening things

    private fun openAttachment(message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        if (attachment.mimetype.isVCardMimeType()) {
            startActivity(
                Intent(this, VCardViewerActivity::class.java)
                    .putExtra(EXTRA_VCARD_URI, uri)
                    .putExtra(VCardViewerActivity.EXTRA_VCARD_SOURCE, cardSource(message)),
            )
        } else if (attachment.mimetype.isImageMimeType() || attachment.mimetype.isVideoMimeType()) {
            // 45: every photo and video loaded in this conversation, oldest first, opened at [attachment].
            val (items, initial) = mediaItems(data.list, message, attachment, people.simLabels)
            if (initial < 0) {
                launchViewIntent(uri, attachment.mimetype, attachment.filename)
            } else {
                startActivity(MediaViewerActivity.intent(this, items, initial))
            }
        } else {
            launchViewIntent(uri, attachment.mimetype, attachment.filename)
        }
    }

    /** "To Mina Farahani", with "· SIM 1" when the phone has two SIMs. */
    private fun scheduleRecipient(subscriptionId: Int): String {
        val to = getString(R.string.scheduled_to, people.scheduleName(conversation?.title))
        return people.simLabels[subscriptionId]?.let { getString(R.string.detail_pair, to, getString(R.string.message_sim_label, it)) } ?: to
    }

    private fun jumpToNextMatch(query: String) {
        val loaded = data.list.filter { it.body.contains(query, ignoreCase = true) }
        ensureBackgroundThread {
            // the Room cache also holds messages older than the loaded page
            val cached = messagesDB.getMessagesWithText("%$query%").filter { it.threadId == threadId }
            val matches = (loaded + cached).distinctBy { it.getStableId() }.sortedByDescending { it.date }
            runOnUiThread {
                val next = holder.stepSearch(matches)
                if (next == null) {
                    toast(R.string.no_matching_messages)
                } else {
                    jumpToMessage(next.id, next.isMMS)
                }
            }
        }
    }

    // endregion

    // region Loading

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                ensureBackgroundThread {
                    conversation = conversationsDB.getConversationWithThreadId(threadId)
                }
                setupCachedMessages {
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        val searchedIsMms = intent.getBooleanExtra(SEARCHED_MESSAGE_IS_MMS, false)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            jumpToMessage(searchedMessageId, searchedIsMms)
                        }

                        if (intent.getBooleanExtra(OPEN_THREAD_SEARCH, false)) {
                            intent.removeExtra(OPEN_THREAD_SEARCH)
                            holder.openSearch()
                        }
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun setupButtons() {
        val restored = restoredText
        if (restored != null) {
            holder.setText(restored)
            restoredAttachments.forEach { addAttachment(it) }
            return
        }
        intent.getStringExtra(THREAD_TEXT)?.let { holder.setText(it) }
        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
            addAttachment(uri)
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            @Suppress("UNCHECKED_CAST")
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                addAttachment(it)
            }
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            var messages = try {
                if (isRecycleBin) {
                    messagesDB.getThreadMessagesFromRecycleBin(threadId)
                } else {
                    if (config.useRecycleBin) {
                        messagesDB.getNonRecycledThreadMessages(threadId)
                    } else {
                        messagesDB.getThreadMessages(threadId)
                    }
                }.toMutableList() as ArrayList<Message>
            } catch (e: Exception) {
                ArrayList()
            }
            clearExpiredScheduledMessages(threadId, messages)
            messages.removeAll { it.isScheduled && it.millis() < System.currentTimeMillis() }

            messages.sortBy { it.date }
            if (messages.size > MESSAGES_LIMIT) {
                messages = ArrayList(messages.takeLast(MESSAGES_LIMIT))
            }
            data.list = messages

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (data.isEmpty && !people.isSpecialNumber) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    holder.requestFocus()
                }

                refreshMenuItems()
                setupSIMSelector()
                holder.refreshComposer()
                callback()
            }
        }
    }

    private fun setupThread(callback: () -> Unit) {
        if (conversation == null && isLaunchedFromShortcut) {
            if (isTaskRoot) {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
            finish()
            return
        }
        ensureBackgroundThread {
            people.loadPrivateContacts()

            val cachedMessagesCode = ArrayList(data.list).hashCode()
            if (!isRecycleBin) {
                var messages = telephony.getMessages(threadId, includeAllUnread = true)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
                data.list = messages
            }

            val hadParticipants = people.participants.isNotEmpty()
            val hasParticipantWithoutName = people.hasUnnamed
            val messagesUnchanged = try {
                data.list.hashCode() == cachedMessagesCode
            } catch (_: Exception) {
                false
            }

            if (hadParticipants && messagesUnchanged && !hasParticipantWithoutName) {
                data.providerReady = true
                runOnUiThread {
                    if (data.isEmpty) publishItems()
                    holder.land(true, ::landingTarget)
                    callback()
                }
                return@ensureBackgroundThread
            }

            setupParticipants()

            // check if no participant came from a privately stored contact in Simple Contacts
            val replacedPrivateNames = people.applyPrivateNames(data.list)

            if (hadParticipants && messagesUnchanged && !replacedPrivateNames) {
                data.providerReady = true
                runOnUiThread {
                    if (data.isEmpty) publishItems()
                    holder.land(true, ::landingTarget)
                    callback()
                }
                return@ensureBackgroundThread
            }

            if (!people.fallBackTo(intent.getStringExtra(THREAD_TITLE) ?: "", intent.getStringExtra(THREAD_NUMBER))) {
                toast(com.afsh.commons.R.string.unknown_error_occurred)
                finish()
                return@ensureBackgroundThread
            }

            if (!isRecycleBin) {
                data.list.chunked(30).forEach { currentMessages ->
                    messagesDB.insertMessages(*currentMessages.toTypedArray())
                }
            }

            data.providerReady = true
            setupAdapter()
            runOnUiThread {
                refreshMenuItems()
                setupSIMSelector()
                callback()
            }
        }
    }

    private fun setupParticipants() {
        if (people.resolve(threadId, data.list, intent.getStringExtra(THREAD_NUMBER))) {
            runOnUiThread {
                maybeDisableShortCodeReply()
            }
        }
    }

    private fun loadMoreMessages() {
        if (data.isEmpty || data.allFetched || data.loadingOlder) return
        data.loadingOlder = true
        val completion = CompletableDeferred<Unit>()
        data.pageLoadCompletion = completion
        val cutoff = data.list.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            val items = threadItems
            runOnUiThread {
                data.loadingOlder = false
                data.pageLoadCompletion = null
                completion.complete(Unit)
                publishItems(items)
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> =
        data.prependOlder(telephony.getMessages(threadId, cutoff).filterNotInByKey(data.list) { it.getStableId() })

    /**
     * Read state stays blocked until the jump has scrolled ([ThreadEvent.JumpSettled]), and timeline
     * page loads pause, so a page load cannot win the race and drop the jump.
     */
    private fun jumpToMessage(messageId: Long, isMms: Boolean? = null) {
        holder.startJump()
        if (data.contains(messageId, isMms)) {
            holder.scrollToJump(messageId, isMms)
            return
        }

        lifecycleScope.launch {
            // Wait for all in-flight older-page loads to complete before starting our own.
            while (data.pageLoadCompletion != null) {
                data.pageLoadCompletion?.await()
            }
            
            // Claim the load slot synchronously on the main thread to prevent races.
            data.loadingOlder = true
            val completion = CompletableDeferred<Unit>()
            data.pageLoadCompletion = completion
            
            ensureBackgroundThread {
                val items = try {
                    var cutoff = data.oldestDate() ?: Int.MAX_VALUE
                    var found = data.contains(messageId, isMms)
                    
                    // Load older pages until we find the message or reach the start of the thread.
                    // Bound to MAX_JUMP_LOOPS to prevent infinite loops if the message doesn't exist.
                    var loops = 0
                    while (!found && !data.allFetched && loops < MAX_JUMP_LOOPS) {
                        val fetched = fetchOlderMessages(cutoff)
                        if (fetched.isEmpty()) break
                        cutoff = data.list.first().date
                        found = data.contains(messageId, isMms)
                        loops++
                    }

                    threadItems = getThreadItems()
                    threadItems
                } catch (e: Exception) {
                    // Without this, page loads stay blocked for the rest of the visit.
                    runOnUiThread {
                        data.loadingOlder = false
                        data.pageLoadCompletion = null
                        completion.complete(Unit)
                        holder.jumpFailed()
                    }
                    return@ensureBackgroundThread
                }
                runOnUiThread {
                    data.loadingOlder = false
                    data.pageLoadCompletion = null
                    completion.complete(Unit)
                    publishItems(items)
                    holder.scrollToJump(messageId, isMms)
                }
            }
        }
    }

    private fun observeMessagingEvents() {
        lifecycleScope.launch {
            MessagingEvents.events.collect { event ->
                if (!event.affectsThread(threadId)) return@collect
                // Our own markThreadReadIfAtBottom already updated local read state.
                if (event is MessagingEvent.ReadStateChanged && isActivityVisible && event.read && !data.hasUnread()) {
                    return@collect
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    ensureBackgroundThread { reloadMessages() }
                } else {
                    refreshPendingWhileStopped = true
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessagingEvents.inThreadFailures.collect { failure ->
                    if (failure.threadId == threadId && failure.reason.isNotEmpty()) {
                        snackbarHost.showSnackbar(failure.reason)
                    }
                }
            }
        }
    }

    private fun syncActiveConversationThread() {
        runOnUiThread {
            if (isActivityVisible && !isDestroyed) {
                MessagingEvents.onConversationThreadUpdated(this, threadId, isRecycleBin)
            }
        }
    }

    private fun reloadMessages() {
        if (isRecycleBin) {
            return
        }

        sender.reloaded()
        data.allFetched = false

        val current = data.list
        val lastMaxId = current.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = getThreadId(people.addresses.toSet())
        val newMessages = telephony.getMessages(newThreadId, includeScheduledMessages = false,
            includeAllUnread = true, oldestLoadedDate = current.firstOrNull { !it.isScheduled }?.date)
        if (current.isNotEmpty() && current.all { it.isScheduled } && newMessages.isNotEmpty()) {
            // update scheduled messages with real thread id
            threadId = newThreadId
            syncActiveConversationThread()
            updateScheduledMessagesThreadId(
                messages = current.filter { it.threadId != threadId },
                newThreadId = threadId
            )
        }

        data.list = newMessages.apply {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
                .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            addAll(scheduledMessages)
            if (config.useRecycleBin) {
                val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId).toSet()
                removeAll(recycledMessages)
            }
        }

        data.list.filter { !it.isScheduled && !it.isReceivedMessage() && it.id > lastMaxId }
            .forEach { latestMessage ->
                messagesDB.insertOrIgnore(latestMessage)
            }

        setupAdapter()
        runOnUiThread {
            setupSIMSelector()
        }
    }

    private fun loadSpamReasons() {
        data.spamReasons = messagesDB.getThreadSpamMarkers(threadId).associate { it.id to it.reason }
    }

    // endregion

    // region Read state

    private fun flushThreadReadState() {
        // Until the landing position settles, "at the bottom" is only where the list happens to start.
        if (isRecycleBin || !data.providerReady) return
        if (holder.readsAtBottom(includeScrolling = true) && data.hasUnread()) {
            val visibleThreadId = threadId
            ensureBackgroundThread {
                markThreadMessagesRead(visibleThreadId)
                InboxRepository.refreshUnreadCounts(applicationContext, listOf(visibleThreadId))
            }
        }
    }

    private fun markThreadReadIfAtBottom() {
        if (!data.providerReady || isRecycleBin || markReadInProgress || !holder.readsAtBottom() || !data.hasUnread()) {
            return
        }
        markReadInProgress = true
        val visibleThreadId = threadId
        ensureBackgroundThread {
            try {
                markThreadMessagesRead(visibleThreadId)
                InboxRepository.refreshUnreadCounts(applicationContext, listOf(visibleThreadId))
            } finally {
                runOnUiThread {
                    markReadInProgress = false
                    if (!isDestroyed && threadId == visibleThreadId) {
                        data.markReadLocally()
                        threadItems = getThreadItems()
                        publishItems()
                    }
                }
            }
        }
    }

    // Opening the thread from the Spam screen is what marks its spam read.
    private fun markSpamRead(ids: Collection<Long>) {
        val unreadIds = data.markSpamReadLocally(ids)
        if (unreadIds.isEmpty()) return
        ensureBackgroundThread {
            unreadIds.forEach { markMessageRead(it, isMMS = false) }
        }
    }

    private fun askNotSpam(selected: List<Message>) {
        showAfshDialog { dismiss ->
            AfshSheet(title = getString(R.string.not_spam_sheet_title), onDismiss = dismiss) {
                AfshSheetItem(
                    label = getString(R.string.not_spam_only),
                    icon = AfshIcon.Inbox,
                    supporting = getString(R.string.not_spam_only_text),
                    onClick = {
                        dismissThen {
                            dismiss()
                            unmarkSpam(selected, allowSender = false)
                        }
                    },
                )
                AfshSheetItem(
                    label = resources.getQuantityString(R.plurals.never_spam, 1),
                    icon = AfshIcon.Verified,
                    supporting = resources.getQuantityString(R.plurals.never_spam_text, 1),
                    onClick = {
                        dismissThen {
                            dismiss()
                            unmarkSpam(selected, allowSender = true)
                        }
                    },
                )
            }
        }
    }

    private fun unmarkSpam(selected: List<Message>, allowSender: Boolean = false) {
        ensureBackgroundThread {
            if (allowSender) {
                (selected.map { it.senderPhoneNumber } + people.addresses)
                    .filter { it.isNotEmpty() }.distinct().forEach { config.addAllowedNumber(it) }
            } else {
                selected.map { it.senderPhoneNumber }.filter { it.isNotEmpty() && config.spamNumbers.containsNumber(it) }
                    .distinct().forEach { config.removeSpamNumber(it) }
            }
            selected.forEach { messagesDB.deleteSpamMarker(it.id) }
            ConversationPreview.refresh(applicationContext, threadId)
            InboxRepository.refreshUnreadCounts(applicationContext, listOf(threadId))
            loadSpamReasons()
            threadItems = getThreadItems()
            val items = threadItems
            runOnUiThread {
                refreshMenuItems()
                publishItems(items)
                if (allowSender) {
                    toast(R.string.sender_allowed)
                } else {
                    toast(resources.getQuantityString(R.plurals.message_restored, selected.size))
                }
            }
        }
    }

    // endregion

    // region Conversation actions

    private fun setConversationMuted(muted: Boolean) {
        config.setConversationMuted(threadId, muted)
        refreshMenuItems()
        toast(
            resources.getQuantityString(
                if (muted) R.plurals.conversation_muted else R.plurals.conversation_unmuted,
                1,
                1,
            ),
        )
    }

    private fun alwaysSpamSender() {
        val numbers = people.addresses
        val names = conversation?.title ?: TextUtils.join(", ", numbers)
        confirm(
            title = resources.getQuantityString(R.plurals.always_spam_title, 1, names),
            explanation = resources.getQuantityString(R.plurals.always_spam_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.always_spam, 1),
        ) {
            ensureBackgroundThread {
                numbers.forEach { config.addSpamNumber(it) }
                runOnUiThread { refreshMenuItems() }
            }
        }
    }

    private fun neverSpamSender() {
        val numbers = people.addresses
        val names = conversation?.title ?: TextUtils.join(", ", numbers)
        confirm(
            title = resources.getQuantityString(R.plurals.never_spam_title, 1, names),
            explanation = resources.getQuantityString(R.plurals.never_spam_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.never_spam, 1),
        ) {
            val spam = data.list.filter { data.isSpam(it) }
            if (spam.isNotEmpty()) {
                unmarkSpam(spam, allowSender = true)
            } else {
                ensureBackgroundThread {
                    numbers.forEach { config.addAllowedNumber(it) }
                    runOnUiThread { refreshMenuItems() }
                }
            }
        }
    }

    private fun blockOnThisPhone() {
        val numbers = people.addresses
        val names = conversation?.title ?: TextUtils.join(", ", numbers)
        confirm(
            title = resources.getQuantityString(R.plurals.block_on_phone_title, 1, names),
            explanation = resources.getQuantityString(R.plurals.block_on_phone_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.block_on_phone, 1),
            destructive = true,
        ) {
            if (blockNumbersOnPhone(numbers)) refreshMenuItems()
        }
    }

    private fun askConfirmDelete() {
        if (threadId in UndoDeletion.threads) return
        // The whole conversation is deleted for good (deleteConversation does not use the bin).
        confirm(
            title = resources.getQuantityString(R.plurals.delete_conversations_title, 1, 1),
            explanation = resources.getQuantityString(R.plurals.delete_conversations_forever_text, 1),
            confirmLabel = getString(R.string.delete_forever),
            destructive = true,
        ) {
            val deletedThread = threadId
            UndoDeletion.threads.add(deletedThread)
            threadItems = getThreadItems()
            publishItems()
            refreshMenuItems()
            UndoDeletion.offer(this,
                undo = {
                    UndoDeletion.threads.remove(deletedThread)
                    threadItems = getThreadItems()
                    publishItems()
                    refreshMenuItems()
                },
                commit = {
                    if (isRecycleBin) recycleBin.emptyConversation(deletedThread)
                    else deleteConversation(deletedThread)
                },
                completed = {
                    UndoDeletion.threads.remove(deletedThread)
                    MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                    if (!isDestroyed) finish()
                })
            MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
        }
    }

    private fun askConfirmRestoreAll() {
        confirm(
            title = getString(R.string.restore_conversation_title),
            explanation = getString(R.string.restore_conversation_text),
            confirmLabel = getString(R.string.restore),
        ) {
            ensureBackgroundThread {
                recycleBin.restoreConversation(threadId)
                runOnUiThread {
                    MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                    finish()
                }
            }
        }
    }

    private fun setArchived(archived: Boolean) {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, archived)
            runOnUiThread {
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(reconcile = true))
                finish()
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = people.participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }

    private fun copyNumberToClipboard() {
        val phoneNumber = conversation?.phoneNumber
            ?.ifEmpty { people.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value }
            ?: return
        copyToClipboard(phoneNumber)
    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                MessagingEvents.publish(MessagingEvent.ConversationsChanged(threadId = threadId, reconcile = false))
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber =
            people.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun renameConversation() {
        val current = conversation ?: return
        showRenameDialog(current) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(current, newTitle = title)
                runOnUiThread { refreshMenuItems() }
            }
        }
    }

    private fun maybeDisableShortCodeReply() {
        if (people.isSpecialNumber && !isRecycleBin) {
            currentFocus?.clearFocus()
            hideKeyboard()
            holder.setText("")
            refreshMenuItems()
        }
    }

    // endregion

    // region Manage people

    private fun managePeople() {
        if (holder.people.open) {
            holder.closePeople()
        } else {
            holder.openPeople(people.recipients())
            loadPeopleContacts()
        }
    }

    /**
     * The address book for Manage people. It needs every contact on the phone, so it loads the first
     * time the person opens Manage people, not on every thread load and refresh.
     */
    private fun loadPeopleContacts() {
        if (addContactFieldReady) return
        if (!hasPermission(PERMISSION_READ_CONTACTS)) {
            holder.showPeopleContacts(NewMessageUiState(canReadContacts = false))
            return
        }
        addContactFieldReady = true
        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            val all = (contacts + people.privateContacts).sorted().map { it.toPickerPerson(resources) }.filter { it.phones.isNotEmpty() }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                holder.showPeopleContacts(NewMessageUiState(contacts = all))
            }
        }
    }

    private fun allowContacts() {
        handlePermission(PERMISSION_READ_CONTACTS) { granted ->
            if (granted) {
                holder.showPeopleContacts(NewMessageUiState())
                loadPeopleContacts()
            }
        }
    }

    /** Applies Manage people: a different set of people is a different conversation, which opens in this one's place. */
    private fun applyManagePeople() {
        holder.closePeople()
        val newThreadId = getThreadId(people.addresses.toHashSet())
        if (threadId != newThreadId) {
            Intent(this, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, newThreadId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(this)
            }
        }
    }

    private fun addPerson(recipient: Recipient) {
        if (!people.add(recipient)) return
        holder.showPeople(people.recipients())
        holder.refreshComposer()
    }

    private fun removePerson(recipient: Recipient) {
        if (!people.remove(recipient)) return
        holder.showPeople(people.recipients())
        holder.refreshComposer()
    }

    // endregion

    // region Send SIM

    private fun setupSIMSelector() {
        // this runs once for the cached thread and again after the provider refresh
        val (cards, index) = people.sendSims(data.list.lastOrNull()) ?: return
        holder.showSims(cards, index)
    }

    // endregion

    // region Composer: attachments, dictation and scheduling

    private val dictateMessage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?: return@registerForActivityResult
            // Insert, never auto-send: dictation is frequently wrong.
            holder.insertAtCursor(spoken)
        }

    private fun launchDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        try {
            dictateMessage.launch(intent)
        } catch (_: ActivityNotFoundException) {
            toast(com.afsh.commons.R.string.no_app_found)
        }
    }

    private fun attach(option: AttachOption) = when (option) {
        AttachOption.PHOTO -> launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
        AttachOption.CAMERA -> launchCapturePhotoIntent()
        AttachOption.VIDEO -> launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
        AttachOption.RECORD_VIDEO -> launchActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), CAPTURE_VIDEO_INTENT)
        AttachOption.AUDIO -> launchActivityForResult(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION), CAPTURE_AUDIO_INTENT)
        AttachOption.FILE -> launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
        AttachOption.CONTACT -> launchActivityForResult(Intent(Intent.ACTION_PICK).setType(ContactsContract.Contacts.CONTENT_TYPE), PICK_CONTACT_INTENT)
        AttachOption.SEND_LATER -> launchScheduleSendDialog(holder.scheduledMillis)
    }

    private fun launchActivityForResult(
        intent: Intent,
        requestCode: Int,
        @StringRes error: Int = com.afsh.commons.R.string.no_app_found,
    ) {
        hideKeyboard()
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            showErrorToast(getString(error))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun launchCapturePhotoIntent() {
        val dir = File(cacheDir, "attachments").apply { if (!exists()) mkdirs() }
        val imageFile = File.createTempFile("attachment_", ".jpg", dir)
        capturedImageUri = getMyFileUri(imageFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
        }
        launchActivityForResult(intent, CAPTURE_PHOTO_INTENT)
    }

    private fun launchGetContentIntent(mimeTypes: Array<String>, requestCode: Int) {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            launchActivityForResult(this, requestCode)
        }
    }

    /** A picked contact goes into the composer as plain text (name, then numbers), so it sends as SMS. */
    private fun addContactAsText(contactUri: Uri) {
        ensureBackgroundThread {
            val contact = ContactsHelper(this).getContactFromUri(contactUri)
            val text = contact?.let { contactAsText(it.getNameToDisplay(), it.phoneNumbers) }
            runOnUiThread {
                when {
                    contact == null -> toast(com.afsh.commons.R.string.unknown_error_occurred)
                    text == null -> toast(com.afsh.commons.R.string.no_phone_number_found)
                    else -> {
                        val at = holder.composerText.selection.min.coerceIn(0, holder.text.length)
                        val (value, cursor) = insertBlock(holder.text, at, text)
                        holder.replaceText(value, cursor)
                    }
                }
            }
        }
    }

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (holder.hasAttachment(id)) {
            toast(R.string.duplicate_item_warning)
            return
        }

        val mimeType = contentResolver.getType(uri)
        if (mimeType == null) {
            toast(com.afsh.commons.R.string.unknown_error_occurred)
            return
        }
        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val mmsFileSizeLimit = config.mmsFileSizeLimit
        if (isGif || !isImage) {
            // is it assumed that images will always be compressed below the max MMS size limit
            val fileSize = contentResolver.fileSize(uri)
            if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit) {
                toast(R.string.attachment_sized_exceeds_max_limit, length = Toast.LENGTH_LONG)
                return
            }
        }

        val compress = isImage && !isGif && mmsFileSizeLimit != FILE_SIZE_NONE
        val selection = AttachmentSelection(id, uri, mimeType, getFilenameFromUri(uri), isPending = compress)
        holder.addAttachment(selection)
        if (!compress) return
        imageCompressor.compressImage(uri, mmsFileSizeLimit) { compressedUri ->
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (holder.finishCompression(selection, compressedUri) && compressedUri == null) toast(R.string.compress_error)
            }
        }
    }

    private fun saveMMS(attachments: List<Attachment>) {
        val (intent, requestCode) = when (val request = sender.saveAttachments(attachments) ?: return) {
            is MessageSendCoordinator.SaveRequest.File -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = request.mimetype
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, request.title)
            } to PICK_SAVE_FILE_INTENT
            MessageSendCoordinator.SaveRequest.Folder ->
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addCategory(Intent.CATEGORY_DEFAULT) to PICK_SAVE_DIR_INTENT
        }
        launchActivityForResult(intent = intent, requestCode = requestCode, error = com.afsh.commons.R.string.system_service_disabled)
    }

    private fun saveAttachments(resultData: Intent) {
        applicationContext.contentResolver.takePersistableUriPermission(
            resultData.data!!, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val destinationUri = resultData.data ?: return
        sender.saveTo(destinationUri)
    }

    private fun launchScheduleSendDialog(originalMillis: Long? = null) {
        sender.whenExactAlarmsAllowed {
            val subscriptionId = holder.sendSubscriptionId ?: -1
            val target = ScheduleTarget(holder.text.trim(), scheduleRecipient(subscriptionId))
            sendLater.launch(SendLaterActivity.Request(target, originalMillis))
        }
    }

    /** 12 returned a time: with something written it is scheduled at once; otherwise the composer waits for it. */
    private fun onSendTimeChosen(millis: Long) {
        val dateTime = DateTime(millis)
        val label = if (dateTime.yearOfCentury().get() > DateTime.now().yearOfCentury().get()) {
            millis.formatDate(this)
        } else {
            DateUtils.formatDateTime(this, millis, FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_NO_YEAR)
        }
        holder.setSchedule(millis, label)
        if (holder.composer.hasContent(holder.text)) sendMessage()
    }

    /** 47 decided: save the new text and time, send now, or cancel. */
    private fun onScheduledDecision(messageId: Long, decision: ScheduledMessageActivity.Decision) {
        val message = data.list.firstOrNull { it.id == messageId && it.isScheduled } ?: return
        when (decision) {
            is ScheduledMessageActivity.Decision.Save -> sender.updateScheduled(message, decision.text, decision.time)
            ScheduledMessageActivity.Decision.SendNow -> sendScheduledNow(message)
            ScheduledMessageActivity.Decision.Cancel -> deleteMessages(listOf(message), false, false)
        }
    }

    /** 84 · Send now: the scheduled message goes as a normal one, and its schedule is dropped. */
    private fun sendScheduledNow(message: Message) {
        data.list.removeAll { message.id == it.id }
        message.attachment?.attachments?.forEach { addAttachment(it.getUri()) }
        sender.sendScheduledNow(message, holder.attachments)
    }

    // endregion

    // region Sending and deleting

    private fun sendMessage() {
        // Also while an image is still compressing: the original may be over the MMS limit.
        if (!holder.composer.hasContent(holder.text)) return
        holder.scrollToBottom()
        sender.send(holder.text, holder.attachments, holder.sendSubscriptionId, holder.scheduledMillis)
    }

    private fun deleteMessages(
        messagesToRemove: List<Message>,
        toRecycleBin: Boolean,
        fromRecycleBin: Boolean,
    ) {
        if (!fromRecycleBin) {
            val ids = messagesToRemove.map { it.getStableId() }.toSet()
            runOnUiThread {
                UndoDeletion.messages.addAll(ids)
                threadItems = getThreadItems()
                holder.clearSelection()
                publishItems()
                UndoDeletion.offer(this,
                    undo = {
                        UndoDeletion.messages.removeAll(ids)
                        threadItems = getThreadItems()
                        publishItems()
                    },
                    commit = { performMessageDeletion(messagesToRemove, toRecycleBin, false) },
                    completed = {
                        UndoDeletion.messages.removeAll(ids)
                        if (!isDestroyed && !isFinishing) {
                            if (data.isEmpty) finish() else setupThread {}
                        }
                    })
            }
        } else {
            performMessageDeletion(messagesToRemove, toRecycleBin, true)
            runOnUiThread {
                holder.clearSelection()
                if (data.isEmpty) finish() else setupThread {}
            }
        }
    }

    private fun performMessageDeletion(messagesToRemove: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        data.list.removeAll(messagesToRemove.toSet())
        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                deleteScheduledMessage(messageId)
                cancelScheduleSendPendingIntent(messageId)
            } else {
                if (toRecycleBin) {
                    recycleBin.moveToRecycleBin(messageId)
                } else if (fromRecycleBin) {
                    recycleBin.restore(messageId)
                } else {
                    deleteMessage(messageId, message.isMMS)
                }
            }
        }
        updateLastConversationMessage(threadId)

        // move all scheduled messages to a temporary thread when there are no real messages left
        val messages = data.list
        if (messages.isNotEmpty() && messages.all { it.isScheduled }) {
            val scheduledMessage = messages.last()
            val fakeThreadId = generateRandomId()
            createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            updateScheduledMessagesThreadId(messages, fakeThreadId)
            threadId = fakeThreadId
            syncActiveConversationThread()
        }
    }

    // endregion

    companion object {
        private const val MAX_JUMP_LOOPS = 1000
        private const val SAVED_COMPOSER_TEXT = "composer_text"
        private const val SAVED_NEW_ON_OPEN = "new_on_open"
        private const val SAVED_COMPOSER_ATTACHMENTS = "composer_attachments"
    }
}

package com.afsh.messages.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.speech.RecognizerIntent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
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
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.afsh.messages.BuildConfig
import com.afsh.messages.R
import com.afsh.messages.extensions.clearExpiredScheduledMessages
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.copyToUri
import com.afsh.messages.extensions.createTemporaryThread
import com.afsh.messages.extensions.deleteConversation
import com.afsh.messages.extensions.deleteMessage
import com.afsh.messages.extensions.deleteScheduledMessage
import com.afsh.messages.extensions.deleteSmsDraft
import com.afsh.messages.extensions.dialNumber
import com.afsh.messages.extensions.emptyMessagesRecycleBinForConversation
import com.afsh.messages.extensions.filterNotInByKey
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.getFileSizeFromUri
import com.afsh.messages.extensions.getMessages
import com.afsh.messages.extensions.getSmsDraft
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.getThreadParticipants
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.extensions.indexOfFirstOrNull
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
import com.afsh.messages.extensions.moveMessageToRecycleBin
import com.afsh.messages.extensions.removeDiacriticsIfNeeded
import com.afsh.messages.extensions.renameConversation
import com.afsh.messages.extensions.restoreAllMessagesFromRecycleBinForConversation
import com.afsh.messages.extensions.restoreMessageFromRecycleBin
import com.afsh.messages.extensions.saveSmsDraft
import com.afsh.messages.extensions.shouldUnarchive
import com.afsh.messages.extensions.subscriptionManagerCompat
import com.afsh.messages.extensions.toArrayList
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
import com.afsh.messages.helpers.InboxRepository
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
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URI
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URIS
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.helpers.THREAD_TEXT
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.helpers.contactAsText
import com.afsh.messages.helpers.generateRandomId
import com.afsh.messages.helpers.insertBlock
import com.afsh.messages.helpers.refreshConversations
import com.afsh.messages.helpers.refreshMessages
import com.afsh.messages.messaging.cancelScheduleSendPendingIntent
import com.afsh.messages.messaging.isLongMmsMessage
import com.afsh.messages.messaging.isShortCodeWithLetters
import com.afsh.messages.messaging.scheduleMessage
import com.afsh.messages.messaging.sendMessageCompat
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Events
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.afsh.messages.models.SIMCard
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.afshTextScale
import com.afsh.messages.ui.components.confirm
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.openRequestExactAlarmSettings
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File

/**
 * The conversation screen's host: lifecycle, intents, permissions, and the provider and settings the
 * conversation reads and writes. The screen's state and the logic that needs no Android live in [holder];
 * the loaded messages and what the timeline derives from them live in [data].
 */
class ThreadActivity : SimpleActivity(), UndoDeletion.UndoHost {
    private var threadId = 0L
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

    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var addContactFieldReady = false
    private val availableSIMCards = ArrayList<SIMCard>()
    private var pendingAttachmentsToSave: List<Attachment>? = null
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

        override fun simChosen(subscriptionId: Int) {
            participants.flatMap { contact -> contact.phoneNumbers.map { it.normalizedNumber } }.forEach {
                config.saveUseSIMIdAtNumber(it, subscriptionId)
            }
        }

        override fun isMms(text: String, hasAttachments: Boolean) = isMmsMessage(text, hasAttachments)

        override fun smsLength(text: String): Pair<Int, Int>? {
            if (!config.showCharacterCounter) return null
            val length = SmsMessage.calculateLength(if (config.useSimpleCharacters) text.normalizeString() else text, false)
            return length[0] to length[2]
        }
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
                ThreadRoute(holder, snackbarHost, sendOnEnter = config.sendOnEnter)
            }
        }
        // Keeps the side padding for display cutouts; the composer pads itself for the keyboard and navigation bar.
        setupEdgeToEdge()

        val extras = intent.extras
        if (extras == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let { holder.showTitle(it) }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)
        openSpam = intent.getBooleanExtra(OPEN_SPAM, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        loadConversation()
        refreshMenuItems()
    }

    override fun onResume() {
        super.onResume()
        refreshAfshTheme()
        applySystemBars()
        // mute and starring can change on other screens
        refreshMenuItems()
        publishItems()

        isActivityVisible = true
        root.post(updateScrollPosition)

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread { refreshMenuItems() }
            }

            val smsDraft = getSmsDraft(threadId)
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
        bus?.post(Events.RefreshConversations())
        isActivityVisible = false
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
        bus?.unregister(this)
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
            if (draftMessage.isNotEmpty() && !hasAttachments) {
                saveSmsDraft(draftMessage, threadId)
            } else {
                deleteSmsDraft(threadId)
            }
        }
    }

    private fun refreshAfshTheme() {
        darkTheme = afshDark()
        textScale = afshTextScale()
    }

    private fun applySystemBars() {
        // Status icons sit on the pine header in both themes; the composer below is light or dark.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // region Screen events

    private fun onThreadEvent(event: ThreadEvent) {
        when (event) {
            ThreadEvent.Back -> onBackPressedDispatcher.onBackPressed()
            ThreadEvent.OpenDetails -> if (conversation != null && !isRecycleBin) launchConversationDetails(threadId)
            ThreadEvent.Dial -> if (participants.isNotEmpty()) dialNumber()
            is ThreadEvent.Menu -> if (participants.isNotEmpty()) handleMenuItemAction(event.action)
            is ThreadEvent.SearchSubmit -> jumpToNextMatch(event.query)
            is ThreadEvent.Act -> onMessageAction(event.action, event.messages)
            is ThreadEvent.Retry -> resendMessage(event.message)
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
            ThreadMenuAction.BLOCK -> blockNumber()
            ThreadMenuAction.ALLOW_SENDER -> unmarkSpam(data.list.filter { data.isSpam(it) }, allowSender = true)
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
            MessageAction.DETAILS -> startActivity(MessageDetailsActivity.intent(this, messageFacts(first, simLabels)))
            MessageAction.EDIT_SCHEDULED -> editScheduled.launch(
                ScheduledMessageActivity.Request(first.id, ScheduleTarget(first.body, scheduleRecipient(first.subscriptionId)), first.millis()),
            )
            MessageAction.SEND_NOW -> sendScheduledNow(first)
            MessageAction.SAVE_AS -> selected.flatMap { it.attachment?.attachments.orEmpty() }.takeIf { it.isNotEmpty() }?.let { saveMMS(it) }
            MessageAction.NOT_SPAM -> {
                holder.clearSelection()
                unmarkSpam(selected)
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
                muted = config.isConversationMuted(threadId),
                specialNumber = isSpecialNumber(),
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
            simLabels = simLabels,
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

    private val simLabels: Map<Int, String> by lazy { loadSimLabels() }

    @SuppressLint("MissingPermission")
    private fun loadSimLabels(): Map<Int, String> {
        val sims = subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()
        // One SIM tells the reader nothing, so it is not labelled.
        return if (sims.size < 2) emptyMap() else sims.associate { it.subscriptionId to "%d".format(it.simSlotIndex + 1) }
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
                    actionLabel = getString(org.fossify.commons.R.string.undo),
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
            val (items, initial) = mediaItems(data.list, message, attachment, simLabels)
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
        val name = conversation?.title?.takeIf { it.isNotEmpty() }
            ?: participants.joinToString { it.name.ifEmpty { it.phoneNumbers.firstOrNull()?.value.orEmpty() } }
        val to = getString(R.string.scheduled_to, name)
        return simLabels[subscriptionId]?.let { getString(R.string.detail_pair, to, getString(R.string.message_sim_label, it)) } ?: to
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
                if (data.isEmpty && !isSpecialNumber()) {
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
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = ArrayList(data.list).hashCode()
            if (!isRecycleBin) {
                var messages = getMessages(threadId, includeAllUnread = true)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
                data.list = messages
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && data.list.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    data.providerReady = true
                    setupAdapter()
                    runOnUiThread { callback() }
                    return@ensureBackgroundThread
                }
            } catch (ignored: Exception) {
            }

            setupParticipants()

            // check if no participant came from a privately stored contact in Simple Contacts
            if (privateContacts.isNotEmpty()) {
                val senderNumbersToReplace = HashMap<String, String>()
                participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
                    privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first().normalizedNumber) }
                        ?.apply {
                            senderNumbersToReplace[participant.phoneNumbers.first().normalizedNumber] =
                                name
                            participant.name = name
                            participant.photoUri = photoUri
                        }
                }

                data.list.forEach { message ->
                    if (senderNumbersToReplace.keys.contains(message.senderName)) {
                        message.senderName = senderNumbersToReplace[message.senderName]!!
                    }
                }
            }

            if (participants.isEmpty()) {
                val name = intent.getStringExtra(THREAD_TITLE) ?: ""
                val number = intent.getStringExtra(THREAD_NUMBER)
                if (number == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    finish()
                    return@ensureBackgroundThread
                }

                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = 0,
                    contactId = 0,
                    name = name,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                participants.add(contact)
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
        if (participants.isEmpty()) {
            participants = if (data.isEmpty) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                data.list.first().participants
            }
            runOnUiThread {
                maybeDisableShortCodeReply()
            }
        }
    }

    private fun getPhoneNumbersFromIntent(): ArrayList<String> {
        val numberFromIntent = intent.getStringExtra(THREAD_NUMBER)
        val numbers = ArrayList<String>()

        if (numberFromIntent != null) {
            if (numberFromIntent.startsWith('[') && numberFromIntent.endsWith(']')) {
                val type = object : TypeToken<List<String>>() {}.type
                numbers.addAll(Gson().fromJson(numberFromIntent, type))
            } else {
                numbers.add(numberFromIntent)
            }
        }
        return numbers
    }

    private fun fixParticipantNumbers(
        participants: ArrayList<SimpleContact>,
        properNumbers: ArrayList<String>,
    ): ArrayList<SimpleContact> {
        for (number in properNumbers) {
            for (participant in participants) {
                @Suppress("UNCHECKED_CAST")
                participant.phoneNumbers = participant.phoneNumbers.map {
                    val numberWithoutPlus = number.replace("+", "")
                    if (numberWithoutPlus == it.normalizedNumber.trim()) {
                        if (participant.name == it.normalizedNumber) {
                            participant.name = number
                        }
                        PhoneNumber(number, 0, "", number)
                    } else {
                        PhoneNumber(it.normalizedNumber, 0, "", it.normalizedNumber)
                    }
                } as ArrayList<PhoneNumber>
            }
        }

        return participants
    }

    private fun loadMoreMessages() {
        if (data.isEmpty || data.allFetched || data.loadingOlder) return
        data.loadingOlder = true
        val cutoff = data.list.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            val items = threadItems
            runOnUiThread {
                data.loadingOlder = false
                publishItems(items)
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> =
        data.prependOlder(getMessages(threadId, cutoff).filterNotInByKey(data.list) { it.getStableId() })

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

        ensureBackgroundThread {
            // A page the timeline asked for may still be loading; let it land rather than give up.
            var waits = 0
            while (data.loadingOlder && waits++ < JUMP_WAIT_LIMIT) Thread.sleep(JUMP_WAIT_MS)
            data.loadingOlder = true
            val items = try {
                var cutoff = data.oldestDate() ?: Int.MAX_VALUE
                var found = data.contains(messageId, isMms)
                var loops = 0

                // not the best solution, but this will do for now.
                while (!found && !data.allFetched) {
                    if (fetchOlderMessages(cutoff).isEmpty() || loops >= 1000) break
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
                    holder.jumpFailed()
                }
                return@ensureBackgroundThread
            }
            runOnUiThread {
                data.loadingOlder = false
                publishItems(items)
                holder.scrollToJump(messageId, isMms)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin) {
            return
        }

        refreshedSinceSent = true
        data.allFetched = false

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val current = data.list
        val lastMaxId = current.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = getThreadId(participants.getAddresses().toSet())
        val newMessages = getMessages(newThreadId, includeScheduledMessages = false,
            includeAllUnread = true, oldestLoadedDate = current.firstOrNull { !it.isScheduled }?.date)
        if (current.isNotEmpty() && current.all { it.isScheduled } && newMessages.isNotEmpty()) {
            // update scheduled messages with real thread id
            threadId = newThreadId
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

    private fun unmarkSpam(selected: List<Message>, allowSender: Boolean = false) {
        ensureBackgroundThread {
            if (allowSender) {
                (selected.map { it.senderPhoneNumber } + participants.getAddresses())
                    .filter { it.isNotEmpty() }.distinct().forEach { config.addAllowedNumber(it) }
            } else {
                // Not spam from a blocked sender unblocks them, or their next message goes to Spam again.
                selected.map { it.senderPhoneNumber }.filter { it.isNotEmpty() && config.spamNumbers.containsNumber(it) }
                    .distinct().forEach { config.removeSpamNumber(it) }
            }
            selected.forEach { messagesDB.deleteSpamMarker(it.id) }
            InboxRepository.refreshUnreadCounts(applicationContext, listOf(threadId))
            loadSpamReasons()
            threadItems = getThreadItems()
            val items = threadItems
            runOnUiThread {
                refreshMenuItems()
                publishItems(items)
                toast(if (allowSender) R.string.sender_allowed else R.string.message_restored)
            }
        }
    }

    // endregion

    // region Conversation actions

    private fun setConversationMuted(muted: Boolean) {
        config.setConversationMuted(threadId, muted)
        refreshMenuItems()
        toast(if (muted) R.string.conversation_muted else R.string.conversation_unmuted)
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val names = conversation?.title ?: TextUtils.join(", ", numbers)
        confirm(
            title = getString(R.string.block_sender_title, names),
            explanation = getString(R.string.block_sender_text),
            confirmLabel = getString(R.string.block_sender),
            destructive = true,
        ) {
            ensureBackgroundThread {
                // App-level block: new SMS from these numbers are stored and go to Spam.
                numbers.forEach {
                    config.addSpamNumber(it)
                }
                runOnUiThread { refreshMenuItems() }
            }
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
                    if (isRecycleBin) emptyMessagesRecycleBinForConversation(deletedThread)
                    else deleteConversation(deletedThread)
                },
                completed = {
                    UndoDeletion.threads.remove(deletedThread)
                    refreshConversations()
                    if (!isDestroyed) finish()
                })
            refreshConversations()
        }
    }

    private fun askConfirmRestoreAll() {
        confirm(
            title = getString(R.string.restore_conversation_title),
            explanation = getString(R.string.restore_conversation_text),
            confirmLabel = getString(R.string.restore),
        ) {
            ensureBackgroundThread {
                restoreAllMessagesFromRecycleBinForConversation(threadId)
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun setArchived(archived: Boolean) {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, archived)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }

    private fun copyNumberToClipboard() {
        val phoneNumber = conversation?.phoneNumber
            ?.ifEmpty { participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value }
            ?: return
        copyToClipboard(phoneNumber)
    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                bus?.post(Events.RefreshConversations())
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber =
            participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun renameConversation() {
        showRenameDialog(conversation!!) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(conversation!!, newTitle = title)
                runOnUiThread { refreshMenuItems() }
            }
        }
    }

    private fun isSpecialNumber(): Boolean {
        val addresses = participants.getAddresses()
        return addresses.any { isShortCodeWithLetters(it) }
    }

    private fun maybeDisableShortCodeReply() {
        if (isSpecialNumber() && !isRecycleBin) {
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
            holder.openPeople(participants.map { it.toRecipient() })
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
            val all = (contacts + privateContacts).sorted().map { it.toPickerPerson(resources) }.filter { it.phones.isNotEmpty() }
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
        val numbers = HashSet<String>()
        participants.forEach { contact ->
            contact.phoneNumbers.forEach {
                numbers.add(it.normalizedNumber)
            }
        }

        val newThreadId = getThreadId(numbers)
        if (threadId != newThreadId) {
            Intent(this, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, newThreadId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(this)
            }
        }
    }

    private fun SimpleContact.toRecipient(): Recipient {
        val phone = phoneNumbers.firstOrNull()
        return Recipient(phone?.normalizedNumber?.ifEmpty { phone.value } ?: "", name)
    }

    private fun addPerson(recipient: Recipient) {
        if (participants.any { it.toRecipient().number == recipient.number }) return
        val phoneNumber = PhoneNumber(recipient.number, 0, "", recipient.number)
        participants.add(
            SimpleContact(
                rawId = recipient.number.hashCode(),
                contactId = recipient.number.hashCode(),
                name = recipient.name,
                photoUri = "",
                phoneNumbers = arrayListOf(phoneNumber),
                birthdays = ArrayList(),
                anniversaries = ArrayList(),
            )
        )
        holder.showPeople(participants.map { it.toRecipient() })
        holder.refreshComposer()
    }

    private fun removePerson(recipient: Recipient) {
        // The conversation's own person stays.
        if (participants.firstOrNull()?.toRecipient() == recipient) return
        participants = participants.filterNot { it.toRecipient() == recipient }.toCollection(ArrayList())
        holder.showPeople(participants.map { it.toRecipient() })
        holder.refreshComposer()
    }

    // endregion

    // region Send SIM

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val availableSIMs = subscriptionManagerCompat().activeSubscriptionInfoList ?: return
        if (availableSIMs.size > 1) {
            // this runs once for the cached thread and again after the provider refresh
            availableSIMCards.clear()
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                // The physical slot, as the timeline's SIM labels use; the list position only orders the toggle.
                val slot = subscriptionInfo.simSlotIndex.takeIf { it >= 0 }?.plus(1) ?: (index + 1)
                availableSIMCards.add(SIMCard(slot, subscriptionInfo.subscriptionId, label))
            }

            val numbers = participants.flatMap { contact -> contact.phoneNumbers.map { it.normalizedNumber } }
            if (numbers.isEmpty()) {
                return
            }

            holder.showSims(availableSIMCards.toList(), getProperSimIndex(availableSIMs, numbers))
        }
    }

    @SuppressLint("MissingPermission")
    private fun getProperSimIndex(
        availableSIMs: MutableList<SubscriptionInfo>,
        numbers: List<String>,
    ): Int {
        val userPreferredSimId = config.getUseSIMIdAtNumber(numbers.first())
        val userPreferredSimIdx =
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val lastMessage = data.list.lastOrNull()
        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == lastMessage.subscriptionId }
        } else {
            null
        }

        val defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == defaultSmsSubscriptionId }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
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
            toast(org.fossify.commons.R.string.no_app_found)
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
        @StringRes error: Int = org.fossify.commons.R.string.no_app_found,
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
                    contact == null -> toast(org.fossify.commons.R.string.unknown_error_occurred)
                    text == null -> toast(org.fossify.commons.R.string.no_phone_number_found)
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
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }
        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val mmsFileSizeLimit = config.mmsFileSizeLimit
        if (isGif || !isImage) {
            // is it assumed that images will always be compressed below the max MMS size limit
            val fileSize = getFileSizeFromUri(uri)
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
        pendingAttachmentsToSave = attachments
        if (attachments.size == 1) {
            val attachment = attachments.first()
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = attachment.mimetype
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, attachment.uriString.split("/").last())
                launchActivityForResult(
                    intent = this,
                    requestCode = PICK_SAVE_FILE_INTENT,
                    error = org.fossify.commons.R.string.system_service_disabled
                )
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                launchActivityForResult(
                    intent = this,
                    requestCode = PICK_SAVE_DIR_INTENT,
                    error = org.fossify.commons.R.string.system_service_disabled
                )
            }
        }
    }

    private fun saveAttachments(resultData: Intent) {
        applicationContext.contentResolver.takePersistableUriPermission(
            resultData.data!!, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val destinationUri = resultData.data ?: return
        ensureBackgroundThread {
            try {
                if (DocumentsContract.isTreeUri(destinationUri)) {
                    val outputDir = DocumentFile.fromTreeUri(this, destinationUri)
                        ?: return@ensureBackgroundThread
                    pendingAttachmentsToSave?.forEach { attachment ->
                        val documentFile = outputDir.createFile(
                            attachment.mimetype,
                            attachment.filename.takeIf { it.isNotBlank() }
                                ?: attachment.uriString.getFilenameFromPath()
                        ) ?: return@forEach
                        copyToUri(src = attachment.getUri(), dst = documentFile.uri)
                    }
                } else {
                    copyToUri(pendingAttachmentsToSave!!.first().getUri(), resultData.data!!)
                }

                toast(org.fossify.commons.R.string.file_saved)
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                pendingAttachmentsToSave = null
            }
        }
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                confirm(
                    title = getString(org.fossify.commons.R.string.permission_required),
                    explanation = getString(org.fossify.commons.R.string.allow_alarm_scheduled_messages),
                    confirmLabel = getString(R.string.open_settings),
                ) {
                    openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                }
            }
        } else {
            callback()
        }
    }

    private fun launchScheduleSendDialog(originalMillis: Long? = null) {
        askForExactAlarmPermissionIfNeeded {
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
            is ScheduledMessageActivity.Decision.Save -> updateScheduledMessage(message, decision.text, decision.time)
            ScheduledMessageActivity.Decision.SendNow -> sendScheduledNow(message)
            ScheduledMessageActivity.Decision.Cancel -> deleteMessages(listOf(message), false, false)
        }
    }

    private fun updateScheduledMessage(message: Message, text: String, millis: Long) {
        if (millis <= System.currentTimeMillis()) {
            toast(R.string.must_pick_time_in_the_future)
            return
        }
        ensureBackgroundThread {
            val updated = message.copy(
                body = text,
                date = (millis / 1000).toInt(),
                attachment = message.attachment?.copy(text = text),
            )
            // The alarm is keyed by the message id, so this replaces the old one.
            scheduleMessage(updated)
            insertOrUpdateMessage(updated)
            conversationsDB.getConversationWithThreadId(threadId)?.let { current ->
                if (current.snippet == message.body) conversationsDB.insertOrUpdate(current.copy(snippet = text))
            }
        }
    }

    /** 84 · Send now: the scheduled message goes as a normal one, and its schedule is dropped. */
    private fun sendScheduledNow(message: Message) {
        data.list.removeAll { message.id == it.id }
        message.attachment?.attachments?.forEach { addAttachment(it.getUri()) }
        sendNormalMessage(message.body, message.subscriptionId)
        ensureBackgroundThread {
            deleteScheduledMessage(message.id)
            cancelScheduleSendPendingIntent(message.id)
            refreshMessages()
        }
    }

    // endregion

    // region Sending and deleting

    private fun isMmsMessage(text: String, hasAttachments: Boolean = holder.attachments.isNotEmpty()): Boolean {
        val isGroupMms = participants.size > 1 && config.sendGroupMessageMMS
        return hasAttachments || isGroupMms || isLongMmsMessage(text)
    }

    private fun sendMessage() {
        // Also while an image is still compressing: the original may be over the MMS limit.
        if (!holder.composer.hasContent(holder.text)) return
        holder.scrollToBottom()

        val text = removeDiacriticsIfNeeded(holder.text)
        val subscriptionId = holder.sendSubscriptionId ?: SmsManager.getDefaultSmsSubscriptionId()

        val scheduledMillis = holder.scheduledMillis
        if (scheduledMillis != null) {
            sendScheduledMessage(text, subscriptionId, scheduledMillis)
        } else {
            sendNormalMessage(text, subscriptionId)
        }
    }

    private fun sendScheduledMessage(text: String, subscriptionId: Int, millis: Long) {
        if (millis < System.currentTimeMillis() + 1000L) {
            toast(R.string.must_pick_time_in_the_future)
            launchScheduleSendDialog(millis)
            return
        }

        refreshedSinceSent = false
        try {
            ensureBackgroundThread {
                val messageId = generateRandomId()
                val message = buildScheduledMessage(text, subscriptionId, messageId, millis)
                if (data.isEmpty) {
                    // create a temporary thread until a real message is sent
                    threadId = message.threadId
                    createTemporaryThread(message, message.threadId, conversation)
                }
                val conversation = conversationsDB.getConversationWithThreadId(threadId)
                if (conversation != null) {
                    val nowSeconds = (System.currentTimeMillis() / 1000).toInt()
                    conversationsDB.insertOrUpdate(
                        conversation.copy(
                            date = nowSeconds,
                            snippet = message.body
                        )
                    )
                }
                scheduleMessage(message)
                insertOrUpdateMessage(message)

                runOnUiThread {
                    holder.clearMessage()
                    holder.clearSchedule()
                }
            }
        } catch (e: Exception) {
            showErrorToast(
                e.localizedMessage ?: getString(org.fossify.commons.R.string.unknown_error_occurred)
            )
        }
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.getAddresses()
        val attachments = buildMessageAttachments()

        try {
            refreshedSinceSent = false
            sendMessageCompat(text, addresses, subscriptionId, attachments)
            ensureBackgroundThread {
                val messages = getMessages(threadId, limit = maxOf(1, attachments.size))
                    .filterNotInByKey(data.list) { it.getStableId() }
                for (message in messages) {
                    insertOrUpdateMessage(message)
                }
            }
            holder.clearMessage()
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: Error) {
            showErrorToast(
                e.localizedMessage ?: getString(org.fossify.commons.R.string.unknown_error_occurred)
            )
        }
    }

    /** C-M5: resends at once on the SIM shown, leaving the composer as it is. */
    private fun resendMessage(message: Message) {
        // Until the provider shows it leave the failed state, a second tap would transmit it again.
        if (!holder.startRetry(message)) return
        val subscriptionId = holder.sendSubscriptionId ?: SmsManager.getDefaultSmsSubscriptionId()
        try {
            refreshedSinceSent = false
            sendMessageCompat(
                message.body, participants.getAddresses(), subscriptionId, message.attachment?.attachments.orEmpty(), message,
                // Today's settings must not turn a failed MMS into an SMS, which could not replace its row.
                forceMms = message.isMMS,
            )
            refreshMessages()
        } catch (e: Exception) {
            holder.retryFailed(message)
            showErrorToast(e)
        } catch (e: Error) {
            holder.retryFailed(message)
            showErrorToast(e.localizedMessage ?: getString(org.fossify.commons.R.string.unknown_error_occurred))
        }
    }

    private fun insertOrUpdateMessage(message: Message) {
        data.upsert(message)

        val newItems = getThreadItems()
        runOnUiThread {
            threadItems = newItems
            publishItems(newItems)
            holder.scrollToBottom()
            if (!refreshedSinceSent) {
                refreshMessages()
            }
        }
        messagesDB.insertOrUpdate(message)
        val isOutgoing = !message.isReceivedMessage() && !message.isScheduled
        if (isOutgoing) {
            InboxRepository.bumpAfterOutgoing(this, message.threadId, message.body)
        }
        if (shouldUnarchive()) {
            updateConversationArchivedStatus(message.threadId, false)
        }
        if (isOutgoing || shouldUnarchive()) {
            refreshConversations()
        }
    }

    private fun buildScheduledMessage(text: String, subscriptionId: Int, messageId: Long, millis: Long): Message {
        val threadId = if (data.isEmpty) messageId else threadId
        return Message(
            id = messageId,
            body = text,
            type = MESSAGE_TYPE_QUEUED,
            status = STATUS_NONE,
            participants = participants,
            date = (millis / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = isMmsMessage(text),
            attachment = MessageAttachment(messageId, text, buildMessageAttachments(messageId)),
            senderPhoneNumber = "",
            senderName = "",
            senderPhotoUri = "",
            subscriptionId = subscriptionId,
            isScheduled = true
        )
    }

    private fun buildMessageAttachments(messageId: Long = -1L) = holder.attachments
        .map { Attachment(null, messageId, it.uri.toString(), it.mimetype, 0, 0, it.filename) }
        .toArrayList()

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
                    moveMessageToRecycleBin(messageId)
                } else if (fromRecycleBin) {
                    restoreMessageFromRecycleBin(messageId)
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
        }
    }

    // endregion

    companion object {
        private const val JUMP_WAIT_MS = 50L
        private const val JUMP_WAIT_LIMIT = 100
        private const val SAVED_COMPOSER_TEXT = "composer_text"
        private const val SAVED_NEW_ON_OPEN = "new_on_open"
        private const val SAVED_COMPOSER_ATTACHMENTS = "composer_attachments"
    }
}

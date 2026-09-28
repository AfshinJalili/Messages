package org.fossify.messages.activities

import org.fossify.messages.helpers.UndoDeletion
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.Toast
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.insetsController
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.maybeShowNumberPickerDialog
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.openRequestExactAlarmSettings
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ExportResult
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.VcfExporter
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.AutoCompleteTextViewAdapter
import org.fossify.messages.databinding.ActivityThreadBinding
import org.fossify.messages.databinding.ItemSelectedContactBinding
import org.fossify.messages.dialogs.InvalidNumberDialog
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.dialogs.ScheduleMessageDialog
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.copyToUri
import org.fossify.messages.extensions.createTemporaryThread
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.deleteScheduledMessage
import org.fossify.messages.extensions.deleteSmsDraft
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.emptyMessagesRecycleBinForConversation
import org.fossify.messages.extensions.filterNotInByKey
import org.fossify.messages.extensions.getAddresses
import org.fossify.messages.extensions.getFileSizeFromUri
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getSmsDraft
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.getThreadTitle
import org.fossify.messages.extensions.indexOfFirstOrNull
import org.fossify.messages.extensions.isGifMimeType
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.helpers.InboxRepository
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.moveMessageToRecycleBin
import org.fossify.messages.extensions.removeDiacriticsIfNeeded
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.restoreAllMessagesFromRecycleBinForConversation
import org.fossify.messages.extensions.restoreMessageFromRecycleBin
import org.fossify.messages.extensions.saveSmsDraft
import org.fossify.messages.extensions.shouldUnarchive
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.extensions.toArrayList
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.extensions.updateScheduledMessagesThreadId
import org.fossify.messages.helpers.CAPTURE_AUDIO_INTENT
import org.fossify.messages.helpers.CAPTURE_PHOTO_INTENT
import org.fossify.messages.helpers.CAPTURE_VIDEO_INTENT
import org.fossify.messages.helpers.FILE_SIZE_NONE
import org.fossify.messages.helpers.IS_LAUNCHED_FROM_SHORTCUT
import org.fossify.messages.helpers.IS_RECYCLE_BIN
import org.fossify.messages.helpers.MESSAGES_LIMIT
import org.fossify.messages.helpers.PICK_CONTACT_INTENT
import org.fossify.messages.helpers.PICK_DOCUMENT_INTENT
import org.fossify.messages.helpers.PICK_PHOTO_INTENT
import org.fossify.messages.helpers.PICK_SAVE_DIR_INTENT
import org.fossify.messages.helpers.PICK_SAVE_FILE_INTENT
import org.fossify.messages.helpers.PICK_VIDEO_INTENT
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SEARCHED_MESSAGE_IS_MMS
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URI
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URIS
import org.fossify.messages.helpers.OPEN_SPAM
import org.fossify.messages.helpers.containsNumber
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TEXT
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.generateRandomId
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.messaging.cancelScheduleSendPendingIntent
import org.fossify.messages.messaging.isLongMmsMessage
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.messaging.scheduleMessage
import org.fossify.messages.messaging.sendMessageCompat
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.AttachmentSelection
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.fossify.messages.models.SIMCard
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.helpers.OPEN_THREAD_SEARCH
import org.fossify.messages.models.ThreadItem.ThreadSpamGroup
import org.fossify.messages.models.buildThreadItems
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.messages.dialogs.MessageDetailsDialog
import org.fossify.messages.dialogs.SelectTextDialog
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.openLineDark
import org.fossify.messages.ui.openLineTextScale
import org.fossify.messages.helpers.ImageCompressor
import org.fossify.messages.ui.thread.AttachOption
import org.fossify.messages.ui.thread.ComposerEvent
import org.fossify.messages.ui.thread.ComposerState
import org.fossify.messages.ui.thread.InitialScroll
import org.fossify.messages.ui.thread.SimOption
import org.fossify.messages.ui.thread.ThreadComposer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.fossify.messages.ui.thread.MessageAction
import org.fossify.messages.ui.thread.ScrollRequest
import org.fossify.messages.ui.thread.ThreadEvent
import org.fossify.messages.ui.thread.ThreadHeader
import org.fossify.messages.ui.thread.ThreadHeaderState
import org.fossify.messages.ui.thread.ThreadMenuAction
import org.fossify.messages.ui.thread.ThreadTimeline
import org.fossify.messages.ui.thread.ThreadUiState
import org.fossify.messages.ui.thread.starKey
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import org.fossify.messages.ui.thread.LiftScrim
import kotlin.math.roundToInt

class ThreadActivity : SimpleActivity(), UndoDeletion.UndoHost {
    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var markReadInProgress = false
    private var providerMessagesReady = false
    private var pendingInitialScroll = true
    private var initialPositionSettled = false
    private val updateScrollPosition = Runnable {
        if (isActivityVisible && !isDestroyed) {
            markThreadReadIfAtBottom()
        }
    }
    private var listAtBottom = true
    private var listScrolling = false
    private var scrollNonce = 0L
    /** The composer's text lives here, not in [ui], so typing does not rebuild the timeline state. */
    private var composerText by mutableStateOf(TextFieldValue(""))
    /** Apart from [ui] for the same reason: the counter changes as you type, and the timeline must not recompose for it. */
    private val composerState = mutableStateOf(ComposerState())
    private var composer: ComposerState
        get() = composerState.value
        set(value) {
            composerState.value = value
            updateComposerVisibility()
        }
    /** Text and attachments from before a recreation; they win over the intent extras. */
    private var restoredText: String? = null
    private var restoredAttachments: List<Uri> = emptyList()
    private val imageCompressor by lazy { ImageCompressor(this) }

    private val uiState = mutableStateOf(ThreadUiState())
    private var ui: ThreadUiState
        get() = uiState.value
        set(value) {
            val wasSelecting = uiState.value.selecting
            uiState.value = value
            backCallback.isEnabled = value.selecting || value.searching
            // The selection bar takes the composer's place (design view 10). Gone, not empty, so its insets padding goes too.
            updateComposerVisibility()
            if (value.selecting && !wasSelecting) hideKeyboard()
        }
    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    @VisibleForTesting
    internal val snackbarHost = SnackbarHostState()
    @VisibleForTesting
    internal val timelineItems get() = ui.items
    @VisibleForTesting
    internal val timelineSettledAtBottom get() = listAtBottom && !listScrolling
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                ui.selecting -> clearSelection()
                ui.searching -> closeSearch()
            }
        }
    }
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    /** [Message.getStableId]s of failed messages whose Retry is in flight. */
    private val resending = mutableSetOf<Long>()
    private var pendingAttachmentsToSave: List<Attachment>? = null
    private var capturedImageUri: Uri? = null
    @Volatile
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    @Volatile
    private var isJumpingToMessage = false
    private var activeJump = NO_JUMP
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false
    private var spamReasons: Map<Long, Int> = emptyMap()
    private val expandedSpamGroups = HashSet<Long>()
    private var openSpam = false

    private var isScheduledMessage: Boolean = false
    private var lastSearchMatchKey: Long? = null
    private var scheduledMessage: Message? = null
    private lateinit var scheduledDateTime: DateTime

    private val binding by viewBinding(ActivityThreadBinding::inflate)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredText = savedInstanceState?.getString(SAVED_COMPOSER_TEXT)
        restoredAttachments = savedInstanceState?.getStringArrayList(SAVED_COMPOSER_ATTACHMENTS).orEmpty().map { it.toUri() }
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        refreshOpenLineTheme()
        setupCompose()
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.threadComposer)
        )

        val extras = intent.extras
        if (extras == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            ui = ui.copy(header = ui.header.copy(title = it))
        }
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
        refreshOpenLineTheme()
        applySystemBars()
        // mute and starring can change on other screens
        refreshMenuItems()
        publishItems()

        isActivityVisible = true
        binding.threadTimeline.post(updateScrollPosition)

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    setupThreadTitle()
                }
            }

            val smsDraft = getSmsDraft(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread { setComposerText(smsDraft) }
            }
        }

        binding.threadAddContacts.setBackgroundColor(getBottomBarColor())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isActivityVisible) binding.threadTimeline.post(updateScrollPosition)
    }

    override fun onPause() {
        super.onPause()
        saveDraftMessage()
        flushThreadReadState()
        bus?.post(Events.RefreshConversations())
        isActivityVisible = false
        binding.threadTimeline.removeCallbacks(updateScrollPosition)
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
        outState.putString(SAVED_COMPOSER_TEXT, composerText.text)
        // Compressed copies live in the cache; the originals are re-added and compressed again.
        outState.putStringArrayList(SAVED_COMPOSER_ATTACHMENTS, ArrayList(getAttachmentSelections().map { it.id }))
    }

    private fun saveDraftMessage() {
        val draftMessage = composerText.text
        val hasAttachments = getAttachmentSelections().isNotEmpty()
        ensureBackgroundThread {
            if (draftMessage.isNotEmpty() && !hasAttachments) {
                saveSmsDraft(draftMessage, threadId)
            } else {
                deleteSmsDraft(threadId)
            }
        }
    }

    /** Same visibility rules as the old toolbar menu. */
    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable
        val hasItems = threadItems.isNotEmpty()
        val senderBlocked = participants.size == 1 && config.spamNumbers.containsNumber(participants.getAddresses().first())
        val isMuted = config.isConversationMuted(threadId)
        val actions = ThreadMenuAction.entries.filter { action ->
            when (action) {
                ThreadMenuAction.SEARCH -> !isRecycleBin
                ThreadMenuAction.DETAILS -> conversation != null && !isRecycleBin
                ThreadMenuAction.MUTE -> !isRecycleBin && !isMuted
                ThreadMenuAction.UNMUTE -> !isRecycleBin && isMuted
                ThreadMenuAction.MARK_UNREAD -> hasItems && !isRecycleBin
                ThreadMenuAction.RENAME -> participants.size > 1 && conversation != null && !isRecycleBin
                ThreadMenuAction.ADD_PERSON -> !isSpecialNumber() && !isRecycleBin
                // allow saving number in cases when we don't have it stored yet
                ThreadMenuAction.ADD_TO_CONTACT -> participants.size == 1 && participants.first().name == firstPhoneNumber && !isRecycleBin
                ThreadMenuAction.COPY_NUMBER -> participants.size == 1 && !firstPhoneNumber.isNullOrEmpty() && !isRecycleBin
                ThreadMenuAction.ARCHIVE -> hasItems && conversation?.isArchived == false && !isRecycleBin && archiveAvailable
                ThreadMenuAction.UNARCHIVE -> hasItems && conversation?.isArchived == true && !isRecycleBin && archiveAvailable
                // Also the way to unblock, since allowing a number removes it from the blocked set.
                ThreadMenuAction.ALLOW_SENDER -> !isRecycleBin && participants.size == 1 && (senderBlocked || messages.any { isSpam(it) })
                ThreadMenuAction.BLOCK -> !isRecycleBin && !senderBlocked
                ThreadMenuAction.RESTORE -> hasItems && isRecycleBin
                ThreadMenuAction.DELETE -> hasItems
            }
        }
        val title = conversation?.title?.takeIf { it.isNotEmpty() } ?: participants.getThreadTitle().ifEmpty { ui.header.title }
        val specialNumber = isSpecialNumber()
        val subtitle = when {
            participants.size > 1 -> resources.getQuantityString(R.plurals.thread_people, participants.size, participants.size)
            specialNumber -> getString(R.string.thread_automated_sender)
            firstPhoneNumber != null && firstPhoneNumber != title -> firstPhoneNumber
            else -> ""
        }
        ui = ui.copy(
            header = ThreadHeaderState(
                title = title,
                subtitle = subtitle,
                photoUri = conversation?.photoUri?.takeIf { participants.size == 1 } ?: participants.singleOrNull()?.photoUri.orEmpty(),
                isGroup = participants.size > 1,
                // A participant list is required before any action can run.
                canDial = participants.size == 1 && !isSpecialNumber() && !isRecycleBin,
                actions = if (participants.isEmpty()) emptyList() else actions,
            ),
            isGroup = participants.size > 1,
            isRecycleBin = isRecycleBin,
            // A number or a letter short code is not a name to greet.
            firstName = if (participants.size == 1 && title != firstPhoneNumber && !specialNumber && title.any { it.isLetter() }) title.substringBefore(' ') else "",
        )
        composer = composer.copy(visible = !isRecycleBin && threadId !in UndoDeletion.threads, canReply = !specialNumber)
    }

    // The selection bar takes the composer's place (design view 10). Gone, not empty, so its insets padding goes too.
    private fun updateComposerVisibility() {
        binding.threadComposer.beVisibleIf(composer.visible && !ui.selecting)
    }

    private fun setupCompose() {
        binding.threadHeader.setContent {
            OpenLineTheme(dark = darkTheme, textScale = textScale) {
                ThreadHeader(uiState.value, ::onEvent)
            }
        }
        binding.threadTimeline.setContent {
            OpenLineTheme(dark = darkTheme, textScale = textScale) {
                ThreadTimeline(uiState.value, snackbarHost, ::onEvent, onDim = ::dimHeader)
            }
        }
        binding.threadComposer.setContent {
            OpenLineTheme(dark = darkTheme, textScale = textScale) {
                ThreadComposer(composerState.value, uiState.value.firstName, composerText, config.sendOnEnter, ::onComposerTextChange, ::onComposerEvent)
            }
        }
    }

    // The header is its own ComposeView, outside the timeline's lift scrim.
    // setAlpha replaces the colour's own alpha, so the drawable is opaque and the scrim's 40% is applied here.
    private val headerScrim by lazy { ColorDrawable(LiftScrim.copy(alpha = 1f).toArgb()).also { binding.threadHeader.foreground = it } }

    private fun dimHeader(progress: Float) {
        headerScrim.alpha = (progress * LiftScrim.alpha * 255).roundToInt()
    }

    private fun refreshOpenLineTheme() {
        darkTheme = openLineDark()
        textScale = openLineTextScale()
        // setupEdgeToEdge pads this view for the nav bar and keyboard; the padding must be the composer's surfaceContainer too.
        binding.threadComposer.setBackgroundColor(getColor(if (darkTheme) R.color.open_line_night_surface else R.color.design_white))
    }

    private fun applySystemBars() {
        // Status icons sit on the pine header in both themes; the composer below is light or dark.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    private fun onEvent(event: ThreadEvent) {
        when (event) {
            ThreadEvent.Back -> onBackPressedDispatcher.onBackPressed()
            ThreadEvent.ClearSelection -> clearSelection()
            ThreadEvent.OpenDetails -> if (conversation != null && !isRecycleBin) launchConversationDetails(threadId)
            ThreadEvent.Dial -> if (participants.isNotEmpty()) dialNumber()
            is ThreadEvent.Menu -> if (participants.isNotEmpty()) handleMenuItemAction(event.action)
            is ThreadEvent.SearchSubmit -> jumpToNextMatch(event.query)
            ThreadEvent.SearchChanged -> lastSearchMatchKey = null
            ThreadEvent.SearchClose -> closeSearch()
            is ThreadEvent.ToggleSelection -> toggleSelection(event.message)
            is ThreadEvent.Act -> onMessageAction(event.action, event.messages)
            is ThreadEvent.SpamGroup -> {
                toggleSpamGroup(event.group)
                publishItems()
            }
            is ThreadEvent.Retry -> resendMessage(event.message)
            ThreadEvent.NextSim -> nextSim()
            is ThreadEvent.OpenAttachment -> openAttachment(event.message, event.attachment)
            is ThreadEvent.CopyText -> copyToClipboard(event.text)
            ThreadEvent.JumpToLatest -> scrollToBottomFromFab()
            ThreadEvent.LoadOlder -> tryLoadMoreMessages()
            is ThreadEvent.Viewport -> {
                listAtBottom = event.atBottom
                listScrolling = event.scrolling
                binding.threadTimeline.removeCallbacks(updateScrollPosition)
                binding.threadTimeline.post(updateScrollPosition)
            }
            // An older jump's scroll can settle while a newer jump is still walking pages.
            is ThreadEvent.JumpSettled -> if (event.nonce == activeJump) {
                isJumpingToMessage = false
                initialPositionSettled = true
                binding.threadTimeline.post(updateScrollPosition)
            }
            ThreadEvent.InitialScrollSettled -> {
                initialPositionSettled = true
                ui = ui.copy(initialScroll = null)
                binding.threadTimeline.post(updateScrollPosition)
            }
        }
    }

    /** Pushes [items] and everything derived from them to the Compose timeline. Main thread only. */
    private fun publishItems(items: List<ThreadItem> = threadItems) {
        val messageItems = items.filterIsInstance<Message>()
        val keys = messageItems.map { it.getStableId() }.toSet()
        // A retried message may be retried again once it has left the failed state (and failed anew).
        resending.removeAll { key -> messageItems.any { it.getStableId() == key && it.type != Telephony.Sms.MESSAGE_TYPE_FAILED } }
        ui = ui.copy(
            items = items.toList(),
            selected = ui.selected intersect keys,
            starred = messageItems.filter { config.isMessageStarred(it.id, it.isMMS) }.map { it.starKey() }.toSet(),
            spamReasons = spamReasons,
            simLabels = simLabels,
            empty = providerMessagesReady && !isRecycleBin && messages.isEmpty(),
        )
    }

    private fun requestScroll(request: (Long) -> ScrollRequest) {
        ui = ui.copy(scrollRequest = request(++scrollNonce))
    }

    private fun toggleSelection(message: Message) {
        val key = message.getStableId()
        ui = ui.copy(selected = if (key in ui.selected) ui.selected - key else ui.selected + key)
    }

    private fun clearSelection() {
        ui = ui.copy(selected = emptySet())
    }

    private fun closeSearch() {
        hideKeyboard()
        lastSearchMatchKey = null
        ui = ui.copy(searching = false)
    }

    private fun onMessageAction(action: MessageAction, selected: List<Message>) {
        if (selected.isEmpty()) return
        val first = selected.first()
        when (action) {
            MessageAction.COPY -> copyMessages(selected)
            MessageAction.FORWARD -> forwardMessage(first)
            MessageAction.STAR, MessageAction.UNSTAR -> {
                selected.forEach { config.setMessageStarred(it.id, it.isMMS, action == MessageAction.STAR) }
                clearSelection()
                publishItems()
            }
            MessageAction.DELETE -> confirmDeleteMessages(selected, isRecycleBin) { toDelete, toRecycleBin ->
                deleteMessages(toDelete, toRecycleBin, false)
            }
            MessageAction.SHARE -> shareTextIntent(first.body)
            MessageAction.SELECT_TEXT -> if (first.body.isNotBlank()) SelectTextDialog(this, first.body)
            MessageAction.DETAILS -> if (first.isScheduled) showScheduledMessageInfo(first) else MessageDetailsDialog(this, first)
            MessageAction.SAVE_AS -> selected.flatMap { it.attachment?.attachments.orEmpty() }.takeIf { it.isNotEmpty() }?.let { saveMMS(it) }
            MessageAction.NOT_SPAM -> {
                clearSelection()
                unmarkSpam(selected)
            }
            MessageAction.RESTORE -> confirmRestoreMessages(selected) { deleteMessages(it, false, true) }
            MessageAction.SELECT_ALL -> ui = ui.copy(selected = ui.items.filterIsInstance<Message>().map { it.getStableId() }.toSet())
        }
    }

    private fun openAttachment(message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        if (attachment.mimetype.isVCardMimeType()) {
            startActivity(Intent(this, VCardViewerActivity::class.java).putExtra(EXTRA_VCARD_URI, uri))
        } else {
            launchViewIntent(uri, attachment.mimetype, attachment.filename)
        }
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

    /** Each submit steps to the next older match and wraps around. */
    private fun jumpToNextMatch(query: String) {
        val loaded = messages.filter { it.body.contains(query, ignoreCase = true) }
        ensureBackgroundThread {
            // the Room cache also holds messages older than the loaded page
            val cached = messagesDB.getMessagesWithText("%$query%").filter { it.threadId == threadId }
            val matches = (loaded + cached).distinctBy { it.getStableId() }.sortedByDescending { it.date }
            runOnUiThread {
                if (matches.isEmpty()) {
                    toast(R.string.no_matching_messages)
                    return@runOnUiThread
                }

                val current = matches.indexOfFirst { it.getStableId() == lastSearchMatchKey }
                val next = matches[(current + 1) % matches.size]
                lastSearchMatchKey = next.getStableId()
                jumpToMessage(next.id, next.isMMS)
            }
        }
    }

    private fun setConversationMuted(muted: Boolean) {
        config.setConversationMuted(threadId, muted)
        refreshMenuItems()
        toast(if (muted) R.string.conversation_muted else R.string.conversation_unmuted)
    }

    private fun handleMenuItemAction(action: ThreadMenuAction) {
        when (action) {
            ThreadMenuAction.SEARCH -> ui = ui.copy(searching = true)
            ThreadMenuAction.BLOCK -> tryBlocking()
            ThreadMenuAction.ALLOW_SENDER -> unmarkSpam(messages.filter { isSpam(it) }, allowSender = true)
            ThreadMenuAction.DELETE -> askConfirmDelete()
            ThreadMenuAction.RESTORE -> askConfirmRestoreAll()
            ThreadMenuAction.ARCHIVE -> archiveConversation()
            ThreadMenuAction.UNARCHIVE -> unarchiveConversation()
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

                PICK_CONTACT_INTENT -> addContactAttachment(data)
                PICK_SAVE_FILE_INTENT -> saveAttachments(resultData)
                PICK_SAVE_DIR_INTENT -> saveAttachments(resultData)
            }
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
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

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (messages.isEmpty() && !isSpecialNumber()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    updateComposer { copy(focusRequest = focusRequest + 1) }
                }

                setupThreadTitle()
                setupSIMSelector()
                refreshComposer()
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

            val cachedMessagesCode = messages.clone().hashCode()
            if (!isRecycleBin) {
                messages = getMessages(threadId, includeAllUnread = true)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    providerMessagesReady = true
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

                messages.forEach { message ->
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
                messages.chunked(30).forEach { currentMessages ->
                    messagesDB.insertMessages(*currentMessages.toTypedArray())
                }
            }

            providerMessagesReady = true
            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
                callback()
            }
        }
    }

    private fun setupAdapter() {
        loadSpamReasons()
        threadItems = getThreadItems()

        val items = threadItems
        runOnUiThread {
            refreshMenuItems()
            // Inbound refresh preserves the viewport. Sending has its own explicit scroll.
            publishItems(items)
            // Cached rows can be stale about read state, so the landing position waits for the provider.
            if (pendingInitialScroll && (providerMessagesReady || isRecycleBin)) {
                pendingInitialScroll = false
                startInitialScroll()
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            contacts.addAll(privateContacts)
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                binding.addContactOrNumber.setAdapter(adapter)
                binding.addContactOrNumber.imeOptions = EditorInfo.IME_ACTION_NEXT
                binding.addContactOrNumber.setOnItemClickListener { _, _, position, _ ->
                    val currContacts =
                        (binding.addContactOrNumber.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    maybeShowNumberPickerDialog(selectedContact.phoneNumbers) { phoneNumber ->
                        val contactWithSelectedNumber = selectedContact.copy(
                            phoneNumbers = arrayListOf(phoneNumber)
                        )
                        addSelectedContact(contactWithSelectedNumber)
                    }
                }

                binding.addContactOrNumber.onTextChangeListener {
                    binding.confirmInsertedNumber.beVisibleIf(it.length > 2)
                }
            }
        }

        runOnUiThread {
            binding.confirmInsertedNumber.setOnClickListener {
                val number = binding.addContactOrNumber.value
                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = number.hashCode(),
                    contactId = number.hashCode(),
                    name = number,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                addSelectedContact(contact)
            }
        }
    }

    private fun scrollToBottom() {
        requestScroll { ScrollRequest.Bottom(it, smooth = true) }
    }

    private val dictateMessage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?: return@registerForActivityResult
            // Insert, never auto-send: dictation is frequently wrong.
            val at = composerText.selection.min
            onComposerTextChange(TextFieldValue(composerText.text.replaceRange(at, at, spoken), TextRange(at + spoken.length)))
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

    // Spam is read by opening its group, so thread-wide read state leaves it out.
    private fun hasUnreadMessages(): Boolean {
        return messages.any { !it.read && it.isReceivedMessage() && !it.isScheduled && !isSpam(it) }
    }

    private fun flushThreadReadState() {
        // Until the landing position settles, "at the bottom" is only where the list happens to start.
        if (isRecycleBin || !providerMessagesReady || !initialPositionSettled) return
        if (listAtBottom && hasUnreadMessages()) {
            val visibleThreadId = threadId
            ensureBackgroundThread {
                markThreadMessagesRead(visibleThreadId)
                InboxRepository.refreshUnreadCounts(applicationContext, listOf(visibleThreadId))
            }
        }
    }

    private fun markThreadReadIfAtBottom() {
        if (!initialPositionSettled || !providerMessagesReady || isRecycleBin || markReadInProgress ||
            listScrolling || !listAtBottom || !hasUnreadMessages()) {
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
                        applyLocalThreadRead()
                    }
                }
            }
        }
    }

    private fun applyLocalThreadRead() {
        messages = ArrayList(messages.map {
            if (!it.read && it.isReceivedMessage() && !it.isScheduled && !isSpam(it)) it.copy(read = true) else it
        })
        threadItems = getThreadItems()
        publishItems()
    }

    private fun startInitialScroll() {
        if (isRecycleBin) {
            initialPositionSettled = true
            return
        }
        if (openSpam) {
            val spam = threadItems.filterIsInstance<Message>().filter { isSpam(it) }
            markSpamRead(spam.map { it.id })
            val target = spam.firstOrNull { !it.read } ?: spam.lastOrNull()
            if (target != null) {
                ui = ui.copy(initialScroll = InitialScroll.Message(target.id, target.isMMS))
                return
            }
        } else if (threadItems.any { it is ThreadItem.ThreadUnreadSeparator }) {
            ui = ui.copy(initialScroll = InitialScroll.FirstUnread)
            return
        }
        initialPositionSettled = true
    }

    private fun scrollToBottomFromFab() {
        notificationManager.cancel(threadId.hashCode())
        scrollToBottom()
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
                clearSelection()
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
                            if (messages.isEmpty()) finish() else setupThread {}
                        }
                    })
            }
        } else {
            performMessageDeletion(messagesToRemove, toRecycleBin, true)
            runOnUiThread {
                clearSelection()
                if (messages.isEmpty()) finish() else setupThread {}
            }
        }
    }

    private fun performMessageDeletion(messagesToRemove: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        messages.removeAll(messagesToRemove.toSet())
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
        if (messages.isNotEmpty() && messages.all { it.isScheduled }) {
            val scheduledMessage = messages.last()
            val fakeThreadId = generateRandomId()
            createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            updateScheduledMessagesThreadId(messages, fakeThreadId)
            threadId = fakeThreadId
        }
    }

    /**
     * Read state stays blocked until the jump has scrolled ([ThreadEvent.JumpSettled]), and timeline
     * page loads pause, so a page load cannot win the race and drop the jump.
     */
    private fun jumpToMessage(messageId: Long, isMms: Boolean? = null) {
        pendingInitialScroll = false
        isJumpingToMessage = true
        activeJump = NO_JUMP
        ui = ui.copy(initialScroll = null)
        fun Message.isTarget() = id == messageId && (isMms == null || isMMS == isMms)
        if (messages.any { it.isTarget() }) {
            if (revealSpamMessage(messageId)) publishItems()
            requestScroll {
                activeJump = it
                ScrollRequest.ToMessage(it, messageId, isMms)
            }
            return
        }

        ensureBackgroundThread {
            // A page the timeline asked for may still be loading; let it land rather than give up.
            var waits = 0
            while (loadingOlderMessages && waits++ < JUMP_WAIT_LIMIT) Thread.sleep(JUMP_WAIT_MS)
            loadingOlderMessages = true
            val items = try {
                var cutoff = messages.firstOrNull()?.date ?: Int.MAX_VALUE
                var found = messages.any { it.isTarget() }
                var loops = 0

                // not the best solution, but this will do for now.
                while (!found && !allMessagesFetched) {
                    if (fetchOlderMessages(cutoff).isEmpty() || loops >= 1000) break
                    cutoff = messages.first().date
                    found = messages.any { it.isTarget() }
                    loops++
                }

                threadItems = getThreadItems()
                revealSpamMessage(messageId)
                threadItems
            } catch (e: Exception) {
                // Without this, page loads stay blocked for the rest of the visit.
                runOnUiThread {
                    loadingOlderMessages = false
                    isJumpingToMessage = false
                }
                return@ensureBackgroundThread
            }
            runOnUiThread {
                loadingOlderMessages = false
                publishItems(items)
                requestScroll {
                activeJump = it
                ScrollRequest.ToMessage(it, messageId, isMms)
            }
            }
        }
    }

    private fun tryLoadMoreMessages() {
        if (!isJumpingToMessage) loadMoreMessages()
    }

    private fun loadMoreMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) return
        loadingOlderMessages = true
        val cutoff = messages.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            val items = threadItems
            runOnUiThread {
                loadingOlderMessages = false
                publishItems(items)
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> {
        val older = getMessages(threadId, cutoff)
            .filterNotInByKey(messages) { it.getStableId() }

        if (older.isEmpty()) {
            allMessagesFetched = true
            return older
        }

        messages.addAll(0, older)
        return older
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                setupConversation()
                setupCachedMessages {
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        val searchedIsMms = intent.getBooleanExtra(SEARCHED_MESSAGE_IS_MMS, false)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            pendingInitialScroll = false
                            jumpToMessage(searchedMessageId, searchedIsMms)
                        }

                        if (intent.getBooleanExtra(OPEN_THREAD_SEARCH, false)) {
                            intent.removeExtra(OPEN_THREAD_SEARCH)
                            ui = ui.copy(searching = true)
                        }
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun setupConversation() {
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
        }
    }

    private fun setupButtons() = binding.apply {
        updateTextColors(threadHolder)
        confirmManageContacts.applyColorFilter(getProperTextColor())
        confirmManageContacts.setOnClickListener {
            hideKeyboard()
            threadAddContacts.beGone()

            val numbers = HashSet<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }

            val newThreadId = getThreadId(numbers)
            if (threadId != newThreadId) {
                hideKeyboard()
                Intent(this@ThreadActivity, ThreadActivity::class.java).apply {
                    putExtra(THREAD_ID, newThreadId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
        }

        val restored = restoredText
        if (restored != null) {
            setComposerText(restored)
            restoredAttachments.forEach { addAttachment(it) }
            return@apply
        }
        intent.getStringExtra(THREAD_TEXT)?.let { setComposerText(it) }
        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
            addAttachment(uri)
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                addAttachment(it)
            }
        }
    }

    private fun updateComposer(change: ComposerState.() -> ComposerState) {
        composer = composer.change()
    }

    private fun setComposerText(text: String) = onComposerTextChange(TextFieldValue(text, TextRange(text.length)))

    private fun onComposerTextChange(value: TextFieldValue) {
        val changed = value.text != composerText.text
        composerText = value
        if (changed) refreshComposer()
    }

    private fun onComposerEvent(event: ComposerEvent) {
        when (event) {
            ComposerEvent.Send -> sendMessage()
            ComposerEvent.Dictate -> launchDictation()
            is ComposerEvent.Attach -> attach(event.option)
            ComposerEvent.Schedule -> launchScheduleSendDialog(if (isScheduledMessage) scheduledDateTime else null)
            ComposerEvent.CancelSchedule -> {
                hideScheduleSendUi()
                scheduledMessage?.let { cancelScheduledMessageAndRefresh(it.id) }
                scheduledMessage = null
            }
            ComposerEvent.NextSim -> nextSim()
            is ComposerEvent.RemoveAttachment -> removeAttachment(event.attachment.id)
            is ComposerEvent.OpenAttachment -> event.attachment.let {
                if (it.mimetype.isVCardMimeType()) {
                    startActivity(Intent(this, VCardViewerActivity::class.java).putExtra(EXTRA_VCARD_URI, it.uri))
                } else {
                    launchViewIntent(it.uri, it.mimetype, it.filename)
                }
            }
            ComposerEvent.ReplyInfo -> InvalidNumberDialog(activity = this, text = getString(R.string.invalid_short_code_desc))
        }
    }

    private fun attach(option: AttachOption) = when (option) {
        AttachOption.PHOTO -> launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
        AttachOption.CAMERA -> launchCapturePhotoIntent()
        AttachOption.VIDEO -> launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
        AttachOption.RECORD_VIDEO -> launchCaptureVideoIntent()
        AttachOption.AUDIO -> launchCaptureAudioIntent()
        AttachOption.FILE -> launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
        AttachOption.CONTACT -> launchPickContactIntent()
        AttachOption.SEND_LATER -> launchScheduleSendDialog(if (isScheduledMessage) scheduledDateTime else null)
    }

    /** Length pill (R6-45): only near the SMS limit or with the counter setting on; an MMS shows "MMS" instead. */
    private fun refreshComposer() {
        val text = composerText.text
        val attachments = getAttachmentSelections()
        val mms = isMmsMessage(text) && (text.isNotEmpty() || attachments.isNotEmpty())
        var parts = 0
        var remaining = 0
        if (!mms && text.isNotEmpty()) {
            val length = SmsMessage.calculateLength(if (config.useSimpleCharacters) text.normalizeString() else text, false)
            if (config.showCharacterCounter || length[0] > 1 || length[2] <= SMS_COUNTER_THRESHOLD) {
                parts = length[0]
                remaining = length[2]
            }
        }
        updateComposer { copy(isMms = mms, smsParts = parts, smsLeft = remaining) }
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = org.fossify.commons.R.string.allow_alarm_scheduled_messages,
                    positiveActionCallback = {
                        openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                    },
                )
            }
        } else {
            callback()
        }
    }

    private fun setupParticipants() {
        if (participants.isEmpty()) {
            participants = if (messages.isEmpty()) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                messages.first().participants
            }
            runOnUiThread {
                maybeDisableShortCodeReply()
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
            setComposerText("")
            refreshMenuItems()
        }
    }

    private fun setupThreadTitle() = refreshMenuItems()

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
                val simCard = SIMCard(slot, subscriptionInfo.subscriptionId, label)
                availableSIMCards.add(simCard)
            }

            val numbers = ArrayList<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }

            if (numbers.isEmpty()) {
                return
            }

            currentSIMCardIndex = getProperSimIndex(availableSIMs, numbers)
            updateComposer { copy(sims = availableSIMCards.map { SimOption(it.id, it.label) }, simIndex = currentSIMCardIndex) }
            ui = ui.copy(sendSim = availableSIMCards[currentSIMCardIndex].id)
        }
    }

    private fun nextSim() {
        if (availableSIMCards.size < 2) return
        val index = (currentSIMCardIndex + 1) % availableSIMCards.size
        val simCard = availableSIMCards[index]
        currentSIMCardIndex = index
        participants.flatMap { contact -> contact.phoneNumbers.map { it.normalizedNumber } }.forEach {
            config.saveUseSIMIdAtNumber(it, simCard.subscriptionId)
        }
        updateComposer { copy(simIndex = index) }
        ui = ui.copy(sendSim = simCard.id)
    }

    @SuppressLint("MissingPermission")
    private fun getProperSimIndex(
        availableSIMs: MutableList<SubscriptionInfo>,
        numbers: List<String>,
    ): Int {
        val userPreferredSimId = config.getUseSIMIdAtNumber(numbers.first())
        val userPreferredSimIdx =
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val lastMessage = messages.lastOrNull()
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

    private fun tryBlocking() {
        blockNumber()
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(this, question) {
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
        ConfirmationDialog(this, getString(R.string.delete_whole_conversation_confirmation)) {
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
        ConfirmationDialog(this, getString(R.string.restore_confirmation)) {
            ensureBackgroundThread {
                restoreAllMessagesFromRecycleBinForConversation(threadId)
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun archiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, true)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun unarchiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, false)
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

    private fun managePeople() {
        if (binding.threadAddContacts.isVisible()) {
            hideKeyboard()
            binding.threadAddContacts.beGone()
        } else {
            showSelectedContacts()
            binding.threadAddContacts.beVisible()
            binding.addContactOrNumber.requestFocus()
            showKeyboard(binding.addContactOrNumber)
        }
    }

    private fun showSelectedContacts() {
        val properPrimaryColor = getProperPrimaryColor()

        val views = ArrayList<View>()
        participants.forEach { contact ->
            ItemSelectedContactBinding.inflate(layoutInflater).apply {
                val selectedContactBg =
                    AppCompatResources.getDrawable(
                        this@ThreadActivity,
                        R.drawable.item_selected_contact_background
                    )
                (selectedContactBg as LayerDrawable).findDrawableByLayerId(R.id.selected_contact_bg)
                    .applyColorFilter(properPrimaryColor)
                selectedContactHolder.background = selectedContactBg

                selectedContactName.text = contact.name
                selectedContactName.setTextColor(properPrimaryColor.getContrastColor())
                selectedContactRemove.applyColorFilter(properPrimaryColor.getContrastColor())

                selectedContactRemove.setOnClickListener {
                    if (contact.rawId != participants.first().rawId) {
                        removeSelectedContact(contact.rawId)
                    }
                }
                views.add(root)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: SimpleContact) {
        binding.addContactOrNumber.setText("")
        if (participants.map { it.rawId }.contains(contact.rawId)) {
            return
        }

        participants.add(contact)
        showSelectedContacts()
        refreshComposer()
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
        RenameConversationDialog(this, conversation!!) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(conversation!!, newTitle = title)
                runOnUiThread {
                    setupThreadTitle()
                }
            }
        }
    }

    private fun getThreadItems(): ArrayList<ThreadItem> {
        if (isFinishing) return ArrayList()
        messages.sortBy { it.date }
        return buildThreadItems(
            messages = messages,
            isSpam = ::isSpam,
            isHidden = { it.getStableId() in UndoDeletion.messages || threadId in UndoDeletion.threads },
            // In OPEN_SPAM mode the set holds the collapsed groups instead of the expanded ones.
            isExpanded = { (it in expandedSpamGroups) != openSpam },
        )
    }

    private fun isSpam(message: Message) = !message.isMMS && message.id in spamReasons

    private fun loadSpamReasons() {
        spamReasons = messagesDB.getThreadSpamMarkers(threadId).associate { it.id to it.reason }
    }

    private fun toggleSpamGroup(group: ThreadSpamGroup) {
        if (!expandedSpamGroups.remove(group.key)) expandedSpamGroups.add(group.key)
        if (!group.expanded) markSpamRead(group.messageIds)
        threadItems = getThreadItems()
    }

    // A jump to a message inside a collapsed spam group opens the group first.
    private fun revealSpamMessage(messageId: Long): Boolean {
        val group = threadItems.firstOrNull { it is ThreadSpamGroup && !it.expanded && messageId in it.messageIds } as? ThreadSpamGroup
            ?: return false
        toggleSpamGroup(group)
        return true
    }

    // Opening a spam group is what marks its spam read.
    private fun markSpamRead(ids: Collection<Long>) {
        val unreadIds = messages.filter { it.id in ids && isSpam(it) && !it.read }.map { it.id }.toSet()
        if (unreadIds.isEmpty()) return
        messages = ArrayList(messages.map { if (!it.isMMS && it.id in unreadIds) it.copy(read = true) else it })
        ensureBackgroundThread {
            unreadIds.forEach { markMessageRead(it, isMMS = false) }
        }
    }

    private fun unmarkSpam(selected: List<Message>, allowSender: Boolean = false) {
        ensureBackgroundThread {
            if (allowSender) {
                (selected.map { it.senderPhoneNumber } + participants.getAddresses())
                    .filter { it.isNotEmpty() }.distinct().forEach { config.addAllowedNumber(it) }
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

    private fun getAttachmentsDir(): File {
        return File(cacheDir, "attachments").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun launchCapturePhotoIntent() {
        val imageFile = File.createTempFile("attachment_", ".jpg", getAttachmentsDir())
        capturedImageUri = getMyFileUri(imageFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
        }
        launchActivityForResult(intent, CAPTURE_PHOTO_INTENT)
    }

    private fun launchCaptureVideoIntent() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        launchActivityForResult(intent, CAPTURE_VIDEO_INTENT)
    }

    private fun launchCaptureAudioIntent() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        launchActivityForResult(intent, CAPTURE_AUDIO_INTENT)
    }

    private fun launchGetContentIntent(mimeTypes: Array<String>, requestCode: Int) {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            launchActivityForResult(this, requestCode)
        }
    }

    private fun launchPickContactIntent() {
        Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            launchActivityForResult(this, PICK_CONTACT_INTENT)
        }
    }

    private fun addContactAttachment(contactUri: Uri) {
        ensureBackgroundThread {
            val contact = ContactsHelper(this).getContactFromUri(contactUri)
            if (contact != null) {
                val outputFile = File(getAttachmentsDir(), "${contact.contactId}.vcf")
                val outputStream = outputFile.outputStream()

                VcfExporter().exportContacts(
                    activity = this,
                    outputStream = outputStream,
                    contacts = arrayListOf(contact),
                    showExportingToast = false,
                ) {
                    if (it == ExportResult.EXPORT_OK) {
                        val vCardUri = getMyFileUri(outputFile)
                        runOnUiThread {
                            addAttachment(vCardUri)
                        }
                    } else {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }

    private fun getAttachmentSelections() = composer.attachments

    private fun setAttachments(attachments: List<AttachmentSelection>) {
        updateComposer { copy(attachments = attachments) }
        refreshComposer()
    }

    private fun removeAttachment(id: String) = setAttachments(getAttachmentSelections().filterNot { it.id == id })

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (getAttachmentSelections().any { it.id == id }) {
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
        setAttachments(getAttachmentSelections() + selection)
        if (!compress) return
        imageCompressor.compressImage(uri, mmsFileSizeLimit) { compressedUri ->
            runOnUiThread {
                // By identity: removed, sent, or removed and added again while this one was compressing.
                if (isDestroyed || getAttachmentSelections().none { it === selection }) return@runOnUiThread
                if (compressedUri == null) {
                    toast(R.string.compress_error)
                    setAttachments(getAttachmentSelections().filterNot { it === selection })
                } else {
                    setAttachments(getAttachmentSelections().map { if (it === selection) it.copy(uri = compressedUri, isPending = false) else it })
                }
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

    private fun getOutgoingText() = composerText.text

    private fun sendMessage() {
        // Also while an image is still compressing: the original may be over the MMS limit.
        if (!composer.hasContent(getOutgoingText())) return
        scrollToBottom()

        val text = removeDiacriticsIfNeeded(getOutgoingText())

        val subscriptionId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
            ?: SmsManager.getDefaultSmsSubscriptionId()

        if (isScheduledMessage) {
            sendScheduledMessage(text, subscriptionId)
        } else {
            sendNormalMessage(text, subscriptionId)
        }
    }

    private fun sendScheduledMessage(text: String, subscriptionId: Int) {
        if (scheduledDateTime.millis < System.currentTimeMillis() + 1000L) {
            toast(R.string.must_pick_time_in_the_future)
            launchScheduleSendDialog(scheduledDateTime)
            return
        }

        refreshedSinceSent = false
        try {
            ensureBackgroundThread {
                val messageId = scheduledMessage?.id ?: generateRandomId()
                val message = buildScheduledMessage(text, subscriptionId, messageId)
                if (messages.isEmpty()) {
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
                    clearCurrentMessage()
                    hideScheduleSendUi()
                    scheduledMessage = null
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
                    .filterNotInByKey(messages) { it.getStableId() }
                for (message in messages) {
                    insertOrUpdateMessage(message)
                }
            }
            clearCurrentMessage()

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
        if (!resending.add(message.getStableId())) return
        val subscriptionId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
            ?: SmsManager.getDefaultSmsSubscriptionId()
        try {
            refreshedSinceSent = false
            sendMessageCompat(
                message.body, participants.getAddresses(), subscriptionId, message.attachment?.attachments.orEmpty(), message,
                // Today's settings must not turn a failed MMS into an SMS, which could not replace its row.
                forceMms = message.isMMS,
            )
            refreshMessages()
        } catch (e: Exception) {
            resending.remove(message.getStableId())
            showErrorToast(e)
        } catch (e: Error) {
            resending.remove(message.getStableId())
            showErrorToast(e.localizedMessage ?: getString(org.fossify.commons.R.string.unknown_error_occurred))
        }
    }

    private fun clearCurrentMessage() {
        setComposerText("")
        setAttachments(emptyList())
    }

    private fun insertOrUpdateMessage(message: Message) {
        if (messages.map { it.id }.contains(message.id)) {
            val messageToReplace = messages.find { it.id == message.id }
            messages[messages.indexOf(messageToReplace)] = message
        } else {
            messages.add(message)
        }

        val newItems = getThreadItems()
        runOnUiThread {
            threadItems = newItems
            publishItems(newItems)
            scrollToBottom()
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

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        binding.selectedContacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin =
            (binding.selectedContacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val mediumMargin = resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()
        val parentWidth = realScreenSize.x - sideMargin * 2
        val firstRowWidth =
            parentWidth - resources.getDimension(org.fossify.commons.R.dimen.normal_icon_size)
                .toInt() + sideMargin / 2
        var widthSoFar = 0
        var isFirstRow = true

        for (i in views.indices) {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            layout.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LayoutParams(views[i].measuredWidth, LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            layout.addView(views[i], params)
            layout.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            val checkWidth = if (isFirstRow) firstRowWidth else parentWidth
            if (widthSoFar >= checkWidth) {
                isFirstRow = false
                binding.selectedContacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LayoutParams(layout.measuredWidth, layout.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(layout, params)
                widthSoFar = layout.measuredWidth
            } else {
                if (!isFirstRow) {
                    (layout.layoutParams as LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(layout)
            }
        }
        binding.selectedContacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        participants =
            participants.filter { it.rawId != id }.toMutableList() as ArrayList<SimpleContact>
        showSelectedContacts()
        refreshComposer()
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

    fun saveMMS(attachments: List<Attachment>) {
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

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin) {
            return
        }

        refreshedSinceSent = true
        allMessagesFetched = false

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val lastMaxId = messages.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = getThreadId(participants.getAddresses().toSet())
        val newMessages = getMessages(newThreadId, includeScheduledMessages = false,
            includeAllUnread = true, oldestLoadedDate = messages.firstOrNull { !it.isScheduled }?.date)
        if (messages.isNotEmpty() && messages.all { it.isScheduled } && newMessages.isNotEmpty()) {
            // update scheduled messages with real thread id
            threadId = newThreadId
            updateScheduledMessagesThreadId(
                messages = messages.filter { it.threadId != threadId },
                newThreadId = threadId
            )
        }

        messages = newMessages.apply {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
                .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            addAll(scheduledMessages)
            if (config.useRecycleBin) {
                val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId).toSet()
                removeAll(recycledMessages)
            }
        }

        messages.filter { !it.isScheduled && !it.isReceivedMessage() && it.id > lastMaxId }
            .forEach { latestMessage ->
                messagesDB.insertOrIgnore(latestMessage)
            }

        setupAdapter()
        runOnUiThread {
            setupSIMSelector()
        }
    }

    private fun isMmsMessage(text: String): Boolean {
        val isGroupMms = participants.size > 1 && config.sendGroupMessageMMS
        val isLongMmsMessage = isLongMmsMessage(text)
        return getAttachmentSelections().isNotEmpty() || isGroupMms || isLongMmsMessage
    }

    private fun showScheduledMessageInfo(message: Message) {
        val items = arrayListOf(
            RadioItem(TYPE_EDIT, getString(R.string.update_message)),
            RadioItem(TYPE_SEND, getString(R.string.send_now)),
            RadioItem(TYPE_DELETE, getString(org.fossify.commons.R.string.delete))
        )
        RadioGroupDialog(
            activity = this,
            items = items,
            titleId = R.string.scheduled_message
        ) { any ->
            when (any as Int) {
                TYPE_DELETE -> deleteMessages(listOf(message), false, false)
                TYPE_EDIT -> editScheduledMessage(message)
                TYPE_SEND -> {
                    messages.removeAll { message.id == it.id }
                    extractAttachments(message)
                    sendNormalMessage(message.body, message.subscriptionId)
                    cancelScheduledMessageAndRefresh(message.id)
                }
            }
        }
    }

    private fun extractAttachments(message: Message) {
        val messageAttachment = message.attachment
        if (messageAttachment != null) {
            for (attachment in messageAttachment.attachments) {
                addAttachment(attachment.getUri())
            }
        }
    }

    private fun editScheduledMessage(message: Message) {
        scheduledMessage = message
        clearCurrentMessage()
        setComposerText(message.body)
        extractAttachments(message)
        scheduledDateTime = DateTime(message.millis())
        showScheduleMessageDialog()
    }

    private fun cancelScheduledMessageAndRefresh(messageId: Long) {
        ensureBackgroundThread {
            deleteScheduledMessage(messageId)
            cancelScheduleSendPendingIntent(messageId)
            refreshMessages()
        }
    }

    private fun launchScheduleSendDialog(originalDateTime: DateTime? = null) {
        askForExactAlarmPermissionIfNeeded {
            ScheduleMessageDialog(this, originalDateTime) { newDateTime ->
                if (newDateTime != null) {
                    scheduledDateTime = newDateTime
                    showScheduleMessageDialog()
                }
            }
        }
    }

    private fun showScheduleMessageDialog() {
        isScheduledMessage = true
        val dateTime = scheduledDateTime
        val millis = dateTime.millis
        val label = if (dateTime.yearOfCentury().get() > DateTime.now().yearOfCentury().get()) {
            millis.formatDate(this)
        } else {
            val flags = FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_NO_YEAR
            DateUtils.formatDateTime(this, millis, flags)
        }
        updateComposer { copy(scheduledAt = label) }
    }

    private fun hideScheduleSendUi() {
        isScheduledMessage = false
        updateComposer { copy(scheduledAt = null) }
    }

    private fun buildScheduledMessage(text: String, subscriptionId: Int, messageId: Long): Message {
        val threadId = if (messages.isEmpty()) messageId else threadId
        return Message(
            id = messageId,
            body = text,
            type = MESSAGE_TYPE_QUEUED,
            status = STATUS_NONE,
            participants = participants,
            date = (scheduledDateTime.millis / 1000).toInt(),
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

    private fun buildMessageAttachments(messageId: Long = -1L) = getAttachmentSelections()
        .map { Attachment(null, messageId, it.uri.toString(), it.mimetype, 0, 0, it.filename) }
        .toArrayList()

    private fun getBottomBarColor() = if (isDynamicTheme()) {
        resources.getColor(org.fossify.commons.R.color.you_bottom_bar_color)
    } else {
        getBottomNavigationBackgroundColor()
    }

    companion object {
        private const val TYPE_EDIT = 14
        private const val TYPE_SEND = 15
        private const val TYPE_DELETE = 16
        private const val JUMP_WAIT_MS = 50L
        private const val JUMP_WAIT_LIMIT = 100
        private const val NO_JUMP = -1L
        // SmsMessage code units left in the current part before the counter appears.
        private const val SMS_COUNTER_THRESHOLD = 20
        private const val SAVED_COMPOSER_TEXT = "composer_text"
        private const val SAVED_COMPOSER_ATTACHMENTS = "composer_attachments"
    }
}

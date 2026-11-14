package com.afsh.messages.ui.thread

import android.net.Uri
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Message
import com.afsh.messages.models.SIMCard
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.newmessage.NewMessageUiState
import com.afsh.messages.ui.newmessage.Recipient

/**
 * What the conversation screen reads and calls. The screen knows nothing else about the conversation;
 * [ThreadStateHolder] is the implementation.
 */
interface ThreadScreenModel {
    val ui: ThreadUiState
    val composer: ComposerState
    val composerText: TextFieldValue
    val people: ManagePeopleState
    /** How far the timeline's lift scrim dims the header, 0 to 1. */
    val headerDim: Float

    fun onEvent(event: ThreadEvent)
    fun onComposerEvent(event: ComposerEvent)
    fun onComposerTextChange(value: TextFieldValue)
    fun onPeopleEvent(event: PeopleEvent)
    fun onDim(progress: Float)
}

sealed interface PeopleEvent {
    data class Add(val recipient: Recipient) : PeopleEvent
    data class Remove(val recipient: Recipient) : PeopleEvent
    data object Done : PeopleEvent
    data object AllowContacts : PeopleEvent
}

/** What the conversation is, as the header and its menu need it. Built by the host from the provider and settings. */
data class ThreadFacts(
    val title: String = "",
    val photoUri: String = "",
    val participantCount: Int = 0,
    val firstNumber: String? = null,
    /** The single participant's name, which equals [firstNumber] for a number not in contacts. */
    val firstName: String? = null,
    val hasConversation: Boolean = false,
    /** Null until the conversation row has loaded. */
    val archived: Boolean? = null,
    val archiveAvailable: Boolean = true,
    val hasItems: Boolean = false,
    val isRecycleBin: Boolean = false,
    val senderBlocked: Boolean = false,
    val muted: Boolean = false,
    /** A letter short code, which cannot receive replies. */
    val specialNumber: Boolean = false,
    val hasSpam: Boolean = false,
    /** A whole-thread delete is waiting for its Undo. */
    val deletePending: Boolean = false,
    /** Already worded by the host: "3 people" and "Automated sender". */
    val groupSubtitle: String = "",
    val specialSubtitle: String = "",
)

/** Same visibility rules as the old toolbar menu. */
fun threadMenuActions(facts: ThreadFacts): List<ThreadMenuAction> = with(facts) {
    val single = participantCount == 1
    ThreadMenuAction.entries.filter { action ->
        when (action) {
            ThreadMenuAction.SEARCH -> !isRecycleBin
            ThreadMenuAction.DETAILS -> hasConversation && !isRecycleBin
            ThreadMenuAction.MUTE -> !isRecycleBin && !muted
            ThreadMenuAction.UNMUTE -> !isRecycleBin && muted
            ThreadMenuAction.MARK_UNREAD -> hasItems && !isRecycleBin
            ThreadMenuAction.RENAME -> participantCount > 1 && hasConversation && !isRecycleBin
            ThreadMenuAction.ADD_PERSON -> !specialNumber && !isRecycleBin
            // allow saving number in cases when we don't have it stored yet
            ThreadMenuAction.ADD_TO_CONTACT -> single && firstName == firstNumber && !isRecycleBin
            ThreadMenuAction.COPY_NUMBER -> single && !firstNumber.isNullOrEmpty() && !isRecycleBin
            ThreadMenuAction.ARCHIVE -> hasItems && archived == false && !isRecycleBin && archiveAvailable
            ThreadMenuAction.UNARCHIVE -> hasItems && archived == true && !isRecycleBin && archiveAvailable
            // Also the way to unblock, since allowing a number removes it from the blocked set.
            ThreadMenuAction.ALLOW_SENDER -> !isRecycleBin && single && (senderBlocked || hasSpam)
            ThreadMenuAction.BLOCK -> !isRecycleBin && !senderBlocked
            ThreadMenuAction.RESTORE -> hasItems && isRecycleBin
            ThreadMenuAction.DELETE -> hasItems
        }
    }
}

/** The header for [facts]; [fallbackTitle] is the title the thread opened with, until participants load. */
fun threadHeader(facts: ThreadFacts, fallbackTitle: String): ThreadHeaderState = with(facts) {
    val title = this.title.ifEmpty { fallbackTitle }
    ThreadHeaderState(
        title = title,
        subtitle = when {
            participantCount > 1 -> groupSubtitle
            specialNumber -> specialSubtitle
            firstNumber != null && firstNumber != title -> firstNumber
            else -> ""
        },
        photoUri = photoUri,
        isGroup = participantCount > 1,
        // A participant list is required before any action can run.
        canDial = participantCount == 1 && !specialNumber && !isRecycleBin,
        actions = if (participantCount == 0) emptyList() else threadMenuActions(facts),
    )
}

/** "Say hello to …" takes a first name; a number or a letter short code is not a name to greet. */
fun greetingName(facts: ThreadFacts, title: String): String =
    if (facts.participantCount == 1 && title != facts.firstNumber && !facts.specialNumber && title.any { it.isLetter() }) {
        title.substringBefore(' ')
    } else {
        ""
    }

/** Where the list lands when the provider's messages first arrive; null means it is already settled. */
fun landing(isRecycleBin: Boolean, openSpam: Boolean, items: List<ThreadItem>, isSpam: (Message) -> Boolean): InitialScroll? {
    if (isRecycleBin) return null
    if (openSpam) {
        val spam = items.filterIsInstance<Message>().filter(isSpam)
        val target = spam.firstOrNull { !it.read } ?: spam.lastOrNull()
        if (target != null) return InitialScroll.Message(target.id, target.isMMS)
    } else if (items.any { it is ThreadItem.ThreadUnreadSeparator }) {
        return InitialScroll.FirstUnread
    }
    return null
}

/** Each submit steps to the next older match and wraps around. [matches] are newest first. */
fun nextMatch(matches: List<Message>, lastKey: Long?): Message? {
    if (matches.isEmpty()) return null
    val current = matches.indexOfFirst { it.getStableId() == lastKey }
    return matches[(current + 1) % matches.size]
}

/**
 * The conversation screen's state and the conversation logic that needs no Android: selection, search,
 * scrolling and jumps, read-state gating, the composer, the send SIM and the Manage people panel. Everything
 * that reaches the provider, settings or another screen goes to [host].
 *
 * Main thread only, apart from [isJumping], which a background page load reads.
 */
class ThreadStateHolder(private val host: Host) : ThreadScreenModel {

    interface Host {
        /** Events that reach the provider, settings, another screen or the system. */
        fun onEvent(event: ThreadEvent)
        fun onComposerEvent(event: ComposerEvent)
        fun onPeopleEvent(event: PeopleEvent)
        fun hideKeyboard()
        /** The viewport or landing changed: the host checks whether the thread can now be marked read. */
        fun viewportChanged()
        /** Back is handled here while this is true. */
        fun backEnabledChanged(enabled: Boolean)
        /** The send SIM was switched by hand; the host remembers it for the participants. */
        fun simChosen(subscriptionId: Int)
        /** Whether [text] goes as MMS (attachments, group MMS, long text). */
        fun isMms(text: String, hasAttachments: Boolean): Boolean
        /** SMS parts and characters left in the last one, or null with the counter setting off. */
        fun smsLength(text: String): Pair<Int, Int>?
    }

    private var uiValue by mutableStateOf(ThreadUiState())
    override val ui: ThreadUiState get() = uiValue

    /** Apart from [ui]: the counter changes as you type, and the timeline must not recompose for it. */
    private var composerValue by mutableStateOf(ComposerState())
    override val composer: ComposerState get() = composerValue

    /** Apart from [ui], so typing does not rebuild the timeline state. */
    private var textValue by mutableStateOf(TextFieldValue(""))
    override val composerText: TextFieldValue get() = textValue

    private var peopleValue by mutableStateOf(ManagePeopleState())
    override val people: ManagePeopleState get() = peopleValue

    private val dim = mutableFloatStateOf(0f)
    override val headerDim get() = dim.floatValue

    val handlesBack get() = people.open || ui.selecting || ui.searching

    // Viewport and landing.
    var listAtBottom = true
        private set
    var listScrolling = false
        private set
    /** Until the landing position settles, "at the bottom" is only where the list happens to start. */
    var initialPositionSettled = false
        private set
    /** The landing waits for the provider's messages, since cached rows can be stale about read state. */
    var pendingLanding = true
        private set
    private var scrollNonce = 0L
    private var activeJump = NO_JUMP
    @Volatile
    var isJumping = false
        private set
    private var lastSearchMatchKey: Long? = null

    // Send SIM.
    private var sims: List<SIMCard> = emptyList()
    private var simIndex = 0
    val sendSubscriptionId: Int? get() = sims.getOrNull(simIndex)?.subscriptionId

    /** [Message.getStableId]s of failed messages whose Retry is in flight. */
    private val resending = mutableSetOf<Long>()

    /** The picked send time, or null when sending now. */
    var scheduledMillis: Long? = null
        private set

    val text get() = composerText.text
    val attachments get() = composer.attachments

    private fun backChanged() = host.backEnabledChanged(handlesBack)

    private fun setUi(value: ThreadUiState) {
        val wasSelecting = uiValue.selecting
        uiValue = value
        backChanged()
        if (value.selecting && !wasSelecting) host.hideKeyboard()
    }

    private fun setPeople(value: ManagePeopleState) {
        peopleValue = value
        backChanged()
    }

    // region ThreadScreenModel

    override fun onEvent(event: ThreadEvent) {
        when (event) {
            ThreadEvent.ClearSelection -> clearSelection()
            is ThreadEvent.ToggleSelection -> {
                val key = event.message.getStableId()
                setUi(ui.copy(selected = if (key in ui.selected) ui.selected - key else ui.selected + key))
            }
            is ThreadEvent.Act -> if (event.action == MessageAction.SELECT_ALL && event.messages.isNotEmpty()) {
                setUi(ui.copy(selected = ui.items.filterIsInstance<Message>().map { it.getStableId() }.toSet()))
            } else {
                host.onEvent(event)
            }
            ThreadEvent.SearchChanged -> lastSearchMatchKey = null
            ThreadEvent.SearchClose -> closeSearch()
            ThreadEvent.NextSim -> nextSim()
            ThreadEvent.LoadOlder -> if (!isJumping) host.onEvent(event)
            is ThreadEvent.Viewport -> {
                listAtBottom = event.atBottom
                listScrolling = event.scrolling
                host.viewportChanged()
            }
            // An older jump's scroll can settle while a newer jump is still walking pages.
            is ThreadEvent.JumpSettled -> if (event.nonce == activeJump) {
                isJumping = false
                initialPositionSettled = true
                host.viewportChanged()
            }
            ThreadEvent.InitialScrollSettled -> {
                initialPositionSettled = true
                setUi(ui.copy(initialScroll = null))
                host.viewportChanged()
            }
            else -> host.onEvent(event)
        }
    }

    override fun onComposerEvent(event: ComposerEvent) {
        when (event) {
            ComposerEvent.CancelSchedule -> clearSchedule()
            ComposerEvent.NextSim -> nextSim()
            is ComposerEvent.RemoveAttachment -> setAttachments(attachments.filterNot { it.id == event.attachment.id })
            else -> host.onComposerEvent(event)
        }
    }

    override fun onComposerTextChange(value: TextFieldValue) {
        val changed = value.text != composerText.text
        textValue = value
        if (changed) refreshComposer()
    }

    override fun onPeopleEvent(event: PeopleEvent) = host.onPeopleEvent(event)

    override fun onDim(progress: Float) {
        dim.floatValue = progress
    }

    // endregion

    /** Back: closes Manage people, then the selection, then search. False when there is nothing to close. */
    fun back(): Boolean {
        when {
            people.open -> closePeople()
            ui.selecting -> clearSelection()
            ui.searching -> closeSearch()
            else -> return false
        }
        return true
    }

    // region Header and timeline

    /** Applies what the conversation is to the header, the greeting and the composer. */
    fun showFacts(facts: ThreadFacts) {
        val header = threadHeader(facts, ui.header.title)
        setUi(
            ui.copy(
                header = header,
                isGroup = facts.participantCount > 1,
                isRecycleBin = facts.isRecycleBin,
                firstName = greetingName(facts, header.title),
            ),
        )
        composerValue = composer.copy(
            visible = !facts.isRecycleBin && !facts.deletePending,
            canReply = !facts.specialNumber,
            blocked = facts.senderBlocked,
        )
    }

    /** The title from the intent, shown until the participants load. */
    fun showTitle(title: String) {
        setUi(ui.copy(header = ui.header.copy(title = title)))
    }

    /** Pushes [items] and what is derived from them; [starred] are [starKey]s. */
    fun showItems(
        items: List<ThreadItem>,
        starred: Set<Long>,
        spamReasons: Map<Long, Int>,
        simLabels: Map<Int, String>,
        empty: Boolean,
    ) {
        val messageItems = items.filterIsInstance<Message>()
        val keys = messageItems.mapTo(HashSet()) { it.getStableId() }
        // A retried message may be retried again once it has left the failed state (and failed anew).
        resending.removeAll { key -> messageItems.any { it.getStableId() == key && it.type != Telephony.Sms.MESSAGE_TYPE_FAILED } }
        setUi(
            ui.copy(
                items = items.toList(),
                selected = ui.selected intersect keys,
                starred = starred,
                spamReasons = spamReasons,
                simLabels = simLabels,
                empty = empty,
            ),
        )
    }

    fun clearSelection() {
        setUi(ui.copy(selected = emptySet()))
    }

    // endregion

    // region Search

    fun openSearch() {
        setUi(ui.copy(searching = true))
    }

    fun closeSearch() {
        host.hideKeyboard()
        lastSearchMatchKey = null
        setUi(ui.copy(searching = false))
    }

    /** The next match to jump to, newest first and wrapping; null when nothing matches. */
    fun stepSearch(matches: List<Message>): Message? = nextMatch(matches, lastSearchMatchKey)?.also {
        lastSearchMatchKey = it.getStableId()
    }

    // endregion

    // region Scrolling, landing and read state

    fun scrollToBottom() {
        setUi(ui.copy(scrollRequest = ScrollRequest.Bottom(++scrollNonce, smooth = true)))
    }

    /**
     * Starts the landing once the provider's messages are in (or at once in the Recycle Bin). Runs once per
     * visit; a jump that starts first cancels it.
     */
    fun land(ready: Boolean, target: () -> InitialScroll?) {
        if (!pendingLanding || !ready) return
        pendingLanding = false
        val scroll = target()
        if (scroll == null) {
            initialPositionSettled = true
        } else {
            setUi(ui.copy(initialScroll = scroll))
        }
    }

    /**
     * A jump to a message. Read state stays blocked until the jump has scrolled ([ThreadEvent.JumpSettled]),
     * and page loads pause, so a page load cannot win the race and drop the jump.
     */
    fun startJump() {
        pendingLanding = false
        isJumping = true
        activeJump = NO_JUMP
        setUi(ui.copy(initialScroll = null))
    }

    /** The jump's message is loaded: scroll to it. */
    fun scrollToJump(messageId: Long, isMms: Boolean?) {
        val nonce = ++scrollNonce
        activeJump = nonce
        setUi(ui.copy(scrollRequest = ScrollRequest.ToMessage(nonce, messageId, isMms)))
    }

    /** The jump could not load its page; page loads may run again. */
    fun jumpFailed() {
        isJumping = false
    }

    /** True when the reader is settled at the bottom, so unread messages count as seen. */
    fun readsAtBottom(includeScrolling: Boolean = false) =
        initialPositionSettled && listAtBottom && (includeScrolling || !listScrolling)

    /** Marks [message] as being retried; false when a retry of it is already in flight. */
    fun startRetry(message: Message) = resending.add(message.getStableId())

    fun retryFailed(message: Message) {
        resending.remove(message.getStableId())
    }

    // endregion

    // region Send SIM

    /** The phone's SIMs, in toggle order, with [index] the one sending. Fewer than two shows no switcher. */
    fun showSims(cards: List<SIMCard>, index: Int) {
        sims = cards
        simIndex = index
        composerValue = composer.copy(sims = cards.map { SimOption(it.id, it.label) }, simIndex = index)
        cards.getOrNull(index)?.let { setUi(ui.copy(sendSim = it.id)) }
    }

    /** The switcher is a toggle (R6-42): each tap moves to the next SIM. */
    fun nextSim() {
        if (sims.size < 2) return
        simIndex = (simIndex + 1) % sims.size
        val card = sims[simIndex]
        host.simChosen(card.subscriptionId)
        composerValue = composer.copy(simIndex = simIndex)
        setUi(ui.copy(sendSim = card.id))
    }

    // endregion

    // region Composer

    fun setText(text: String) = onComposerTextChange(TextFieldValue(text, TextRange(text.length)))

    /** Puts [inserted] at the cursor, as dictation does, leaving the cursor after it. */
    fun insertAtCursor(inserted: String) {
        val at = composerText.selection.min.coerceIn(0, text.length)
        onComposerTextChange(TextFieldValue(text.replaceRange(at, at, inserted), TextRange(at + inserted.length)))
    }

    /** Replaces the text with [value], the cursor at [cursor]. */
    fun replaceText(value: String, cursor: Int) = onComposerTextChange(TextFieldValue(value, TextRange(cursor)))

    fun hasAttachment(id: String) = attachments.any { it.id == id }

    fun addAttachment(selection: AttachmentSelection) = setAttachments(attachments + selection)

    /**
     * A compressed copy of [selection] is ready ([uri]), or failed (null). False when [selection] is gone: removed,
     * sent, or removed and added again while it was compressing (matched by identity).
     */
    fun finishCompression(selection: AttachmentSelection, uri: Uri?): Boolean {
        if (attachments.none { it === selection }) return false
        setAttachments(
            if (uri == null) {
                attachments.filterNot { it === selection }
            } else {
                attachments.map { if (it === selection) it.copy(uri = uri, isPending = false) else it }
            },
        )
        return true
    }

    fun clearMessage() {
        setText("")
        setAttachments(emptyList())
    }

    /** Moves focus to the field and opens the keyboard. */
    fun requestFocus() {
        composerValue = composer.copy(focusRequest = composer.focusRequest + 1)
    }

    fun setSchedule(millis: Long, label: String) {
        scheduledMillis = millis
        composerValue = composer.copy(scheduledAt = label)
    }

    fun clearSchedule() {
        scheduledMillis = null
        composerValue = composer.copy(scheduledAt = null)
    }

    /** Length pill (R6-45): only with the counter setting on (off by default); an MMS shows "MMS" instead. */
    fun refreshComposer() {
        val text = text
        val mms = host.isMms(text, attachments.isNotEmpty()) && (text.isNotEmpty() || attachments.isNotEmpty())
        val length = if (!mms && text.isNotEmpty()) host.smsLength(text) else null
        composerValue = composer.copy(isMms = mms, smsParts = length?.first ?: 0, smsLeft = length?.second ?: 0)
    }

    private fun setAttachments(list: List<AttachmentSelection>) {
        composerValue = composer.copy(attachments = list)
        refreshComposer()
    }

    // endregion

    // region Manage people

    fun openPeople(people: List<Recipient>) {
        setPeople(this.people.copy(open = true, people = people))
    }

    fun closePeople() {
        host.hideKeyboard()
        setPeople(people.copy(open = false))
    }

    fun showPeople(people: List<Recipient>) {
        setPeople(this.people.copy(people = people))
    }

    fun showPeopleContacts(contacts: NewMessageUiState) {
        setPeople(people.copy(contacts = contacts))
    }

    // endregion

    private companion object {
        const val NO_JUMP = -1L
    }
}

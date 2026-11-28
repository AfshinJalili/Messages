package com.afsh.messages.activities

import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.helpers.IS_LAUNCHED_FROM_SHORTCUT
import com.afsh.messages.helpers.IS_RECYCLE_BIN
import com.afsh.messages.helpers.MessageSendCoordinator
import com.afsh.messages.helpers.OPEN_SPAM
import com.afsh.messages.helpers.ParticipantResolver
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.helpers.ThreadRepository
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.helpers.isBlockedOnPhone
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.thread.ThreadFacts
import com.afsh.messages.ui.thread.ThreadStateHolder
import com.afsh.messages.ui.thread.starKey

/**
 * One visit to a conversation on [activity]: the parts of the conversation screen and what they share. Each
 * part owns one concern and reaches the others through this. The screen's state and the logic that needs no
 * Android live in [holder]; the loaded messages live in [repository].
 */
internal class ThreadVisit(val activity: ThreadActivity) {
    private val intent = activity.intent
    val isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
    val openSpam = intent.getBooleanExtra(OPEN_SPAM, false)
    val isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)

    val repository = ThreadRepository.from(activity, intent.getLongExtra(THREAD_ID, 0L), isRecycleBin)
    val data get() = repository.messages
    val threadId get() = repository.threadId
    val people by lazy { ParticipantResolver.from(activity) }

    val holder = ThreadStateHolder(ThreadEvents(this))
    val loader = ThreadLoader(this)
    val composer = ThreadComposerActions(this)
    val sender by lazy { MessageSendCoordinator.from(activity, people, ThreadSendHost(this)) }
    val deletion = ThreadDeletion(this)
    val spam = ThreadSpamActions(this)
    val actions = ThreadConversationActions(this)
    val managePeople = ThreadPeopleActions(this)

    /** The cached conversation row; null until it loads, or for a thread Room does not have. */
    @Volatile
    var conversation: Conversation? = null

    /** Between onResume and onPause. */
    @Volatile
    var isVisible = false

    /** Android blocks the single participant; read off the main thread by [readBlockedOnPhone]. */
    @Volatile
    var blockedOnPhone = false

    /** Blocking. Reads Android's block list for the single participant; [showFacts] shows it. */
    fun readBlockedOnPhone() {
        val addresses = people.participants.getAddresses()
        blockedOnPhone = addresses.size == 1 && activity.isBlockedOnPhone(addresses)
    }

    /** The timeline as last built. */
    @Volatile
    var threadItems = ArrayList<ThreadItem>()

    /** Builds the timeline again and shows it. Main thread. */
    fun refreshTimeline() {
        threadItems = repository.items()
        publish()
    }

    /** Tells the holder what the conversation is: the header, its menu and the composer follow from it. */
    fun showFacts() {
        val participants = people.participants
        val single = participants.size == 1
        val config = activity.config
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
                alwaysSpam = single && config.spamNumbers.containsNumber(participants.getAddresses().first()),
                blockedOnPhone = single && blockedOnPhone,
                muted = config.isConversationMuted(threadId),
                specialNumber = people.isSpecialNumber,
                hasSpam = data.list.any { data.isSpam(it) },
                deletePending = threadId in UndoDeletion.threads,
                groupSubtitle = if (participants.size > 1) {
                    activity.resources.getQuantityString(R.plurals.thread_people, participants.size, participants.size)
                } else {
                    ""
                },
                specialSubtitle = activity.getString(R.string.thread_automated_sender),
            ),
        )
    }

    /** Pushes [items] and everything derived from them to the Compose timeline. Main thread only. */
    fun publish(items: List<ThreadItem> = threadItems) {
        val config = activity.config
        holder.showItems(
            items = items,
            starred = items.filterIsInstance<Message>().filter { config.isMessageStarred(it.id, it.isMMS) }.mapTo(HashSet()) { it.starKey() },
            spamReasons = data.spamReasons,
            simLabels = people.simLabels,
            empty = data.providerReady && !isRecycleBin && data.isEmpty,
        )
    }
}

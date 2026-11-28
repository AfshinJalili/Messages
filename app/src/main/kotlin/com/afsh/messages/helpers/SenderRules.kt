package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.messagesDB

/** Where a sender's messages go. One choice per sender; the spam filter decides only under [AUTOMATIC]. */
enum class SenderFilter { AUTOMATIC, ALWAYS_SPAM, NEVER_SPAM }

/**
 * [number]'s filter from the two lists. The never-spam list wins, as it does in [IncomingSpamClassifier].
 */
fun senderFilter(number: String, spamNumbers: Collection<String>, allowedNumbers: Collection<String>): SenderFilter = when {
    allowedNumbers.containsNumber(number) -> SenderFilter.NEVER_SPAM
    spamNumbers.containsNumber(number) -> SenderFilter.ALWAYS_SPAM
    else -> SenderFilter.AUTOMATIC
}

/** The filter all of [numbers] share, or null when they differ (or there are none). */
fun sharedSenderFilter(numbers: Collection<String>, spamNumbers: Collection<String>, allowedNumbers: Collection<String>): SenderFilter? =
    numbers.map { senderFilter(it, spamNumbers, allowedNumbers) }.distinct().singleOrNull()

/** Where [numbers] stand: their shared filter (null when mixed) and whether Android blocks every one of them. */
data class SenderStanding(val filter: SenderFilter?, val blockedOnPhone: Boolean)

/** Blocking: reads the app's lists and Android's block list. */
fun Context.senderStanding(numbers: Collection<String>): SenderStanding {
    val config = config
    return SenderStanding(
        filter = sharedSenderFilter(numbers, config.spamNumbers, config.allowedNumbers),
        blockedOnPhone = isBlockedOnPhone(numbers),
    )
}

/**
 * Blocking. Gives every one of [numbers] the [filter] and returns the undo, which puts back each sender's own
 * filter and every spam marker this took away. Never spam also moves the spam in [threadIds] back to their
 * conversations; Always spam and Automatic leave messages already received where they are.
 */
fun Context.setSenderFilter(numbers: Collection<String>, threadIds: Collection<Long>, filter: SenderFilter): () -> Unit {
    val config = config
    val before = numbers.associateWith { senderFilter(it, config.spamNumbers, config.allowedNumbers) }
    numbers.forEach { applySenderFilter(it, filter) }
    val restored = if (filter == SenderFilter.NEVER_SPAM) {
        threadIds.flatMap { messagesDB.getThreadSpamMarkers(it) }.also { markers ->
            threadIds.forEach { messagesDB.deleteThreadSpamMarkers(it) }
            if (markers.isNotEmpty()) spamMarkersChanged(threadIds)
        }
    } else {
        emptyList()
    }
    return {
        before.forEach { (number, previous) -> applySenderFilter(number, previous) }
        if (restored.isNotEmpty()) {
            restored.forEach(messagesDB::insertSpamMarker)
            spamMarkersChanged(threadIds)
        }
    }
}

private fun Context.applySenderFilter(number: String, filter: SenderFilter) {
    val config = config
    when (filter) {
        SenderFilter.AUTOMATIC -> {
            config.removeSpamNumber(number)
            config.removeAllowedNumber(number)
        }
        SenderFilter.ALWAYS_SPAM -> config.addSpamNumber(number)
        SenderFilter.NEVER_SPAM -> config.addAllowedNumber(number)
    }
}

/** The inbox previews and unread counts of [threadIds] follow their spam markers. */
private fun Context.spamMarkersChanged(threadIds: Collection<Long>) {
    ConversationPreview.refresh(this, threadIds)
    InboxRepository.refreshUnreadCounts(this, threadIds)
}

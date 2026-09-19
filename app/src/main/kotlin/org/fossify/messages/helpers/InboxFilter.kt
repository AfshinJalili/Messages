package org.fossify.messages.helpers

import android.telephony.PhoneNumberUtils
import androidx.annotation.StringRes
import org.fossify.messages.R
import org.fossify.messages.extensions.extractOtpCode
import org.fossify.messages.models.Conversation

enum class InboxFilter(@StringRes val label: Int) {
    ALL(R.string.inbox_all),
    UNREAD(R.string.inbox_unread),
    PERSONAL(R.string.inbox_personal),
    BUSINESS(R.string.inbox_business),
    UNKNOWN(R.string.inbox_unknown);

    fun matches(conversation: Conversation) = when (this) {
        ALL -> true
        UNREAD -> !conversation.read
        else -> conversation.category() == this
    }
}

/**
 * ponytail: inferred from what the row already has (resolved name, sender id, last snippet), not from
 * every message. Classify per message if users report threads landing in the wrong chip.
 */
fun Conversation.category(): InboxFilter {
    val hasLetterSender = phoneNumber.any { it.isLetter() }
    val isSavedContact = isGroupConversation ||
        (title != phoneNumber && !PhoneNumberUtils.compare(title, phoneNumber))
    // Businesses and OTP services text from alphanumeric ids and short codes.
    val isShortCode = phoneNumber.count { it.isDigit() } in 1..SHORT_CODE_MAX_DIGITS
    return when {
        isSavedContact && !hasLetterSender -> InboxFilter.PERSONAL
        hasLetterSender || isShortCode || snippet.extractOtpCode() != null -> InboxFilter.BUSINESS
        else -> InboxFilter.UNKNOWN
    }
}

private const val SHORT_CODE_MAX_DIGITS = 6

/**
 * Pinned first, newest pin on top so a fresh pin is visibly at the top, and pinned rows never reshuffle
 * by recency. Everything else newest first.
 */
fun List<Conversation>.sortedForInbox(pinned: Set<String>): ArrayList<Conversation> {
    val pinRank = pinned.reversed().withIndex().associate { it.value to it.index }
    return sortedWith(
        compareBy<Conversation> { pinRank[it.threadId.toString()] ?: Int.MAX_VALUE }
            .thenByDescending { it.date }
    ).toCollection(ArrayList())
}

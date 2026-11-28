package com.afsh.messages.ui.inbox

import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.afsh.messages.R
import com.afsh.messages.helpers.ImportProgress
import com.afsh.messages.helpers.InboxFilter
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.messaging.isShortCodeWithLetters
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.navCounts
import java.util.Calendar

@Immutable
data class InboxRow(
    val conversation: Conversation,
    val draft: String?,
    val pinned: Boolean,
    val muted: Boolean,
) {
    val threadId get() = conversation.threadId
}

@Immutable
data class InboxUiState(
    val rows: List<InboxRow> = emptyList(),
    val filter: InboxFilter = InboxFilter.ALL,
    val unreadMessages: Int = 0,
    val loading: Boolean = false,
    val refreshFailed: Boolean = false,
    val importProgress: ImportProgress? = null,
    val selected: Set<Long> = emptySet(),
    val unreadSpam: Int = 0,
    val inboxCountShown: Boolean = false,
    val archiveAvailable: Boolean = true,
    val recycleBinAvailable: Boolean = true,
    val swipeLeft: SwipeAction = SwipeAction.NONE,
    val swipeRight: SwipeAction = SwipeAction.NONE,
) {
    val selecting get() = selected.isNotEmpty()
    val navCounts get() = navCounts(unreadSpam, unreadMessages, inboxCountShown)
    val selectedRows get() = rows.filter { it.threadId in selected }
}

enum class InboxSection(@StringRes val label: Int) {
    PINNED(R.string.inbox_section_pinned),
    TODAY(R.string.inbox_section_today),
    YESTERDAY(R.string.inbox_section_yesterday),
    EARLIER(R.string.inbox_section_earlier),
}

/**
 * Rows arrive pinned-first, then newest-first, so sections come out contiguous. Scheduled sends are
 * dated in the future and remain in date order in Today, without a separate scheduled section.
 */
fun InboxRow.section(now: Calendar): InboxSection {
    if (pinned) return InboxSection.PINNED
    val day = Calendar.getInstance().apply { timeInMillis = conversation.date * 1000L }
    val today = now.clone() as Calendar
    return when {
        day.after(today) -> InboxSection.TODAY
        day.sameDayAs(today) -> InboxSection.TODAY
        day.sameDayAs(today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> InboxSection.YESTERDAY
        else -> InboxSection.EARLIER
    }
}

private fun Calendar.sameDayAs(other: Calendar) =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

enum class InboxAction(@AnyRes val label: Int, val icon: AfshIcon, val labelIsQuantity: Boolean = false) {
    ARCHIVE(R.string.archive, AfshIcon.Archive),
    MARK_UNREAD(R.string.mark_as_unread, AfshIcon.Mail),
    MARK_READ(R.string.mark_as_read, AfshIcon.MailOpen),
    MUTE(R.string.mute_conversation, AfshIcon.NotificationsOff),
    UNMUTE(R.string.unmute_conversation, AfshIcon.Notifications),
    PIN(R.string.pin_conversation, AfshIcon.Pin),
    UNPIN(R.string.unpin_conversation, AfshIcon.Unpin),
    DELETE(com.afsh.commons.R.string.delete, AfshIcon.Delete),
    DIAL(com.afsh.commons.R.string.dial_number, AfshIcon.Call),
    ADD_TO_CONTACT(com.afsh.commons.R.string.add_number_to_contact, AfshIcon.PersonAdd),
    COPY_NUMBER(com.afsh.commons.R.string.copy_number_to_clipboard, AfshIcon.Copy),
    RENAME(R.string.rename_conversation, AfshIcon.Edit),
    DETAILS(R.string.conversation_details, AfshIcon.Info),
    /** Opens the Spam and blocking sheet for the selected senders. */
    SPAM_AND_BLOCKING(R.string.filter_rules, AfshIcon.SpamShield),
}

/** Declaration order of [InboxAction] is the display order; the bar shows the first few, the rest overflow. */
fun availableActions(
    selected: List<InboxRow>,
    archiveAvailable: Boolean,
): List<InboxAction> {
    val first = selected.firstOrNull() ?: return emptyList()
    val single = selected.size == 1
    val person = single && !first.conversation.isGroupConversation
    return InboxAction.entries.filter { action ->
        when (action) {
            InboxAction.ARCHIVE -> archiveAvailable
            InboxAction.MARK_UNREAD -> selected.any { it.conversation.read }
            InboxAction.MARK_READ -> selected.any { !it.conversation.read }
            InboxAction.MUTE -> selected.any { !it.muted }
            InboxAction.UNMUTE -> selected.all { it.muted }
            InboxAction.PIN -> selected.any { !it.pinned }
            InboxAction.UNPIN -> selected.all { it.pinned }
            InboxAction.DIAL -> person && !isShortCodeWithLetters(first.conversation.phoneNumber)
            InboxAction.ADD_TO_CONTACT, InboxAction.COPY_NUMBER -> person
            InboxAction.RENAME -> single && first.conversation.isGroupConversation
            InboxAction.DETAILS -> single
            InboxAction.DELETE -> true
            InboxAction.SPAM_AND_BLOCKING -> true
        }
    }
}

const val PRIMARY_ACTION_COUNT = 3

/** Delete always sits in the bar; everything past the first few safe actions goes to the overflow menu. */
fun List<InboxAction>.splitForBar(): Pair<List<InboxAction>, List<InboxAction>> {
    val candidates = listOfNotNull(
        InboxAction.ARCHIVE.takeIf { it in this },
        firstOrNull { it == InboxAction.MARK_READ || it == InboxAction.MARK_UNREAD },
        firstOrNull { it == InboxAction.MUTE || it == InboxAction.UNMUTE },
    )
    val bar = candidates.take(PRIMARY_ACTION_COUNT) + listOfNotNull(InboxAction.DELETE.takeIf { it in this })
    return bar to (this - bar.toSet())
}

fun InboxFilter.emptyTitle() = when (this) {
    InboxFilter.ALL -> R.string.inbox_empty_title
    InboxFilter.UNREAD -> R.string.no_unread_conversations
    else -> R.string.no_filtered_conversations
}

// Child activities return to the Library tab retained by the inbox composition.
enum class LibraryDestination(@StringRes val label: Int, @StringRes val description: Int, val icon: AfshIcon) {
    STARRED(R.string.starred_messages, R.string.library_starred_description, AfshIcon.Star),
    ARCHIVE(R.string.archived_conversations, R.string.library_archive_description, AfshIcon.Archive),
    SPAM(R.string.inbox_spam, R.string.library_spam_description, AfshIcon.SpamShield),
    RECYCLE_BIN(com.afsh.commons.R.string.recycle_bin, R.string.library_recycle_description, AfshIcon.Delete);

    fun isAvailable(state: InboxUiState) = when (this) {
        ARCHIVE -> state.archiveAvailable
        RECYCLE_BIN -> state.recycleBinAvailable
        else -> true
    }
}

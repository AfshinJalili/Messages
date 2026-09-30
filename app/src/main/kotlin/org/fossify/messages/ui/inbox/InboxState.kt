package org.fossify.messages.ui.inbox

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import org.fossify.messages.R
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.models.Conversation
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
    val selected: Set<Long> = emptySet(),
    val unreadSpam: Int = 0,
    val archiveAvailable: Boolean = true,
    val recycleBinAvailable: Boolean = true,
    val swipeLeft: SwipeAction = SwipeAction.NONE,
    val swipeRight: SwipeAction = SwipeAction.NONE,
) {
    val selecting get() = selected.isNotEmpty()
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

enum class InboxAction(@StringRes val label: Int, @DrawableRes val icon: Int) {
    ARCHIVE(R.string.archive, R.drawable.ic_ol_archive),
    MARK_UNREAD(R.string.mark_as_unread, R.drawable.ic_ol_mail),
    MARK_READ(R.string.mark_as_read, R.drawable.ic_ol_check_check),
    MUTE(R.string.mute_conversation, R.drawable.ic_ol_bell_off),
    UNMUTE(R.string.unmute_conversation, R.drawable.ic_ol_bell),
    PIN(R.string.pin_conversation, R.drawable.ic_ol_pin),
    UNPIN(R.string.unpin_conversation, R.drawable.ic_unpin_vector),
    DELETE(org.fossify.commons.R.string.delete, R.drawable.ic_lucide_trash),
    DIAL(org.fossify.commons.R.string.dial_number, R.drawable.ic_ol_phone),
    ADD_TO_CONTACT(org.fossify.commons.R.string.add_number_to_contact, R.drawable.ic_ol_user_plus),
    COPY_NUMBER(org.fossify.commons.R.string.copy_number_to_clipboard, R.drawable.ic_lucide_copy),
    RENAME(R.string.rename_conversation, R.drawable.ic_ol_pencil),
    DETAILS(R.string.conversation_details, R.drawable.ic_lucide_info),
    BLOCK(org.fossify.commons.R.string.block_number, R.drawable.ic_ol_ban),
}

/** Declaration order of [InboxAction] is the display order; the bar shows the first few, the rest overflow. */
fun availableActions(selected: List<InboxRow>, archiveAvailable: Boolean): List<InboxAction> {
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
            InboxAction.ADD_TO_CONTACT -> person && first.conversation.title == first.conversation.phoneNumber
            InboxAction.COPY_NUMBER -> person
            InboxAction.RENAME -> single && first.conversation.isGroupConversation
            InboxAction.DETAILS -> single
            InboxAction.DELETE -> true
            InboxAction.BLOCK -> selected.none { it.conversation.isGroupConversation }
        }
    }
}

const val PRIMARY_ACTION_COUNT = 3

/** Delete always sits in the bar; everything past the first few safe actions goes to the overflow menu. */
fun List<InboxAction>.splitForBar(): Pair<List<InboxAction>, List<InboxAction>> {
    val candidates = listOfNotNull(
        InboxAction.ARCHIVE.takeIf { it in this },
        if (InboxAction.MARK_READ in this) InboxAction.MARK_READ else InboxAction.MARK_UNREAD.takeIf { it in this },
        firstOrNull { it == InboxAction.MUTE || it == InboxAction.UNMUTE },
    )
    val bar = candidates.take(PRIMARY_ACTION_COUNT) + listOfNotNull(InboxAction.DELETE.takeIf { it in this })
    return bar to (this - bar.toSet())
}

fun InboxFilter.emptyTitle() = when (this) {
    InboxFilter.ALL -> R.string.inbox_empty_title
    InboxFilter.UNREAD -> R.string.inbox_caught_up
    InboxFilter.PERSONAL -> R.string.inbox_empty_people
    InboxFilter.BUSINESS -> R.string.inbox_empty_services
    InboxFilter.UNKNOWN -> R.string.inbox_empty_unknown
}

// Child activities return to the Library tab retained by the inbox composition.
enum class LibraryDestination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    STARRED(R.string.starred_messages, R.drawable.ic_lucide_star),
    ARCHIVE(R.string.archived_conversations, R.drawable.ic_ol_archive),
    SPAM(R.string.inbox_spam, R.drawable.ic_ol_ban),
    RECYCLE_BIN(org.fossify.commons.R.string.recycle_bin, R.drawable.ic_lucide_trash);

    fun isAvailable(state: InboxUiState) = when (this) {
        ARCHIVE -> state.archiveAvailable
        RECYCLE_BIN -> state.recycleBinAvailable
        else -> true
    }
}

fun InboxFilter.emptyBody() = when (this) {
    InboxFilter.ALL -> R.string.inbox_empty_text
    InboxFilter.UNREAD -> R.string.inbox_empty_unread_body
    InboxFilter.PERSONAL -> R.string.inbox_empty_people_body
    InboxFilter.BUSINESS -> R.string.inbox_empty_services_body
    InboxFilter.UNKNOWN -> R.string.inbox_empty_unknown_body
}

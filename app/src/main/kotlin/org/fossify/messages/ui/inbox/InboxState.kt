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
    ARCHIVE(R.string.archive, R.drawable.ic_archive_vector),
    MARK_UNREAD(R.string.mark_as_unread, org.fossify.commons.R.drawable.ic_mail_vector),
    MARK_READ(R.string.mark_as_read, R.drawable.ic_check_double_vector),
    MUTE(R.string.mute_conversation, R.drawable.ic_bell_off_vector),
    UNMUTE(R.string.unmute_conversation, org.fossify.commons.R.drawable.ic_bell_vector),
    PIN(R.string.pin_conversation, org.fossify.commons.R.drawable.ic_pin_vector),
    UNPIN(R.string.unpin_conversation, R.drawable.ic_unpin_vector),
    DELETE(org.fossify.commons.R.string.delete, org.fossify.commons.R.drawable.ic_delete_vector),
    DIAL(org.fossify.commons.R.string.dial_number, org.fossify.commons.R.drawable.ic_phone_vector),
    ADD_TO_CONTACT(org.fossify.commons.R.string.add_number_to_contact, org.fossify.commons.R.drawable.ic_add_person_vector),
    COPY_NUMBER(org.fossify.commons.R.string.copy_number_to_clipboard, org.fossify.commons.R.drawable.ic_copy_vector),
    RENAME(R.string.rename_conversation, org.fossify.commons.R.drawable.ic_edit_vector),
    DETAILS(R.string.conversation_details, org.fossify.commons.R.drawable.ic_info_vector),
    BLOCK(org.fossify.commons.R.string.block_number, org.fossify.commons.R.drawable.ic_block_vector),
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
            InboxAction.ADD_TO_CONTACT, InboxAction.COPY_NUMBER -> person
            InboxAction.RENAME -> single && first.conversation.isGroupConversation
            InboxAction.DETAILS -> single
            InboxAction.DELETE, InboxAction.BLOCK -> true
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
enum class LibraryDestination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    STARRED(R.string.starred_messages, org.fossify.commons.R.drawable.ic_star_vector),
    ARCHIVE(R.string.archived_conversations, R.drawable.ic_archive_vector),
    SPAM(R.string.inbox_spam, org.fossify.commons.R.drawable.ic_block_vector),
    RECYCLE_BIN(org.fossify.commons.R.string.recycle_bin, org.fossify.commons.R.drawable.ic_delete_vector);

    fun isAvailable(state: InboxUiState) = when (this) {
        ARCHIVE -> state.archiveAvailable
        RECYCLE_BIN -> state.recycleBinAvailable
        else -> true
    }
}

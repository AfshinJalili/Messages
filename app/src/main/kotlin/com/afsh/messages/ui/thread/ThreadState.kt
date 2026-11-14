package com.afsh.messages.ui.thread

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.afsh.messages.R
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Message
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.components.AfshIcon

@Immutable
data class ThreadHeaderState(
    val title: String = "",
    val subtitle: String = "",
    val photoUri: String = "",
    val isGroup: Boolean = false,
    val canDial: Boolean = false,
    val actions: List<ThreadMenuAction> = emptyList(),
)

@Immutable
data class ThreadUiState(
    val header: ThreadHeaderState = ThreadHeaderState(),
    /** Chronological, as built by [com.afsh.messages.models.buildThreadItems]. */
    val items: List<ThreadItem> = emptyList(),
    /** [Message.getStableId]s: SMS and MMS ids are separate sequences and can collide. */
    val selected: Set<Long> = emptySet(),
    val starred: Set<Long> = emptySet(),
    val spamReasons: Map<Long, Int> = emptyMap(),
    /** SIM slot label ("1", "2") by subscription id; empty on single-SIM phones, where it says nothing. */
    val simLabels: Map<Int, String> = emptyMap(),
    val isGroup: Boolean = false,
    val isRecycleBin: Boolean = false,
    val searching: Boolean = false,
    val initialScroll: InitialScroll? = null,
    val scrollRequest: ScrollRequest? = null,
    /** Set once the provider confirms the thread has no messages (design 79). */
    val empty: Boolean = false,
    /** First name for "Say hello to …" and the composer hint; empty for groups and bare numbers. */
    val firstName: String = "",
    /** Slot of the SIM that sends and retries ("1", "2"); 0 on single-SIM phones, which show no switcher. */
    val sendSim: Int = 0,
) {
    val selecting get() = selected.isNotEmpty()
    val selectedMessages get() = items.filterIsInstance<Message>().filter { it.getStableId() in selected }

    fun isStarred(message: Message) = message.starKey() in starred
    fun spamReason(message: Message) = if (message.isMMS) null else spamReasons[message.id]
}

/** Everything the composer shows apart from the text, which the activity keeps as its own state. */
@Immutable
data class ComposerState(
    /** False in the Recycle Bin and while a whole-thread delete waits for its Undo. */
    val visible: Boolean = true,
    /** False for letter short codes, which cannot receive replies (design 78). */
    val canReply: Boolean = true,
    /** The sender is blocked: the composer stays, switched off, until they are unblocked (67). */
    val blocked: Boolean = false,
    val attachments: List<AttachmentSelection> = emptyList(),
    /** Empty unless the phone has two or more SIMs. */
    val sims: List<SimOption> = emptyList(),
    val simIndex: Int = 0,
    val isMms: Boolean = false,
    /** SMS parts and characters left in the last one; parts is 0 while the length pill stays hidden (R6-45). */
    val smsParts: Int = 0,
    val smsLeft: Int = 0,
    /** The picked send time, or null when sending now. */
    val scheduledAt: String? = null,
    /** A new value moves focus to the field and opens the keyboard. */
    val focusRequest: Int = 0,
) {
    /** Nothing sends while an image is still compressing, caption or not. */
    fun hasContent(text: String) = (text.isNotEmpty() || attachments.isNotEmpty()) && attachments.none { it.isPending }
}

@Immutable
data class SimOption(val slot: Int, val label: String)

sealed interface ComposerEvent {
    data object Send : ComposerEvent
    data object Dictate : ComposerEvent
    data class Attach(val option: AttachOption) : ComposerEvent
    /** Pick or change the send time. */
    data object Schedule : ComposerEvent
    data object CancelSchedule : ComposerEvent
    /** The switcher is a toggle (R6-42): each tap moves to the next SIM. */
    data object NextSim : ComposerEvent
    data class RemoveAttachment(val attachment: AttachmentSelection) : ComposerEvent
    data class OpenAttachment(val attachment: AttachmentSelection) : ComposerEvent
    data object ReplyInfo : ComposerEvent
}

/**
 * Declaration order is the attach sheet's order (design 11). Contact and Send later lead: they are
 * plain SMS, and where MMS is unavailable they are the only options that work.
 */
enum class AttachOption(@StringRes val title: Int, @StringRes val subtitle: Int, @DrawableRes val icon: Int) {
    CONTACT(R.string.attach_contact, R.string.attach_contact_text, org.fossify.commons.R.drawable.ic_person_vector),
    SEND_LATER(R.string.composer_send_later, R.string.attach_send_later_text, org.fossify.commons.R.drawable.ic_clock_vector),
    PHOTO(R.string.attach_photos, R.string.attach_photos_text, R.drawable.ic_image_vector),
    CAMERA(R.string.attach_camera, R.string.attach_camera_text, org.fossify.commons.R.drawable.ic_camera_vector),
    VIDEO(R.string.attach_video, R.string.attach_video_text, R.drawable.ic_videocam_vector),
    RECORD_VIDEO(R.string.attach_record_video, R.string.attach_record_video_text, R.drawable.ic_video_camera_vector),
    AUDIO(R.string.attach_audio, R.string.attach_audio_text, org.fossify.commons.R.drawable.ic_microphone_vector),
    FILE(R.string.attach_file, R.string.attach_file_text, R.drawable.ic_document_vector),
}

/** Matches [com.afsh.messages.helpers.Config.isMessageStarred]'s key space: MMS and SMS ids can collide. */
fun Message.starKey() = if (isMMS) -id else id

/** Where the list lands once the provider's messages are in. Runs once per screen. */
sealed interface InitialScroll {
    /** Top of the first unread message, unless every unread message already fits on screen. */
    data object FirstUnread : InitialScroll

    data class Message(val messageId: Long, val isMms: Boolean) : InitialScroll
}

/** [nonce] makes a repeat of the same request a new value. */
sealed interface ScrollRequest {
    val nonce: Long

    data class Bottom(override val nonce: Long, val smooth: Boolean) : ScrollRequest
    /** [isMms] null matches either provider, for ids that arrive without one. */
    data class ToMessage(override val nonce: Long, val messageId: Long, val isMms: Boolean? = null) : ScrollRequest {
        fun matches(message: com.afsh.messages.models.Message) = message.id == messageId && (isMms == null || message.isMMS == isMms)
    }
}

sealed interface ThreadEvent {
    data object Back : ThreadEvent
    data object ClearSelection : ThreadEvent
    data object OpenDetails : ThreadEvent
    data object Dial : ThreadEvent
    data class Menu(val action: ThreadMenuAction) : ThreadEvent
    data class SearchSubmit(val query: String) : ThreadEvent
    data object SearchChanged : ThreadEvent
    data object SearchClose : ThreadEvent

    data class ToggleSelection(val message: Message) : ThreadEvent
    data class Act(val action: MessageAction, val messages: List<Message>) : ThreadEvent
    data class Retry(val message: Message) : ThreadEvent
    /** The failed bubble's switcher (R6-46): changes the SIM a retry uses, never sends. */
    data object NextSim : ThreadEvent
    data class OpenAttachment(val message: Message, val attachment: Attachment) : ThreadEvent
    data class CopyText(val text: String) : ThreadEvent

    data object JumpToLatest : ThreadEvent
    data object LoadOlder : ThreadEvent
    data class Viewport(val atBottom: Boolean, val scrolling: Boolean) : ThreadEvent
    data object InitialScrollSettled : ThreadEvent
    data class JumpSettled(val nonce: Long) : ThreadEvent
}

/** Declaration order is the overflow menu order (design view 40). */
enum class ThreadMenuAction(@StringRes val label: Int, val icon: AfshIcon) {
    SEARCH(R.string.search_in_conversation, AfshIcon.Search),
    DETAILS(R.string.conversation_details, AfshIcon.Info),
    MUTE(R.string.mute_conversation, AfshIcon.NotificationsOff),
    UNMUTE(R.string.unmute_conversation, AfshIcon.Notifications),
    MARK_UNREAD(R.string.mark_as_unread, AfshIcon.Mail),
    RENAME(R.string.rename_conversation, AfshIcon.Edit),
    ADD_PERSON(R.string.add_person, AfshIcon.PersonAdd),
    ADD_TO_CONTACT(org.fossify.commons.R.string.add_number_to_contact, AfshIcon.PersonAdd),
    COPY_NUMBER(org.fossify.commons.R.string.copy_number_to_clipboard, AfshIcon.Copy),
    ARCHIVE(R.string.archive, AfshIcon.Archive),
    UNARCHIVE(R.string.unarchive, AfshIcon.Inbox),
    ALLOW_SENDER(R.string.allow_sender, AfshIcon.Verified),
    BLOCK(org.fossify.commons.R.string.block_number, AfshIcon.Block),
    RESTORE(R.string.restore_all_messages, AfshIcon.Restore),
    DELETE(org.fossify.commons.R.string.delete, AfshIcon.Delete),

    ;

    /** Shown in the error color: these remove or shut out the conversation. */
    val destructive: Boolean get() = this == DELETE || this == BLOCK
}

/** Declaration order is the display order; the bar takes the first few (design view 10), the rest overflow. */
enum class MessageAction(@StringRes val label: Int, @DrawableRes val icon: Int) {
    COPY(org.fossify.commons.R.string.copy, R.drawable.ic_lucide_copy),
    FORWARD(R.string.forward_message, R.drawable.ic_lucide_forward),
    STAR(R.string.star_message, R.drawable.ic_lucide_star),
    UNSTAR(R.string.unstar_message, R.drawable.ic_lucide_star_filled),
    DELETE(org.fossify.commons.R.string.delete, R.drawable.ic_lucide_trash),
    SHARE(org.fossify.commons.R.string.share, R.drawable.ic_lucide_share),
    SELECT_TEXT(org.fossify.commons.R.string.select_text, R.drawable.ic_lucide_text_cursor),
    DETAILS(R.string.message_details, R.drawable.ic_lucide_info),
    SAVE_AS(org.fossify.commons.R.string.save_as, R.drawable.ic_lucide_download),
    NOT_SPAM(R.string.not_spam, R.drawable.ic_lucide_circle_check),
    RESTORE(R.string.restore, R.drawable.ic_lucide_rotate_ccw),
    SELECT_ALL(org.fossify.commons.R.string.select_all, R.drawable.ic_lucide_list_checks),

    // Only in a scheduled message's sheet (84).
    EDIT_SCHEDULED(R.string.edit_message_and_time, R.drawable.ic_lucide_pencil),
    SEND_NOW(R.string.send_now, R.drawable.ic_lucide_send),
}

/** Same rules as the old contextual action bar. */
fun selectionActions(state: ThreadUiState): List<MessageAction> {
    val selected = state.selectedMessages
    if (selected.isEmpty()) return emptyList()
    val single = selected.size == 1
    val hasText = selected.any { it.body.isNotEmpty() }
    val allStarred = selected.all { state.isStarred(it) }
    return MessageAction.entries.filter { action ->
        when (action) {
            MessageAction.COPY -> hasText
            MessageAction.FORWARD -> single
            // 13 is for messages that exist; a scheduled one is edited from its sheet (84).
            MessageAction.DETAILS -> single && !selected.first().isScheduled
            MessageAction.EDIT_SCHEDULED, MessageAction.SEND_NOW -> false
            MessageAction.STAR -> !state.isRecycleBin && !allStarred
            MessageAction.UNSTAR -> !state.isRecycleBin && allStarred
            MessageAction.SHARE, MessageAction.SELECT_TEXT -> single && hasText
            MessageAction.SAVE_AS -> selected.any { !it.attachment?.attachments.isNullOrEmpty() }
            MessageAction.NOT_SPAM -> selected.all { state.spamReason(it) != null }
            MessageAction.RESTORE -> state.isRecycleBin
            MessageAction.DELETE, MessageAction.SELECT_ALL -> true
        }
    }
}

/**
 * The sheet a tap on one message opens (R6-48), in sheet order. A scheduled message offers Edit,
 * Send now and Delete instead (84, D05).
 */
fun tapActions(message: Message, state: ThreadUiState): List<MessageAction> {
    if (message.isScheduled) return listOf(MessageAction.EDIT_SCHEDULED, MessageAction.SEND_NOW, MessageAction.DELETE)
    val failed = message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED
    return listOf(
        MessageAction.COPY, MessageAction.FORWARD, MessageAction.STAR, MessageAction.UNSTAR, MessageAction.SHARE,
        MessageAction.SELECT_TEXT, MessageAction.DELETE,
    ).filter { action ->
        when (action) {
            MessageAction.COPY -> message.body.isNotEmpty()
            MessageAction.SHARE -> !failed && message.body.isNotEmpty()
            MessageAction.SELECT_TEXT -> message.body.isNotBlank()
            MessageAction.STAR -> !failed && !state.isRecycleBin && !state.isStarred(message)
            MessageAction.UNSTAR -> !failed && !state.isRecycleBin && state.isStarred(message)
            else -> true
        }
    }
}

private val BAR_ACTIONS = setOf(MessageAction.COPY, MessageAction.FORWARD, MessageAction.STAR, MessageAction.UNSTAR, MessageAction.DELETE)

fun List<MessageAction>.splitForBar(): Pair<List<MessageAction>, List<MessageAction>> = partition { it in BAR_ACTIONS }

package com.afsh.messages.helpers

/**
 * How the app should interrupt or cue the user for a [MessagingEvent].
 *
 * Pure policy output carrying the matched event payload; see GLOSSARY.md ("Attention Policy") and ADR-0001.
 */
sealed interface AttentionDecision {
    data class ShowIncomingAlert(val event: MessagingEvent.IncomingMessage) : AttentionDecision
    data class ShowSendFailureAlert(val event: MessagingEvent.SendFailed) : AttentionDecision
    data class ShowInThreadFailureReason(val event: MessagingEvent.SendFailed) : AttentionDecision
    /** The conversation was read, so its message notification has nothing new to show. */
    data class ClearIncomingAlert(val threadId: Long) : AttentionDecision
    /** [event]'s message went, so a "not sent" notification for that message is out of date. */
    data class ClearSendFailureAlert(val event: MessagingEvent.MessageSent) : AttentionDecision
    data object Suppress : AttentionDecision
}

data class ConversationAttentionFlags(
    val isMuted: Boolean = false,
)

/**
 * Pure rule mapping a [MessagingEvent], current [AppPresence], and conversation flags to an
 * [AttentionDecision]. No Android dependencies.
 */
object AttentionPolicy {
    fun decide(
        event: MessagingEvent,
        presence: AppPresence,
        flags: ConversationAttentionFlags = ConversationAttentionFlags(),
    ): AttentionDecision = when (event) {
        is MessagingEvent.IncomingMessage -> when {
            event.isSpam -> AttentionDecision.Suppress
            flags.isMuted -> AttentionDecision.Suppress
            presence is AppPresence.ViewingConversation && presence.threadId == event.threadId ->
                AttentionDecision.Suppress
            else -> AttentionDecision.ShowIncomingAlert(event)
        }

        is MessagingEvent.SendFailed -> when {
            presence is AppPresence.ViewingConversation && presence.threadId == event.threadId ->
                AttentionDecision.ShowInThreadFailureReason(event)
            else -> AttentionDecision.ShowSendFailureAlert(event)
        }

        // Wherever it was read (inbox, notification action, inline reply, another screen).
        is MessagingEvent.ReadStateChanged ->
            if (event.read) AttentionDecision.ClearIncomingAlert(event.threadId) else AttentionDecision.Suppress

        is MessagingEvent.MessageSent -> AttentionDecision.ClearSendFailureAlert(event)

        is MessagingEvent.DeliveryUpdated,
        is MessagingEvent.MessageDeleted,
        is MessagingEvent.ScheduledMessageSent,
        is MessagingEvent.ConversationsChanged -> AttentionDecision.Suppress
    }
}

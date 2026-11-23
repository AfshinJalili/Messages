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

        is MessagingEvent.MessageSent,
        is MessagingEvent.DeliveryUpdated,
        is MessagingEvent.ReadStateChanged,
        is MessagingEvent.MessageDeleted,
        is MessagingEvent.ScheduledMessageSent,
        is MessagingEvent.ConversationsChanged -> AttentionDecision.Suppress
    }
}

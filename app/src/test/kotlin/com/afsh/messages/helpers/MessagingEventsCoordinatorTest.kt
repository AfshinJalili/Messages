package com.afsh.messages.helpers

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMessagingEventHost : MessagingEventHost {
    val mutedThreads = mutableSetOf<Long>()
    val incomingAlerts = mutableListOf<MessagingEvent.IncomingMessage>()
    val sendFailureAlerts = mutableListOf<MessagingEvent.SendFailed>()
    val cancelledNotifications = mutableListOf<Long>()
    val cancelledSendFailures = mutableListOf<Long>()
    var inboxRefreshes = 0

    override fun isConversationMuted(threadId: Long): Boolean = threadId in mutedThreads

    override fun showIncomingAlert(event: MessagingEvent.IncomingMessage) {
        incomingAlerts += event
    }

    override fun showSendFailureAlert(event: MessagingEvent.SendFailed) {
        sendFailureAlerts += event
    }

    override fun cancelConversationNotification(threadId: Long) {
        cancelledNotifications += threadId
    }

    override fun cancelSendFailureNotification(threadId: Long) {
        cancelledSendFailures += threadId
    }

    override fun refreshInbox() {
        inboxRefreshes++
    }
}

class MessagingEventsCoordinatorTest {

    private var deviceLockedOrOff = false
    private val host = FakeMessagingEventHost()
    private val presence: AppPresenceTracker = AppPresenceTracker(
        isDeviceLockedOrOff = { deviceLockedOrOff },
        onEnterConversation = { coordinator.conversationEntered(it) },
    )
    private val coordinator: MessagingEventCoordinator = MessagingEventCoordinator(presence, host)

    private fun incoming(
        threadId: Long = 10L,
        messageId: Long = 100L,
        isMms: Boolean = false,
        isSpam: Boolean = false,
    ) = MessagingEvent.IncomingMessage(
        threadId = threadId,
        messageId = messageId,
        isMms = isMms,
        isSpam = isSpam,
        address = "+15550100",
        senderName = "Mina",
        body = "Hello",
    )

    private fun sendFailed(
        threadId: Long = 10L,
        messageId: Long = 200L,
        isMms: Boolean = false,
        reason: String = "No service",
    ) = MessagingEvent.SendFailed(
        threadId = threadId,
        messageId = messageId,
        isMms = isMms,
        recipientName = "Mina",
        reason = reason,
    )

    // --- Incoming messages across App Presence states ---

    @Test
    fun `incoming SMS and MMS alert when app is in background`() {
        val sms = incoming(threadId = 10L, isMms = false)
        val mms = incoming(threadId = 10L, messageId = 101L, isMms = true)

        assertEquals(AttentionDecision.ShowIncomingAlert(sms), coordinator.publish(sms))
        assertEquals(AttentionDecision.ShowIncomingAlert(mms), coordinator.publish(mms))
        assertEquals(listOf(sms, mms), host.incomingAlerts)
    }

    @Test
    fun `incoming SMS and MMS are suppressed when viewing that exact conversation`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)

        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isMms = false)))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, messageId = 101L, isMms = true)))
        assertTrue(host.incomingAlerts.isEmpty())
        assertEquals(listOf(10L), host.cancelledNotifications)
    }

    @Test
    fun `incoming message alerts when viewing a different conversation`() {
        val threadB = Any()
        presence.onConversationActive(threadB, threadId = 20L, isRecycleBin = false)

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertEquals(listOf(event), host.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when in-app on Inbox or another non-conversation screen`() {
        val inbox = Any()
        presence.onScreenResumed(inbox)
        assertEquals(AppPresence.InApp, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertEquals(listOf(event), host.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when viewing that thread in Recycle Bin mode`() {
        val recycleThread = Any()
        presence.onConversationActive(recycleThread, threadId = 10L, isRecycleBin = true)
        assertEquals(AppPresence.InApp, presence.current())
        assertTrue(host.cancelledNotifications.isEmpty())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertEquals(listOf(event), host.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when App Lock screen is active over a conversation`() {
        val threadScreen = Any()
        val lockScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        presence.onLockScreenActive(lockScreen)
        assertEquals(AppPresence.Locked, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertEquals(listOf(event), host.incomingAlerts)

        // Pausing the lock screen restores viewing the underlying conversation.
        presence.onScreenPaused(lockScreen)
        assertEquals(AppPresence.ViewingConversation(10L), presence.current())
    }

    @Test
    fun `incoming message alerts when device screen is off or keyguard is locked`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        deviceLockedOrOff = true
        assertEquals(AppPresence.Locked, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertEquals(listOf(event), host.incomingAlerts)
    }

    @Test
    fun `entering conversation while locked defers notification clear until unlocked`() {
        val threadScreen = Any()
        deviceLockedOrOff = true
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertTrue(host.cancelledNotifications.isEmpty())

        deviceLockedOrOff = false
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertEquals(listOf(10L), host.cancelledNotifications)
    }

    @Test
    fun `unlocking after the conversation resumed under the keyguard clears its notification and stops alerts`() {
        val threadScreen = Any()
        // After an unlock, the platform resumes the conversation while the keyguard still reports locked.
        deviceLockedOrOff = true
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertEquals(AppPresence.Locked, presence.current())
        assertTrue(host.cancelledNotifications.isEmpty())

        deviceLockedOrOff = false
        presence.onDeviceUnlocked()
        assertEquals(listOf(10L), host.cancelledNotifications)

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.Suppress, coordinator.publish(event))
        assertTrue(host.incomingAlerts.isEmpty())

        // A repeated unlock broadcast does not cancel again.
        presence.onDeviceUnlocked()
        assertEquals(listOf(10L), host.cancelledNotifications)
    }

    @Test
    fun `a lock that only a publish saw still lets the return to the thread clear its notification`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertEquals(listOf(10L), host.cancelledNotifications)

        // The keyguard covers the open thread without a lifecycle callback, and a message arrives.
        deviceLockedOrOff = true
        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))

        deviceLockedOrOff = false
        presence.onDeviceUnlocked()
        assertEquals(listOf(10L, 10L), host.cancelledNotifications)
    }

    @Test
    fun `tablet split view treats open conversation pane as active beside resumed Inbox`() {
        val inbox = Any()
        val threadA = Any()
        presence.onScreenResumed(inbox)
        presence.onConversationActive(threadA, threadId = 10L, isRecycleBin = false)
        presence.onScreenResumed(threadA)

        assertEquals(AppPresence.ViewingConversation(10L), presence.current())
        assertEquals(listOf(10L), host.cancelledNotifications)
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L)))

        val other = incoming(threadId = 99L)
        assertEquals(AttentionDecision.ShowIncomingAlert(other), coordinator.publish(other))

        // Closing the detail pane reverts to InApp (Inbox still resumed)
        presence.onScreenPaused(threadA)
        assertEquals(AppPresence.InApp, presence.current())
        val afterClose = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(afterClose), coordinator.publish(afterClose))
    }

    @Test
    fun `resolving a newly created thread id transitions presence and cancels notifications`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 0L, isRecycleBin = false)
        assertEquals(AppPresence.InApp, presence.current())
        assertTrue(host.cancelledNotifications.isEmpty())

        presence.onConversationThreadUpdated(threadScreen, threadId = 55L, isRecycleBin = false)
        assertEquals(AppPresence.ViewingConversation(55L), presence.current())
        assertEquals(listOf(55L), host.cancelledNotifications)
    }

    @Test
    fun `thread id update after pause cannot resurrect a backgrounded conversation`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        presence.onScreenPaused(threadScreen)
        assertEquals(AppPresence.Background, presence.current())

        presence.onConversationThreadUpdated(threadScreen, threadId = 55L, isRecycleBin = false)
        assertEquals(AppPresence.Background, presence.current())

        val event = incoming(threadId = 55L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
    }

    // --- Spam & muted conversations ---

    @Test
    fun `spam SMS and MMS are always suppressed regardless of App Presence`() {
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isSpam = true)))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isMms = true, isSpam = true)))
        assertTrue(host.incomingAlerts.isEmpty())
    }

    @Test
    fun `muted conversation incoming messages are suppressed regardless of App Presence`() {
        host.mutedThreads += 10L
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L)))
        assertTrue(host.incomingAlerts.isEmpty())
    }

    // --- Outgoing SMS and MMS send failures ---

    @Test
    fun `SMS and MMS send failures surface in-thread reason without tray alert when viewing that thread`() = runBlocking {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)

        val smsDeferred = async(start = CoroutineStart.UNDISPATCHED) { coordinator.inThreadFailures.first() }
        val smsFailure = sendFailed(threadId = 10L, isMms = false, reason = "No service")
        assertEquals(AttentionDecision.ShowInThreadFailureReason(smsFailure), coordinator.publish(smsFailure))
        assertEquals(smsFailure, smsDeferred.await())

        val mmsDeferred = async(start = CoroutineStart.UNDISPATCHED) { coordinator.inThreadFailures.first() }
        val mmsFailure = sendFailed(threadId = 10L, messageId = 201L, isMms = true, reason = "MMS error")
        assertEquals(AttentionDecision.ShowInThreadFailureReason(mmsFailure), coordinator.publish(mmsFailure))
        assertEquals(mmsFailure, mmsDeferred.await())

        assertTrue(host.sendFailureAlerts.isEmpty())
    }

    @Test
    fun `SMS and MMS send failures post system alert when on another screen, locked, or backgrounded`() {
        val otherThread = Any()
        presence.onConversationActive(otherThread, threadId = 20L, isRecycleBin = false)

        val smsFailure = sendFailed(threadId = 10L, isMms = false)
        assertEquals(AttentionDecision.ShowSendFailureAlert(smsFailure), coordinator.publish(smsFailure))

        presence.onScreenPaused(otherThread)
        assertEquals(AppPresence.Background, presence.current())
        val mmsFailure = sendFailed(threadId = 10L, messageId = 201L, isMms = true)
        assertEquals(AttentionDecision.ShowSendFailureAlert(mmsFailure), coordinator.publish(mmsFailure))

        assertEquals(listOf(smsFailure, mmsFailure), host.sendFailureAlerts)
    }

    @Test
    fun `muting a conversation does not suppress outgoing send failure alerts`() {
        host.mutedThreads += 10L
        val failure = sendFailed(threadId = 10L)

        assertEquals(AttentionDecision.ShowSendFailureAlert(failure), coordinator.publish(failure))
        assertEquals(listOf(failure), host.sendFailureAlerts)
    }

    // --- Non-alerting events, reconcile battery guard, and subscriber filtering ---

    @Test
    fun `non-alerting message events never post alerts or trigger full inbox reconcile`() {
        val events = listOf(
            MessagingEvent.DeliveryUpdated(threadId = 10L, messageId = 1L),
            MessagingEvent.ReadStateChanged(threadId = 10L, read = false, unreadCount = 1),
            MessagingEvent.MessageDeleted(threadId = 10L, messageId = 1L, isMms = false),
            MessagingEvent.ConversationsChanged(threadId = 10L, reconcile = false),
        )
        events.forEach { event ->
            assertEquals(AttentionDecision.Suppress, coordinator.publish(event))
        }
        coordinator.publish(MessagingEvent.MessageSent(threadId = 10L, messageId = 1L, isMms = false))
        coordinator.publish(MessagingEvent.ReadStateChanged(threadId = 10L, read = true, unreadCount = 0))
        assertTrue(host.incomingAlerts.isEmpty())
        assertTrue(host.sendFailureAlerts.isEmpty())
        assertEquals(0, host.inboxRefreshes)
    }

    // --- Clearing notifications that no longer apply ---

    @Test
    fun `entering a conversation clears its message and send failure notifications`() {
        presence.onConversationActive(Any(), threadId = 10L, isRecycleBin = false)

        assertEquals(listOf(10L), host.cancelledNotifications)
        assertEquals(listOf(10L), host.cancelledSendFailures)
    }

    @Test
    fun `reading a conversation anywhere clears its message notification but not its send failure`() {
        val read = MessagingEvent.ReadStateChanged(threadId = 10L, read = true, unreadCount = 0)
        assertEquals(AttentionDecision.ClearIncomingAlert(10L), coordinator.publish(read))
        assertEquals(listOf(10L), host.cancelledNotifications)

        coordinator.publish(MessagingEvent.ReadStateChanged(threadId = 20L, read = false, unreadCount = 1))
        assertEquals(listOf(10L), host.cancelledNotifications)
        assertTrue(host.cancelledSendFailures.isEmpty())
    }

    @Test
    fun `an action outside messaging events clears the message notification through the coordinator`() {
        coordinator.clearConversationNotification(10L)

        assertEquals(listOf(10L), host.cancelledNotifications)
        assertTrue(host.cancelledSendFailures.isEmpty())
    }

    @Test
    fun `the failed message going through clears its send failure notification but not the message notification`() {
        coordinator.publish(sendFailed(threadId = 10L, messageId = 300L))
        val sent = MessagingEvent.MessageSent(threadId = 10L, messageId = 300L, isMms = false)
        assertEquals(AttentionDecision.ClearSendFailureAlert(sent), coordinator.publish(sent))

        assertEquals(listOf(10L), host.cancelledSendFailures)
        assertTrue(host.cancelledNotifications.isEmpty())
    }

    @Test
    fun `another message going through leaves the send failure notification`() {
        coordinator.publish(sendFailed(threadId = 10L, messageId = 300L))
        coordinator.publish(MessagingEvent.MessageSent(threadId = 10L, messageId = 301L, isMms = false))
        coordinator.publish(MessagingEvent.MessageSent(threadId = 10L, messageId = 300L, isMms = true))

        assertTrue(host.cancelledSendFailures.isEmpty())
    }

    @Test
    fun `the parts of one failed message alert once`() {
        val firstPart = sendFailed(threadId = 10L, messageId = 200L)
        assertEquals(AttentionDecision.ShowSendFailureAlert(firstPart), coordinator.publish(firstPart))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(sendFailed(threadId = 10L, messageId = 200L)))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(sendFailed(threadId = 10L, messageId = 200L)))

        assertEquals(listOf(firstPart), host.sendFailureAlerts)
    }

    @Test
    fun `another failed message alerts again, and so does the same one once its alert was cleared`() {
        val first = sendFailed(threadId = 10L, messageId = 200L)
        val second = sendFailed(threadId = 10L, messageId = 201L)
        val mmsWithSameId = sendFailed(threadId = 10L, messageId = 201L, isMms = true)
        coordinator.publish(first)
        coordinator.publish(second)
        coordinator.publish(mmsWithSameId)

        coordinator.publish(MessagingEvent.MessageSent(threadId = 10L, messageId = 201L, isMms = true))
        coordinator.publish(mmsWithSameId)

        assertEquals(listOf(first, second, mmsWithSameId, mmsWithSameId), host.sendFailureAlerts)
    }

    @Test
    fun `an alert decided before the user opened its conversation is no longer wanted`() {
        val inbox = Any()
        presence.onScreenResumed(inbox)
        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert(event), coordinator.publish(event))
        assertTrue(coordinator.stillAlerts(event))

        presence.onConversationActive(Any(), threadId = 10L, isRecycleBin = false)
        assertFalse(coordinator.stillAlerts(event))
        assertTrue(coordinator.stillAlerts(incoming(threadId = 20L)))
    }

    @Test
    fun `an alert for a conversation muted before it posts is no longer wanted`() {
        val event = incoming(threadId = 10L)
        coordinator.publish(event)
        host.mutedThreads += 10L

        assertFalse(coordinator.stillAlerts(event))
    }

    @Test
    fun `structural conversation change and scheduled send trigger inbox reconcile`() {
        coordinator.publish(MessagingEvent.ConversationsChanged(reconcile = true))
        coordinator.publish(MessagingEvent.ScheduledMessageSent(threadId = 10L, messageId = 5L))
        assertEquals(2, host.inboxRefreshes)
    }

    @Test
    fun `thread relevance filter isolates unrelated conversations, ignores single-thread metadata pauses, and matches global changes`() {
        assertTrue(incoming(threadId = 10L).affectsThread(10L))
        assertFalse(incoming(threadId = 10L).affectsThread(20L))
        assertTrue(MessagingEvent.DeliveryUpdated(threadId = null, messageId = 1L).affectsThread(20L))
        assertFalse(MessagingEvent.DeliveryUpdated(threadId = 10L, messageId = 1L).affectsThread(20L))
        assertTrue(MessagingEvent.ConversationsChanged(threadId = null, reconcile = true).affectsThread(20L))
        assertFalse(MessagingEvent.ConversationsChanged(threadId = 10L, reconcile = false).affectsThread(10L))
        assertFalse(MessagingEvent.ConversationsChanged(threadId = 10L, reconcile = false).affectsThread(20L))
    }

    @Test
    fun `spam relevance filter matches incoming spam and structural edits only`() {
        assertTrue(incoming(threadId = 10L, isSpam = true).affectsSpam())
        assertFalse(incoming(threadId = 10L, isSpam = false).affectsSpam())
        assertTrue(MessagingEvent.ConversationsChanged(reconcile = true).affectsSpam())
        assertTrue(MessagingEvent.MessageDeleted(threadId = 10L, messageId = 1L, isMms = false).affectsSpam())
        assertFalse(MessagingEvent.MessageSent(threadId = 10L, messageId = 1L, isMms = false).affectsSpam())
    }

    @Test
    fun `published events are delivered on the shared flow`() = runBlocking {
        val next = async(start = CoroutineStart.UNDISPATCHED) { coordinator.events.first() }
        val event = incoming(threadId = 10L, isSpam = true)
        coordinator.publish(event)
        assertEquals(event, next.await())
    }
}

package com.afsh.messages.helpers

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAttentionSink : AttentionSink {
    val mutedThreads = mutableSetOf<Long>()
    val incomingAlerts = mutableListOf<MessagingEvent.IncomingMessage>()
    val sendFailureAlerts = mutableListOf<MessagingEvent.SendFailed>()
    val cancelledNotifications = mutableListOf<Long>()
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

    override fun refreshInbox() {
        inboxRefreshes++
    }
}

class MessagingEventsCoordinatorTest {

    private var deviceLockedOrOff = false
    private val sink = FakeAttentionSink()
    private val presence = AppPresenceTracker(
        isDeviceLockedOrOff = { deviceLockedOrOff },
        onEnterConversation = { sink.cancelConversationNotification(it) },
    )
    private val coordinator = MessagingEventCoordinator(presence, sink)

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

        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(sms))
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(mms))
        assertEquals(listOf(sms, mms), sink.incomingAlerts)
    }

    @Test
    fun `incoming SMS and MMS are suppressed when viewing that exact conversation`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)

        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isMms = false)))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, messageId = 101L, isMms = true)))
        assertTrue(sink.incomingAlerts.isEmpty())
        assertEquals(listOf(10L), sink.cancelledNotifications)
    }

    @Test
    fun `incoming message alerts when viewing a different conversation`() {
        val threadB = Any()
        presence.onConversationActive(threadB, threadId = 20L, isRecycleBin = false)

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(event))
        assertEquals(listOf(event), sink.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when in-app on Inbox or another non-conversation screen`() {
        val inbox = Any()
        presence.onScreenResumed(inbox)
        assertEquals(AppPresence.InApp, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(event))
        assertEquals(listOf(event), sink.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when viewing that thread in Recycle Bin mode`() {
        val recycleThread = Any()
        presence.onConversationActive(recycleThread, threadId = 10L, isRecycleBin = true)
        assertEquals(AppPresence.InApp, presence.current())
        assertTrue(sink.cancelledNotifications.isEmpty())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(event))
        assertEquals(listOf(event), sink.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when App Lock screen is active over a conversation`() {
        val threadScreen = Any()
        val lockScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        presence.onLockScreenActive(lockScreen)
        assertEquals(AppPresence.Locked, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(event))
        assertEquals(listOf(event), sink.incomingAlerts)
    }

    @Test
    fun `incoming message alerts when device screen is off or keyguard is locked`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        deviceLockedOrOff = true
        assertEquals(AppPresence.Locked, presence.current())

        val event = incoming(threadId = 10L)
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(event))
        assertEquals(listOf(event), sink.incomingAlerts)
    }

    @Test
    fun `entering conversation while locked defers notification clear until unlocked`() {
        val threadScreen = Any()
        deviceLockedOrOff = true
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertTrue(sink.cancelledNotifications.isEmpty())

        deviceLockedOrOff = false
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)
        assertEquals(listOf(10L), sink.cancelledNotifications)
    }

    @Test
    fun `tablet split view treats open conversation pane as active beside resumed Inbox`() {
        val inbox = Any()
        val threadA = Any()
        presence.onScreenResumed(inbox)
        presence.onConversationActive(threadA, threadId = 10L, isRecycleBin = false)
        presence.onScreenResumed(threadA)

        assertEquals(AppPresence.ViewingConversation(10L), presence.current())
        assertEquals(listOf(10L), sink.cancelledNotifications)
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L)))
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(incoming(threadId = 99L)))

        // Closing the detail pane reverts to InApp (Inbox still resumed)
        presence.onScreenPaused(threadA)
        assertEquals(AppPresence.InApp, presence.current())
        assertEquals(AttentionDecision.ShowIncomingAlert, coordinator.publish(incoming(threadId = 10L)))
    }

    @Test
    fun `resolving a newly created thread id transitions presence and cancels notifications`() {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 0L, isRecycleBin = false)
        assertEquals(AppPresence.InApp, presence.current())
        assertTrue(sink.cancelledNotifications.isEmpty())

        presence.onConversationActive(threadScreen, threadId = 55L, isRecycleBin = false)
        assertEquals(AppPresence.ViewingConversation(55L), presence.current())
        assertEquals(listOf(55L), sink.cancelledNotifications)
    }

    // --- Spam & muted conversations ---

    @Test
    fun `spam SMS and MMS are always suppressed regardless of App Presence`() {
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isSpam = true)))
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L, isMms = true, isSpam = true)))
        assertTrue(sink.incomingAlerts.isEmpty())
    }

    @Test
    fun `muted conversation incoming messages are suppressed regardless of App Presence`() {
        sink.mutedThreads += 10L
        assertEquals(AttentionDecision.Suppress, coordinator.publish(incoming(threadId = 10L)))
        assertTrue(sink.incomingAlerts.isEmpty())
    }

    // --- Outgoing SMS and MMS send failures ---

    @Test
    fun `SMS and MMS send failures surface in-thread reason without tray alert when viewing that thread`() = runBlocking {
        val threadScreen = Any()
        presence.onConversationActive(threadScreen, threadId = 10L, isRecycleBin = false)

        val smsDeferred = async(start = CoroutineStart.UNDISPATCHED) { coordinator.inThreadFailures.first() }
        val smsFailure = sendFailed(threadId = 10L, isMms = false, reason = "No service")
        assertEquals(AttentionDecision.ShowInThreadFailureReason, coordinator.publish(smsFailure))
        assertEquals(smsFailure, smsDeferred.await())

        val mmsDeferred = async(start = CoroutineStart.UNDISPATCHED) { coordinator.inThreadFailures.first() }
        val mmsFailure = sendFailed(threadId = 10L, messageId = 201L, isMms = true, reason = "MMS error")
        assertEquals(AttentionDecision.ShowInThreadFailureReason, coordinator.publish(mmsFailure))
        assertEquals(mmsFailure, mmsDeferred.await())

        assertTrue(sink.sendFailureAlerts.isEmpty())
    }

    @Test
    fun `SMS and MMS send failures post system alert when on another screen, locked, or backgrounded`() {
        val otherThread = Any()
        presence.onConversationActive(otherThread, threadId = 20L, isRecycleBin = false)

        val smsFailure = sendFailed(threadId = 10L, isMms = false)
        assertEquals(AttentionDecision.ShowSendFailureAlert, coordinator.publish(smsFailure))

        presence.onScreenPaused(otherThread)
        assertEquals(AppPresence.Background, presence.current())
        val mmsFailure = sendFailed(threadId = 10L, messageId = 201L, isMms = true)
        assertEquals(AttentionDecision.ShowSendFailureAlert, coordinator.publish(mmsFailure))

        assertEquals(listOf(smsFailure, mmsFailure), sink.sendFailureAlerts)
    }

    @Test
    fun `muting a conversation does not suppress outgoing send failure alerts`() {
        sink.mutedThreads += 10L
        val failure = sendFailed(threadId = 10L)

        assertEquals(AttentionDecision.ShowSendFailureAlert, coordinator.publish(failure))
        assertEquals(listOf(failure), sink.sendFailureAlerts)
    }

    // --- Non-alerting events, reconcile battery guard, and subscriber filtering ---

    @Test
    fun `non-alerting message events never post alerts or trigger full inbox reconcile`() {
        val events = listOf(
            MessagingEvent.MessageSent(threadId = 10L, messageId = 1L, isMms = false),
            MessagingEvent.DeliveryUpdated(threadId = 10L, messageId = 1L),
            MessagingEvent.ReadStateChanged(threadId = 10L, read = true, unreadCount = 0),
            MessagingEvent.MessageDeleted(threadId = 10L, messageId = 1L, isMms = false),
            MessagingEvent.ConversationsChanged(threadId = 10L, reconcile = false),
        )
        events.forEach { event ->
            assertEquals(AttentionDecision.Suppress, coordinator.publish(event))
        }
        assertTrue(sink.incomingAlerts.isEmpty())
        assertTrue(sink.sendFailureAlerts.isEmpty())
        assertEquals(0, sink.inboxRefreshes)
    }

    @Test
    fun `structural conversation change and scheduled send trigger inbox reconcile`() {
        coordinator.publish(MessagingEvent.ConversationsChanged(reconcile = true))
        coordinator.publish(MessagingEvent.ScheduledMessageSent(threadId = 10L, messageId = 5L))
        assertEquals(2, sink.inboxRefreshes)
    }

    @Test
    fun `thread relevance filter isolates unrelated conversations and matches global changes`() {
        assertTrue(incoming(threadId = 10L).affectsThread(10L))
        assertFalse(incoming(threadId = 10L).affectsThread(20L))
        assertTrue(MessagingEvent.DeliveryUpdated(threadId = null, messageId = 1L).affectsThread(20L))
        assertFalse(MessagingEvent.DeliveryUpdated(threadId = 10L, messageId = 1L).affectsThread(20L))
        assertTrue(MessagingEvent.ConversationsChanged(threadId = null, reconcile = true).affectsThread(20L))
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

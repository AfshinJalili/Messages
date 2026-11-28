package com.afsh.messages.messaging

import com.afsh.messages.models.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val QUEUED = 6
private const val FAILED = 5

/** Room's scheduled messages in memory; each step the sender takes is recorded in order. */
private class FakeScheduledPlatform : ScheduledMessageSender.Platform {
    val scheduled = LinkedHashMap<Long, Message>()
    val steps = ArrayList<String>()
    var sendError: Throwable? = null
    /** Runs while the transport is sending, as a second Send now or the alarm would. */
    var duringSend: (() -> Unit)? = null

    override fun scheduledMessage(messageId: Long) = scheduled[messageId]
    override fun send(message: Message) {
        duringSend?.invoke()
        sendError?.let { throw it }
        steps += "send ${message.id} on SIM ${message.subscriptionId}"
    }
    override fun dropScheduled(messageId: Long) {
        scheduled.remove(messageId)
        steps += "drop $messageId"
    }
    override fun dropEmptyTemporaryThread(threadId: Long) {
        steps += "drop thread $threadId if empty"
    }
    override fun publishSent(threadId: Long, messageId: Long) {
        steps += "sent $messageId in $threadId"
    }
    override fun markFailed(messageId: Long) {
        scheduled[messageId]?.let { scheduled[messageId] = it.copy(type = FAILED) }
        steps += "mark $messageId failed"
    }
    override fun publishFailed(message: Message, error: Throwable, inTelephony: Boolean) {
        val where = if (inTelephony) "in Telephony" else "in ${message.threadId}"
        steps += "not sent ${message.id} $where: ${error.message}"
    }
}

private fun scheduled(id: Long, threadId: Long) = Message(
    id = id, body = "later", type = QUEUED, status = -1, participants = ArrayList(), date = 1, read = false,
    threadId = threadId, isMMS = false, attachment = null, senderPhoneNumber = "", senderName = "", senderPhotoUri = "",
    subscriptionId = 1, isScheduled = true,
)

class ScheduledMessageSenderTest {
    private val platform = FakeScheduledPlatform()
    private val sender = ScheduledMessageSender(platform)

    @Test
    fun `a due message is sent, dropped with its temporary thread, and announced`() {
        platform.scheduled[7] = scheduled(7, threadId = 7)

        assertTrue(sender.send(messageId = 7))

        assertEquals(listOf("send 7 on SIM 1", "drop 7", "drop thread 7 if empty", "sent 7 in 7"), platform.steps)
    }

    @Test
    fun `a message moved to another thread after its alarm was set is still sent, for that thread`() {
        // The alarm still names temporary thread 7; reconcile has since moved the message to thread 50.
        platform.scheduled[7] = scheduled(7, threadId = 50)

        sender.send(messageId = 7)

        assertEquals(listOf("send 7 on SIM 1", "drop 7", "drop thread 50 if empty", "sent 7 in 50"), platform.steps)
    }

    @Test
    fun `send now can pick another SIM`() {
        platform.scheduled[7] = scheduled(7, threadId = 50)

        sender.send(messageId = 7, subscriptionId = 3)

        assertEquals("send 7 on SIM 3", platform.steps.first())
    }

    @Test
    fun `nothing is sent when the alarm's message is gone`() {
        assertFalse(sender.send(messageId = 7))

        assertEquals(emptyList<String>(), platform.steps)
    }

    @Test
    fun `a message that never reached the device stays, marked failed, and the person is told`() {
        platform.scheduled[7] = scheduled(7, threadId = 50)
        platform.sendError = IllegalStateException("could not save")

        assertFalse(sender.send(messageId = 7))

        assertEquals(listOf("mark 7 failed", "not sent 7 in 50: could not save"), platform.steps)
        assertEquals(FAILED, platform.scheduled.getValue(7).type)
    }

    @Test
    fun `a message Telephony stored before the send failed goes from the schedule, and the person is told`() {
        // Telephony holds it as a failed message (or holds the parts already sent) with its own Retry.
        platform.scheduled[7] = scheduled(7, threadId = 50)
        platform.sendError = StoredSendException(IllegalStateException("radio off"))

        assertFalse(sender.send(messageId = 7))

        assertEquals(
            listOf("drop 7", "drop thread 50 if empty", "sent 7 in 50", "not sent 7 in Telephony: java.lang.IllegalStateException: radio off"),
            platform.steps,
        )
    }

    @Test
    fun `an error thrown by the transport counts as a failed send too`() {
        platform.scheduled[7] = scheduled(7, threadId = 50)
        platform.sendError = NoClassDefFoundError("mms")

        sender.send(messageId = 7)

        assertEquals(listOf("mark 7 failed", "not sent 7 in 50: mms"), platform.steps)
    }

    @Test
    fun `a message that is still sending is not sent a second time`() {
        platform.scheduled[7] = scheduled(7, threadId = 50)
        var second: Boolean? = null
        platform.duringSend = {
            platform.duringSend = null
            second = ScheduledMessageSender(platform).send(messageId = 7)
        }

        assertTrue(sender.send(messageId = 7))

        assertEquals(false, second)
        assertEquals(listOf("send 7 on SIM 1"), platform.steps.filter { it.startsWith("send") })
    }

    @Test
    fun `a message whose send failed can be sent again`() {
        platform.scheduled[7] = scheduled(7, threadId = 50)
        platform.sendError = IllegalStateException("could not save")
        assertFalse(sender.send(messageId = 7))

        platform.sendError = null
        assertTrue(sender.send(messageId = 7))

        assertEquals("send 7 on SIM 1", platform.steps[2])
    }
}

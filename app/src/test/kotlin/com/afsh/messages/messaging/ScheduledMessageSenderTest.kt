package com.afsh.messages.messaging

import com.afsh.messages.models.Message
import org.junit.Assert.assertEquals
import org.junit.Test

private const val QUEUED = 6

/** Room's scheduled messages in memory; each step the sender takes is recorded in order. */
private class FakeScheduledPlatform : ScheduledMessageSender.Platform {
    val scheduled = LinkedHashMap<Long, Message>()
    val steps = ArrayList<String>()
    val errors = ArrayList<Throwable>()
    var sendError: Throwable? = null

    override fun scheduledMessage(threadId: Long, messageId: Long) = scheduled[messageId]?.takeIf { it.threadId == threadId }
    override fun send(message: Message) {
        sendError?.let { throw it }
        steps += "send ${message.id}"
    }
    override fun dropScheduled(messageId: Long) {
        scheduled.remove(messageId)
        steps += "drop $messageId"
    }
    override fun removeConversation(threadId: Long) {
        steps += "remove conversation $threadId"
    }
    override fun publishSent(threadId: Long, messageId: Long) {
        steps += "sent $messageId in $threadId"
    }
    override fun failed(error: Throwable) {
        errors += error
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

        sender.sendDue(threadId = 7, messageId = 7)

        assertEquals(listOf("send 7", "drop 7", "remove conversation 7", "sent 7 in 7"), platform.steps)
    }

    @Test
    fun `nothing is sent when the alarm's message is gone`() {
        sender.sendDue(threadId = 7, messageId = 7)

        assertEquals(emptyList<String>(), platform.steps)
    }

    @Test
    fun `a send that throws is reported`() {
        platform.scheduled[7] = scheduled(7, threadId = 7)
        val error = IllegalStateException("no SIM")
        platform.sendError = error

        sender.sendDue(threadId = 7, messageId = 7)

        assertEquals(listOf(error), platform.errors)
    }
}

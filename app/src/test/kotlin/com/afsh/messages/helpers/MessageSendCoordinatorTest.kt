package com.afsh.messages.helpers

import android.net.TestUri
import android.net.Uri
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Conversation
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.afsh.messages.ui.newmessage.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_000_000_000L
private const val QUEUED = 6
private const val SENT = 2

private class FakeSendPlatform : MessageSendCoordinator.Platform {
    data class Transmit(val text: String, val addresses: List<String>, val subscriptionId: Int, val attachments: List<Attachment>, val resend: Message?, val forceMms: Boolean)

    override var useSimpleCharacters = false
    override var sendGroupAsMms = false
    var longText = 10
    var defaultSms = 9
    var exactAlarms = true
    var transmitError: Throwable? = null
    var alarmError: Exception? = null
    val transmits = ArrayList<Transmit>()
    var provider = listOf<Message>()
    val alarms = ArrayList<Message>()
    val dropped = ArrayList<Long>()
    val temporaryThreads = ArrayList<Long>()
    val snippetBumps = ArrayList<Pair<Long, String>>()
    val snippetReplacements = ArrayList<Triple<Long, String, String>>()
    val persisted = ArrayList<Message>()
    val written = ArrayList<List<Attachment>>()

    override fun isLongMms(text: String) = text.length > longText
    override fun defaultSmsSubscriptionId() = defaultSms
    override fun transmit(text: String, addresses: List<String>, subscriptionId: Int, attachments: List<Attachment>, resend: Message?, forceMms: Boolean) {
        transmitError?.let { throw it }
        transmits += Transmit(text, addresses, subscriptionId, attachments, resend, forceMms)
    }
    override fun latestMessages(threadId: Long, limit: Int) = provider.take(limit)
    override fun canScheduleExactAlarms() = exactAlarms
    override fun setAlarm(message: Message) {
        alarmError?.let { throw it }
        alarms += message
    }
    override fun dropScheduled(messageId: Long) { dropped += messageId }
    override fun createScheduledConversation(message: Message, threadId: Long, cached: Conversation?) { temporaryThreads += threadId }
    override fun bumpAfterScheduling(threadId: Long, snippet: String) { snippetBumps += threadId to snippet }
    override fun replaceSnippet(threadId: Long, old: String, new: String) { snippetReplacements += Triple(threadId, old, new) }
    override fun persist(message: Message) { persisted += message }
    override fun writeAttachments(attachments: List<Attachment>, destination: Uri): Boolean {
        written += attachments
        return true
    }
}

private class FakeSendHost : MessageSendCoordinator.Host {
    override var threadId = 1L
    override var conversation: Conversation? = null
    var loaded = listOf<Message>()
    val shown = ArrayList<Message>()
    val events = ArrayList<String>()
    val failures = ArrayList<Any?>()
    val retryFailures = ArrayList<Message>()

    override fun loaded() = loaded
    override fun moveToThread(threadId: Long) {
        this.threadId = threadId
        events += "moved $threadId"
    }
    override fun showStored(message: Message) { shown += message }
    override fun reload() { events += "reload" }
    override fun sent() { events += "sent" }
    override fun scheduled() { events += "scheduled" }
    override fun retryFailed(message: Message) { retryFailures += message }
    override fun timeInPast(retryMillis: Long?) { events += "past $retryMillis" }
    override fun askForExactAlarms() { events += "ask alarms" }
    override fun attachmentsSaved() { events += "saved" }
    override fun failed(error: Exception) { failures += error }
    override fun failed(message: String?) { failures += message }
}

private fun provider(id: Long, threadId: Long = 1) = Message(
    id = id, body = "m$id", type = SENT, status = -1, participants = ArrayList(), date = id.toInt(), read = true, threadId = threadId,
    isMMS = false, attachment = null, senderPhoneNumber = "", senderName = "", senderPhotoUri = "", subscriptionId = 1,
)

private fun attachment(uri: String, mimetype: String = "image/png", filename: String = "") =
    Attachment(null, 1, uri, mimetype, 0, 0, filename)

class MessageSendCoordinatorTest {
    private val platform = FakeSendPlatform()
    private val host = FakeSendHost()
    private val people = ParticipantResolver(FakeParticipantSource())
    private val sender = MessageSendCoordinator(platform, people, host, clock = { NOW }, newId = { 77L }, background = { it() })

    init {
        people.fallBackTo("Mina", "111")
    }

    @Test
    fun `sending now goes to every person on the chosen SIM and stores only what is new`() {
        people.add(Recipient("222", "Sara"))
        host.loaded = listOf(provider(1))
        platform.provider = listOf(provider(2), provider(1))

        sender.send("hello", attachments = emptyList(), subscriptionId = 3, scheduledMillis = null)

        assertEquals(listOf(FakeSendPlatform.Transmit("hello", listOf("111", "222"), 3, emptyList(), null, false)), platform.transmits)
        assertEquals(listOf(2L), host.shown.map { it.id })
        assertEquals(listOf(2L), platform.persisted.map { it.id })
        assertEquals(listOf("sent"), host.events)
    }

    @Test
    fun `without a chosen SIM the system default sends`() {
        sender.send("hello", emptyList(), subscriptionId = null, scheduledMillis = null)
        assertEquals(9, platform.transmits.single().subscriptionId)
    }

    @Test
    fun `simple characters drop the accents before sending`() {
        platform.useSimpleCharacters = true
        sender.send("café", emptyList(), null, null)
        assertEquals("cafe", platform.transmits.single().text)
    }

    @Test
    fun `a failed send is reported and the composer keeps the text`() {
        platform.transmitError = IllegalStateException("radio off")
        sender.send("hello", emptyList(), null, null)

        platform.transmitError = OutOfMemoryError("too big")
        sender.send("hello", emptyList(), null, null)

        assertTrue(failuresAre(host.failures[0], IllegalStateException::class.java))
        assertEquals("too big", host.failures[1])
        assertEquals(emptyList<String>(), host.events)
    }

    @Test
    fun `a message goes as MMS with attachments, to a group with group MMS on, or when long`() {
        assertFalse(sender.isMms("hi", hasAttachments = false))
        assertTrue(sender.isMms("hi", hasAttachments = true))
        assertTrue(sender.isMms("a much longer text", hasAttachments = false))

        people.add(Recipient("222", "Sara"))
        assertFalse(sender.isMms("hi", false))
        platform.sendGroupAsMms = true
        assertTrue(sender.isMms("hi", false))
    }

    @Test
    fun `a time less than a second ahead is asked for again`() {
        sender.send("later", emptyList(), 3, scheduledMillis = NOW + 999)

        assertEquals(listOf("past ${NOW + 999}"), host.events)
        assertTrue(platform.alarms.isEmpty())
    }

    @Test
    fun `scheduling the first message makes a temporary thread and sets the alarm`() {
        val at = NOW + 60_000
        sender.send("later", emptyList(), 3, scheduledMillis = at)

        val message = platform.alarms.single()
        assertEquals(77L, message.id)
        assertEquals(77L, message.threadId)
        assertEquals(QUEUED, message.type)
        assertTrue(message.isScheduled)
        assertEquals((at / 1000).toInt(), message.date)
        assertEquals(3, message.subscriptionId)
        assertEquals(listOf("111"), message.participants.flatMap { p -> p.phoneNumbers.map { it.value } })
        assertEquals(listOf(77L), platform.temporaryThreads)
        assertEquals(listOf(77L to "later"), platform.snippetBumps)
        assertEquals(listOf(message), host.shown)
        assertEquals(listOf(message), platform.persisted)
        assertEquals(listOf("moved 77", "scheduled"), host.events)
        assertTrue(platform.transmits.isEmpty())
    }

    @Test
    fun `scheduling in a thread with messages keeps the thread and bumps its snippet`() {
        host.loaded = listOf(provider(1))
        host.threadId = 5

        sender.send("later", emptyList(), 3, NOW + 60_000)

        assertEquals(5L, platform.alarms.single().threadId)
        assertEquals(listOf(5L to "later"), platform.snippetBumps)
        assertTrue(platform.temporaryThreads.isEmpty())
    }

    @Test
    fun `an edited schedule replaces the alarm and the snippet it set`() {
        val old = provider(4, threadId = 5).copy(body = "old", isScheduled = true)
        host.threadId = 5

        sender.updateScheduled(old, "new", NOW + 120_000)

        val updated = platform.alarms.single()
        assertEquals("new", updated.body)
        assertEquals(((NOW + 120_000) / 1000).toInt(), updated.date)
        assertEquals(listOf(updated), platform.persisted)
        assertEquals(listOf(Triple(5L, "old", "new")), platform.snippetReplacements)
    }

    @Test
    fun `an edited schedule refuses the past`() {
        val old = provider(4, threadId = 5).copy(body = "old", isScheduled = true)
        host.threadId = 5

        sender.updateScheduled(old, "new", NOW)

        assertEquals(listOf("past null"), host.events)
        assertTrue(platform.alarms.isEmpty())
        assertTrue(platform.snippetReplacements.isEmpty())
    }

    @Test
    fun `send now sends the scheduled text on its SIM and drops the schedule`() {
        val scheduled = provider(4).copy(body = "later", subscriptionId = 2, isScheduled = true)

        sender.sendScheduledNow(scheduled, attachments = emptyList())

        assertEquals("later", platform.transmits.single().text)
        assertEquals(2, platform.transmits.single().subscriptionId)
        assertEquals(listOf(4L), platform.dropped)
        assertEquals(listOf("sent", "reload"), host.events)
    }

    @Test
    fun `a retry resends the failed row as it went, and reloads`() {
        val failedMms = provider(4).copy(isMMS = true, attachment = MessageAttachment(4, "", arrayListOf(attachment("content://a"))))

        sender.resend(failedMms, subscriptionId = null)

        val transmit = platform.transmits.single()
        assertEquals(failedMms, transmit.resend)
        assertTrue(transmit.forceMms)
        assertEquals(9, transmit.subscriptionId)
        assertEquals(1, transmit.attachments.size)
        assertEquals(listOf("reload"), host.events)
    }

    @Test
    fun `a retry that cannot send gives the message its Retry back`() {
        platform.transmitError = IllegalStateException()
        val failed = provider(4)

        sender.resend(failed, 1)

        assertEquals(listOf(failed), host.retryFailures)
        assertEquals(1, host.failures.size)
    }

    @Test
    fun `a send waits for a refresh before a stored message stops asking for one`() {
        assertTrue(sender.needsRefresh)
        sender.reloaded()
        assertFalse(sender.needsRefresh)
        sender.send("hello", emptyList(), null, null)
        assertTrue(sender.needsRefresh)
    }

    @Test
    fun `scheduling needs exact alarms`() {
        var opened = 0
        sender.whenExactAlarmsAllowed { opened++ }
        platform.exactAlarms = false
        sender.whenExactAlarmsAllowed { opened++ }

        assertEquals(1, opened)
        assertEquals(listOf("ask alarms"), host.events)
    }

    @Test
    fun `one attachment saves as a file named from its path, several into a folder`() {
        val photo = attachment("content://mms/part/photo.png", "image/png")

        assertEquals(
            MessageSendCoordinator.SaveRequest.File("image/png", "photo.png"),
            sender.saveAttachments(listOf(photo)),
        )
        assertEquals(MessageSendCoordinator.SaveRequest.Folder, sender.saveAttachments(listOf(photo, photo)))
        assertNull(sender.saveAttachments(emptyList()))
    }

    @Test
    fun `the kept attachments are written once and reported saved`() {
        val photo = attachment("content://mms/part/photo.png")
        sender.saveAttachments(listOf(photo))

        sender.saveTo(TestUri("content://file"))
        sender.saveTo(TestUri("content://file"))

        assertEquals(listOf(listOf(photo)), platform.written)
        assertEquals(listOf("saved"), host.events)
        assertEquals(1, host.failures.size)
    }

    @Test
    fun `saving with nothing kept, as after the screen was recreated, fails instead of saying saved`() {
        sender.saveTo(TestUri("content://folder"))

        assertTrue(platform.written.isEmpty())
        assertFalse("saved" in host.events)
        assertEquals(1, host.failures.size)
    }

    @Test
    fun `scheduling reports an alarm the worker cannot set instead of crashing`() {
        platform.alarmError = SecurityException("exact alarms revoked")

        onWorker.send("later", emptyList(), 3, NOW + 60_000)
        runWorker()

        assertEquals(listOf<Any?>("exact alarms revoked"), host.failures)
        assertFalse("scheduled" in host.events)
    }

    @Test
    fun `an edited schedule reports an alarm the worker cannot set instead of crashing`() {
        platform.alarmError = SecurityException("exact alarms revoked")
        val old = provider(4, threadId = 5).copy(body = "old", isScheduled = true)

        onWorker.updateScheduled(old, "new", NOW + 120_000)
        runWorker()

        assertEquals(listOf<Any?>("exact alarms revoked"), host.failures)
        assertTrue(platform.snippetReplacements.isEmpty())
    }

    /** Work handed to the background runs later, on the worker, as on the phone: its exceptions don't reach the caller. */
    private val worker = ArrayList<() -> Unit>()
    private val onWorker = MessageSendCoordinator(platform, people, host, clock = { NOW }, newId = { 77L }, background = { worker += it })

    private fun runWorker() {
        while (worker.isNotEmpty()) worker.removeAt(0)()
    }

    private fun failuresAre(failure: Any?, type: Class<*>) = type.isInstance(failure)
}

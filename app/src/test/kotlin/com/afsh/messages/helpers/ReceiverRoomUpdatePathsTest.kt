package com.afsh.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Documents which targeted Room write each message-path receiver performs before publishing a
 * typed [MessagingEvent] that skips a full Telephony reconcile. Without these writes the inbox
 * row, badge, and open thread would only refresh after a full Telephony pass.
 */
class ReceiverRoomUpdatePathsTest {

    private val receivers = File("src/main/kotlin/com/afsh/messages/receivers")

    private fun source(name: String): String =
        File(receivers, name).readText()

    @Test
    fun `sms receive writes conversation and message then publishes IncomingMessage`() {
        val text = source("SmsReceiver.kt")
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("messagesDB.insertOrUpdate"))
        assertTrue(text.contains("MessagingEvent.IncomingMessage"))
        assertFalse(text.contains("showMessageNotification"))
    }

    @Test
    fun `mms receive writes conversation then publishes IncomingMessage`() {
        val text = source("MmsReceiver.kt")
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("refreshUnreadCounts"))
        assertTrue(text.contains("MessagingEvent.IncomingMessage"))
        assertFalse(text.contains("showMessageNotification"))
    }

    @Test
    fun `mark as read writes read state without full reconcile`() {
        val text = source("MarkAsReadReceiver.kt")
        assertTrue(text.contains("markThreadMessagesRead"))
        assertFalse(text.contains("refreshInbox"))
    }

    @Test
    fun `sms sent status updates message type then publishes MessageSent or SendFailed`() {
        val text = source("SmsStatusSentReceiver.kt")
        assertTrue(text.contains("updateType"))
        assertTrue(text.contains("MessagingEvent.MessageSent"))
        assertTrue(text.contains("MessagingEvent.SendFailed"))
        assertFalse(text.contains("ProcessLifecycleOwner"))
    }

    @Test
    fun `mms sent syncs the one thread then publishes MessageSent or SendFailed`() {
        val text = source("MmsSentReceiver.kt")
        assertTrue(text.contains("ConversationSync"))
        assertTrue(text.contains("syncThreadForMmsUri"))
        assertTrue(text.contains("MessagingEvent.MessageSent"))
        assertTrue(text.contains("MessagingEvent.SendFailed"))
    }

    @Test
    fun `delete updates last conversation message then publishes MessageDeleted`() {
        val text = source("DeleteSmsReceiver.kt")
        assertTrue(text.contains("updateLastConversationMessage"))
        assertTrue(text.contains("MessagingEvent.MessageDeleted"))
    }

    @Test
    fun `conversation sync helper still writes through insertOrUpdateConversation`() {
        val text = File("src/main/kotlin/com/afsh/messages/helpers/ConversationSync.kt").readText()
        assertTrue(text.contains("fun syncThread"))
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("getConversations"))
    }
}

package com.afsh.messages.helpers

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Documents which Room write each message-path receiver uses when
 * [refreshConversations] runs with `reconcile = false`. Without these writes the inbox
 * row, badge and open thread would only refresh after a full Telephony pass.
 */
class ReceiverRoomUpdatePathsTest {

    private val receivers = File("src/main/kotlin/com/afsh/messages/receivers")

    private fun source(name: String): String =
        File(receivers, name).readText()

    @Test
    fun `sms receive writes conversation and message then skips reconcile`() {
        val text = source("SmsReceiver.kt")
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("messagesDB.insertOrUpdate"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
        assertTrue(text.contains("showReceivedMessageNotification") || text.contains("spamReason"))
    }

    @Test
    fun `mms receive writes conversation then skips reconcile`() {
        val text = source("MmsReceiver.kt")
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("refreshUnreadCounts"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
    }

    @Test
    fun `mark as read writes read state then skips reconcile`() {
        val text = source("MarkAsReadReceiver.kt")
        assertTrue(text.contains("markThreadMessagesRead") || text.contains("markRead"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
    }

    @Test
    fun `sms sent status updates message type then skips reconcile`() {
        val text = source("SmsStatusSentReceiver.kt")
        assertTrue(text.contains("updateType"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
    }

    @Test
    fun `mms sent syncs the one thread then skips reconcile`() {
        val text = source("MmsSentReceiver.kt")
        assertTrue(text.contains("ConversationSync"))
        assertTrue(text.contains("syncThreadForMmsUri") || text.contains("syncThread"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
    }

    @Test
    fun `delete updates last conversation message then skips reconcile`() {
        val text = source("DeleteSmsReceiver.kt")
        assertTrue(text.contains("updateLastConversationMessage"))
        assertTrue(text.contains("refreshConversations(reconcile = false)"))
    }

    @Test
    fun `conversation sync helper still writes through insertOrUpdateConversation`() {
        val text = File("src/main/kotlin/com/afsh/messages/helpers/ConversationSync.kt").readText()
        assertTrue(text.contains("fun syncThread"))
        assertTrue(text.contains("insertOrUpdateConversation"))
        assertTrue(text.contains("getConversations"))
    }
}

package com.afsh.messages.ui.inbox

import com.afsh.messages.models.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sender rules in inbox multi-select are one action, Spam and blocking, whatever the senders' current rules:
 * the sheet it opens shows where they stand.
 *
 * Tests use two or more selected rows so Dial (which needs Android Patterns) is not evaluated.
 */
class InboxActionVisibilityTest {
    @Test
    fun `one-to-one selections offer one spam and blocking action`() {
        val actions = availableActions(listOf(row(1, "0912"), row(2, "0913")), archiveAvailable = true)
        assertEquals(1, actions.count { it == InboxAction.SPAM_AND_BLOCKING })
    }

    @Test
    fun `a group selection still offers spam and blocking`() {
        val actions = availableActions(listOf(row(1, "", group = true), row(2, "0913")), archiveAvailable = true)
        assertTrue(InboxAction.SPAM_AND_BLOCKING in actions)
    }

    @Test
    fun `spam and blocking sits in the overflow, not the bar`() {
        val (bar, overflow) = availableActions(listOf(row(1, "0912"), row(2, "0913")), archiveAvailable = true).splitForBar()
        assertTrue(InboxAction.SPAM_AND_BLOCKING !in bar)
        assertTrue(InboxAction.SPAM_AND_BLOCKING in overflow)
    }

    private fun row(id: Long, number: String, group: Boolean = false) = InboxRow(
        conversation = Conversation(
            threadId = id,
            snippet = "",
            date = 0,
            read = true,
            title = "T$id",
            photoUri = "",
            isGroupConversation = group,
            phoneNumber = number,
        ),
        draft = null,
        pinned = false,
        muted = false,
    )
}

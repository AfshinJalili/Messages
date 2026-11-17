package com.afsh.messages.ui.inbox

import com.afsh.messages.models.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Always spam / Never spam in inbox multi-select follow list membership: hide Always spam when
 * every selected sender is already on spamNumbers, and hide Never spam when every selected 1:1
 * sender is already allowlisted (or the selection includes a group).
 *
 * Tests use two or more selected rows so Dial (which needs Android Patterns) is not evaluated.
 */
class InboxActionVisibilityTest {
    @Test
    fun `one-to-one selections offer always and never spam`() {
        val actions = availableActions(listOf(row(1, "0912"), row(2, "0913")), archiveAvailable = true)
        assertTrue(InboxAction.ALWAYS_SPAM in actions)
        assertTrue(InboxAction.NEVER_SPAM in actions)
    }

    @Test
    fun `always spam is hidden when every selected sender is already listed`() {
        val actions = availableActions(
            listOf(row(1, "0912"), row(2, "0913")),
            archiveAvailable = true,
            spamNumbers = setOf("0912", "0913"),
        )
        assertFalse(InboxAction.ALWAYS_SPAM in actions)
        assertTrue(InboxAction.NEVER_SPAM in actions)
    }

    @Test
    fun `never spam is hidden when every selected sender is already allowlisted`() {
        val actions = availableActions(
            listOf(row(1, "0912"), row(2, "0913")),
            archiveAvailable = true,
            allowedNumbers = setOf("0912", "0913"),
        )
        assertFalse(InboxAction.NEVER_SPAM in actions)
        assertTrue(InboxAction.ALWAYS_SPAM in actions)
    }

    @Test
    fun `never spam stays off a group selection`() {
        val actions = availableActions(
            listOf(row(1, "", group = true)),
            archiveAvailable = true,
        )
        assertTrue(InboxAction.ALWAYS_SPAM in actions)
        assertFalse(InboxAction.NEVER_SPAM in actions)
    }

    @Test
    fun `block on this phone stays off a group selection`() {
        val actions = availableActions(
            listOf(row(1, "", group = true)),
            archiveAvailable = true,
        )
        assertFalse(InboxAction.BLOCK_ON_PHONE in actions)
        assertTrue(InboxAction.ALWAYS_SPAM in actions)
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

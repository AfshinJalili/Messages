package com.afsh.messages.ui

import com.afsh.messages.ui.inbox.InboxAction
import com.afsh.messages.ui.thread.ThreadMenuAction
import com.afsh.messages.ui.thread.ThreadFacts
import com.afsh.messages.ui.thread.threadMenuActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Count-sensitive multi-select / menu actions must resolve through `<plurals>` so English and
 * Persian labels agree with the selection size. Verb-only actions stay plain strings.
 */
class ActionLabelQuantityTest {
    @Test
    fun `always and never spam labels are quantity-aware`() {
        assertTrue(InboxAction.ALWAYS_SPAM.labelIsQuantity)
        assertTrue(InboxAction.NEVER_SPAM.labelIsQuantity)
        assertTrue(InboxAction.BLOCK_ON_PHONE.labelIsQuantity)
        assertTrue(ThreadMenuAction.ALWAYS_SPAM.labelIsQuantity)
        assertTrue(ThreadMenuAction.NEVER_SPAM.labelIsQuantity)
        assertTrue(ThreadMenuAction.BLOCK_ON_PHONE.labelIsQuantity)
    }

    @Test
    fun `verb-only inbox actions stay plain strings`() {
        assertFalse(InboxAction.MUTE.labelIsQuantity)
        assertFalse(InboxAction.ARCHIVE.labelIsQuantity)
        assertFalse(InboxAction.DELETE.labelIsQuantity)
        assertFalse(InboxAction.MARK_READ.labelIsQuantity)
        assertFalse(InboxAction.PIN.labelIsQuantity)
    }

    @Test
    fun `never spam is offered on a normal one-to-one thread`() {
        val actions = threadMenuActions(
            ThreadFacts(participantCount = 1, hasConversation = true, hasItems = true, firstNumber = "0912"),
        )
        assertTrue(ThreadMenuAction.NEVER_SPAM in actions)
        assertTrue(ThreadMenuAction.ALWAYS_SPAM in actions)
        assertTrue(ThreadMenuAction.BLOCK_ON_PHONE in actions)
    }

    @Test
    fun `never spam and block on phone stay off a group thread`() {
        val actions = threadMenuActions(
            ThreadFacts(participantCount = 3, hasConversation = true, hasItems = true),
        )
        assertFalse(ThreadMenuAction.NEVER_SPAM in actions)
        assertFalse(ThreadMenuAction.BLOCK_ON_PHONE in actions)
    }

    @Test
    fun `always spam is hidden when the sender is already on the list`() {
        val actions = threadMenuActions(
            ThreadFacts(participantCount = 1, hasConversation = true, hasItems = true, senderBlocked = true),
        )
        assertFalse(ThreadMenuAction.ALWAYS_SPAM in actions)
        assertTrue(ThreadMenuAction.NEVER_SPAM in actions)
    }

    @Test
    fun `never spam is hidden when the sender is already allowlisted`() {
        val actions = threadMenuActions(
            ThreadFacts(participantCount = 1, hasConversation = true, hasItems = true, senderAllowed = true),
        )
        assertFalse(ThreadMenuAction.NEVER_SPAM in actions)
        assertTrue(ThreadMenuAction.ALWAYS_SPAM in actions)
    }
}

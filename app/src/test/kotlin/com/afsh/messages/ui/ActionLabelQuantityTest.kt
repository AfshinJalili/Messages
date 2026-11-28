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
    fun `verb-only inbox actions stay plain strings`() {
        assertFalse(InboxAction.MUTE.labelIsQuantity)
        assertFalse(InboxAction.ARCHIVE.labelIsQuantity)
        assertFalse(InboxAction.DELETE.labelIsQuantity)
        assertFalse(InboxAction.MARK_READ.labelIsQuantity)
        assertFalse(InboxAction.PIN.labelIsQuantity)
    }

    @Test
    fun `spam and blocking is one plain label in both menus`() {
        assertFalse(InboxAction.SPAM_AND_BLOCKING.labelIsQuantity)
        assertFalse(ThreadMenuAction.SPAM_AND_BLOCKING.labelIsQuantity)
    }

    @Test
    fun `spam and blocking is offered on one-to-one and group threads alike`() {
        val single = threadMenuActions(ThreadFacts(participantCount = 1, hasConversation = true, hasItems = true, firstNumber = "0912"))
        val group = threadMenuActions(ThreadFacts(participantCount = 3, hasConversation = true, hasItems = true))
        assertTrue(ThreadMenuAction.SPAM_AND_BLOCKING in single)
        assertTrue(ThreadMenuAction.SPAM_AND_BLOCKING in group)
    }

    @Test
    fun `the sender's current rule does not change the menu`() {
        val facts = ThreadFacts(participantCount = 1, hasConversation = true, hasItems = true)
        val plain = threadMenuActions(facts)
        assertTrue(plain == threadMenuActions(facts.copy(alwaysSpam = true)))
        assertTrue(plain == threadMenuActions(facts.copy(blockedOnPhone = true)))
    }
}

package com.afsh.messages.ui

import com.afsh.messages.ui.inbox.InboxAction
import com.afsh.messages.ui.thread.ThreadMenuAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Count-sensitive multi-select / menu actions must resolve through `<plurals>` so English and
 * Persian labels agree with the selection size. Verb-only actions stay plain strings.
 */
class ActionLabelQuantityTest {
    @Test
    fun `block and allow sender labels are quantity-aware`() {
        assertTrue(InboxAction.BLOCK.labelIsQuantity)
        assertTrue(ThreadMenuAction.BLOCK.labelIsQuantity)
        assertTrue(ThreadMenuAction.ALLOW_SENDER.labelIsQuantity)
    }

    @Test
    fun `verb-only inbox actions stay plain strings`() {
        assertFalse(InboxAction.MUTE.labelIsQuantity)
        assertFalse(InboxAction.ARCHIVE.labelIsQuantity)
        assertFalse(InboxAction.DELETE.labelIsQuantity)
        assertFalse(InboxAction.MARK_READ.labelIsQuantity)
        assertFalse(InboxAction.PIN.labelIsQuantity)
    }
}

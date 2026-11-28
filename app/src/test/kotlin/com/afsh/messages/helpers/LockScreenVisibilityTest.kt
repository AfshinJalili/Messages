package com.afsh.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lock-screen setting decides what a message notification shows where Android hides content. */
class LockScreenVisibilityTest {

    @Test
    fun `sender and message shows both`() {
        assertTrue(lockScreenShowsSender(LOCK_SCREEN_SENDER_MESSAGE))
        assertTrue(lockScreenShowsMessage(LOCK_SCREEN_SENDER_MESSAGE))
    }

    @Test
    fun `sender only hides the message, and so a code in it`() {
        assertTrue(lockScreenShowsSender(LOCK_SCREEN_SENDER))
        assertFalse(lockScreenShowsMessage(LOCK_SCREEN_SENDER))
    }

    @Test
    fun `nothing hides the sender and the message`() {
        assertFalse(lockScreenShowsSender(LOCK_SCREEN_NOTHING))
        assertFalse(lockScreenShowsMessage(LOCK_SCREEN_NOTHING))
    }

    @Test
    fun `an unknown setting shows as little as nothing does`() {
        assertFalse(lockScreenShowsSender(0))
        assertFalse(lockScreenShowsMessage(0))
    }
}

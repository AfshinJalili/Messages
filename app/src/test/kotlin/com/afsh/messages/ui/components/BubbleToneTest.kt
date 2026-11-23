package com.afsh.messages.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleToneTest {
    @Test
    fun `a received message takes the received tone`() {
        assertEquals(BubbleTone.Received, BubbleTone.of(incoming = true))
    }

    @Test
    fun `a sent message takes the sent tone`() {
        assertEquals(BubbleTone.Sent, BubbleTone.of(incoming = false))
    }

    @Test
    fun `a failed send takes the failed tone`() {
        assertEquals(BubbleTone.Failed, BubbleTone.of(incoming = false, failed = true))
    }

    @Test
    fun `selection wins over every other state`() {
        assertEquals(BubbleTone.Selected, BubbleTone.of(incoming = false, failed = true, selected = true))
        assertEquals(BubbleTone.Selected, BubbleTone.of(incoming = true, selected = true))
    }
}

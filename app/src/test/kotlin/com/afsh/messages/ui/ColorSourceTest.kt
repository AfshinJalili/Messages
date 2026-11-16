package com.afsh.messages.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the GM3 primary / onPrimary pair used as the high-emphasis accent. */
class ColorSourceTest {
    @Test
    fun `accent roles match GM3 primary and onPrimary`() {
        val accent = ColorRole(Color(0xFF0B57D0), Color(0xFFFFFFFF))
        assertEquals(Color(0xFF0B57D0), accent.container)
        assertEquals(Color(0xFFFFFFFF), accent.content)
    }
}

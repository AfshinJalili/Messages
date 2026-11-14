package com.afsh.messages.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsTest {
    private val pine = Color(0xFF1F4D3A)
    private val paper = Color(0xFFF6F3EA)
    private val night = Color(0xFF111412)
    private val lime = Color(0xFFD7F36B)

    @Test
    fun `a dark header gets light status icons`() {
        assertEquals(false, systemBarAppearance(header = pine, bottom = paper).lightStatusBar)
    }

    @Test
    fun `a light header gets dark status icons`() {
        assertEquals(true, systemBarAppearance(header = lime, bottom = paper).lightStatusBar)
        assertEquals(true, systemBarAppearance(header = Color.White, bottom = night).lightStatusBar)
    }

    @Test
    fun `navigation icons follow the bottom surface, so the light and dark themes differ`() {
        assertEquals(true, systemBarAppearance(header = pine, bottom = paper).lightNavigationBar)
        assertEquals(false, systemBarAppearance(header = pine, bottom = night).lightNavigationBar)
    }

    @Test
    fun `mid tones pick whichever icon color reads better`() {
        // Pure grey 0x77 is closer in contrast to black text; 0x66 still favors white icons.
        assertEquals(true, systemBarAppearance(header = Color(0xFF777777), bottom = paper).lightStatusBar)
        assertEquals(false, systemBarAppearance(header = Color(0xFF666666), bottom = paper).lightStatusBar)
    }
}

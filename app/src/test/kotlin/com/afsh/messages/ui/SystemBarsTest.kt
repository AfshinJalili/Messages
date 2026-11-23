package com.afsh.messages.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsTest {
    // GM3 light/dark surfaces and primary (same hex as colors.xml).
    private val primary = Color(0xFF0B57D0)
    private val surface = Color(0xFFFFFFFF)
    private val surfaceDark = Color(0xFF131314)
    private val primaryContainer = Color(0xFFD3E3FD)

    @Test
    fun `a dark header gets light status icons`() {
        assertEquals(false, systemBarAppearance(header = primary, bottom = surface).lightStatusBar)
    }

    @Test
    fun `a light header gets dark status icons`() {
        assertEquals(true, systemBarAppearance(header = primaryContainer, bottom = surface).lightStatusBar)
        assertEquals(true, systemBarAppearance(header = Color.White, bottom = surfaceDark).lightStatusBar)
    }

    @Test
    fun `navigation icons follow the bottom surface, so the light and dark themes differ`() {
        assertEquals(true, systemBarAppearance(header = primary, bottom = surface).lightNavigationBar)
        assertEquals(false, systemBarAppearance(header = primary, bottom = surfaceDark).lightNavigationBar)
    }

    @Test
    fun `mid tones pick whichever icon color reads better`() {
        // Pure grey 0x77 is closer in contrast to black text; 0x66 still favors white icons.
        assertEquals(true, systemBarAppearance(header = Color(0xFF777777), bottom = surface).lightStatusBar)
        assertEquals(false, systemBarAppearance(header = Color(0xFF666666), bottom = surface).lightStatusBar)
    }
}

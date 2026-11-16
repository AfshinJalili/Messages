package com.afsh.messages.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSourceTest {
    private val primary = ColorRole(Color(0xFF0B57D0), Color(0xFFFFFFFF))

    @Test
    fun `below Android 12 the static scheme is used even with dynamic color on`() {
        assertEquals(ColorSource.Static, colorSource(dynamicColor = true, sdkInt = 30))
        assertEquals(ColorSource.Static, colorSource(dynamicColor = true, sdkInt = 26))
    }

    @Test
    fun `from Android 12 dynamic color is used when it is on`() {
        assertEquals(ColorSource.Dynamic, colorSource(dynamicColor = true, sdkInt = 31))
        assertEquals(ColorSource.Dynamic, colorSource(dynamicColor = true, sdkInt = 35))
    }

    @Test
    fun `with dynamic color off every API level gets the static scheme`() {
        for (sdk in listOf(26, 30, 31, 35)) {
            assertEquals(ColorSource.Static, colorSource(dynamicColor = false, sdkInt = sdk))
        }
    }

    @Test
    fun `dynamic color is off so the static Google palette always applies`() {
        assertEquals(false, DynamicColor)
        assertEquals(ColorSource.Static, colorSource())
    }

    @Test
    fun `the static accent is the primary role`() {
        val scheme = lightColorScheme()
        assertEquals(primary, accentRole(ColorSource.Static, scheme, primary))
    }

    @Test
    fun `the dynamic accent is the scheme's primary pair`() {
        for (scheme in listOf(
            lightColorScheme(primary = Color.Red, onPrimary = Color.Blue),
            darkColorScheme(primary = Color.Green, onPrimary = Color.Black),
        )) {
            assertEquals(
                ColorRole(scheme.primary, scheme.onPrimary),
                accentRole(ColorSource.Dynamic, scheme, primary),
            )
        }
    }
}

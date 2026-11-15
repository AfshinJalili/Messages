package com.afsh.messages.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSourceTest {
    private val lime = ColorRole(Color(0xFFD7F36B), Color(0xFF16201B))

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
    fun `dynamic color is off until the default look is approved`() {
        assertEquals(false, DynamicColor)
    }

    @Test
    fun `the static accent is the lime role`() {
        val scheme = lightColorScheme()
        assertEquals(lime, accentRole(ColorSource.Static, scheme, lime))
    }

    @Test
    fun `the dynamic accent is the scheme's tertiary container`() {
        for (scheme in listOf(lightColorScheme(tertiaryContainer = Color.Red, onTertiaryContainer = Color.Blue),
            darkColorScheme(tertiaryContainer = Color.Green, onTertiaryContainer = Color.Black))) {
            assertEquals(
                ColorRole(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                accentRole(ColorSource.Dynamic, scheme, lime),
            )
        }
    }
}

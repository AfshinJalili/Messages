package com.afsh.messages

import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.AfshSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingChecks {
    @Test
    fun scaleReproducesTheValuesScreensUseToday() {
        val s = AfshSpacing()
        assertEquals(
            listOf(2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp),
            listOf(s.xxs, s.xs, s.sm, s.md, s.lg, s.xl, s.xxl, s.xxxl),
        )
    }
}

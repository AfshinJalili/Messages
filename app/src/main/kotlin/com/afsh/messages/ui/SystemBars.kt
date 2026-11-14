package com.afsh.messages.ui

import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/** Whether the status and navigation bar icons are drawn dark, for a light bar behind them. */
data class SystemBarAppearance(val lightStatusBar: Boolean, val lightNavigationBar: Boolean)

/**
 * The status bar sits on [header] and the navigation bar on [bottom]; each gets the icon color that
 * reads better on it, so recoloring the header never needs a status bar fix.
 */
fun systemBarAppearance(header: Color, bottom: Color) =
    SystemBarAppearance(lightStatusBar = header.wantsDarkIcons(), lightNavigationBar = bottom.wantsDarkIcons())

// Above this relative luminance black icons have more contrast than white ones (WCAG contrast ratio).
private val DARK_ICON_LUMINANCE = sqrt(1.05f * 0.05f) - 0.05f

private fun Color.wantsDarkIcons() = luminance() > DARK_ICON_LUMINANCE

/**
 * Sets [window]'s bar icons from the current theme: the status bar from the header role, the
 * navigation bar from the surface. Call it once inside an activity's [AfshTheme]. It applies again
 * on every resume, after the Commons base activity has set the bars from the background color.
 */
@Composable
fun AfshSystemBars(window: Window) {
    val appearance = systemBarAppearance(Afsh.colors.header.container, MaterialTheme.colorScheme.surface)
    LifecycleResumeEffect(window, appearance) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = appearance.lightStatusBar
            isAppearanceLightNavigationBars = appearance.lightNavigationBar
        }
        onPauseOrDispose {}
    }
}

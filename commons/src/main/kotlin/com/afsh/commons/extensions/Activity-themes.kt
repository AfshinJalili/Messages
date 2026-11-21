package com.afsh.commons.extensions

import android.app.Activity
import com.afsh.commons.AfshApp

/**
 * XML theme for Commons screens. AFSH Messages always supplies [AfshApp.themeId]
 * (AppTheme.Afsh.Light / Dark from the GM3 token file).
 */
fun Activity.getThemeId(color: Int = baseConfig.primaryColor, showTransparentTop: Boolean = false): Int {
    val app = application as? AfshApp
        ?: error("Application must be AfshApp")
    return app.themeId(this)
        ?: error("AfshApp.themeId must return the app XML theme")
}

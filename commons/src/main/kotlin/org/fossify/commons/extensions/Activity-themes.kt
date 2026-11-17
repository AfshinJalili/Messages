package org.fossify.commons.extensions

import android.app.Activity
import org.fossify.commons.FossifyApp

/**
 * XML theme for Commons screens. AFSH Messages always supplies [FossifyApp.themeId]
 * (AppTheme.Afsh.Light / Dark from the GM3 token file). There is no Fossify palette fallback.
 */
fun Activity.getThemeId(color: Int = baseConfig.primaryColor, showTransparentTop: Boolean = false): Int {
    val app = application as? FossifyApp
        ?: error("Application must be FossifyApp")
    return app.themeId(this)
        ?: error("FossifyApp.themeId must return the app XML theme")
}

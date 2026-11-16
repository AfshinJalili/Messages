package com.afsh.messages.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.isSystemInDarkMode
import org.fossify.commons.extensions.syncGlobalConfig
import org.fossify.commons.R as CommonsR

/**
 * Splash launcher that syncs theme config and launches MainActivity. Uses AndroidX SplashScreen API.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Sync global config (same as BaseSplashActivity)
        syncGlobalConfig {
            baseConfig.apply {
                if (isSystemThemeEnabled) {
                    val isUsingSystemDarkTheme = isSystemInDarkMode()
                    textColor = resources.getColor(
                        if (isUsingSystemDarkTheme) CommonsR.color.theme_dark_text_color
                        else CommonsR.color.theme_light_text_color,
                        theme
                    )
                    backgroundColor = resources.getColor(
                        if (isUsingSystemDarkTheme) CommonsR.color.theme_dark_background_color
                        else CommonsR.color.theme_light_background_color,
                        theme
                    )
                }
            }

            // Launch main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

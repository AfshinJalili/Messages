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
                        if (isUsingSystemDarkTheme) CommonsR.color.m3_on_surface_dark
                        else CommonsR.color.m3_on_surface,
                        theme
                    )
                    backgroundColor = resources.getColor(
                        if (isUsingSystemDarkTheme) CommonsR.color.m3_surface_dark
                        else CommonsR.color.m3_surface,
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

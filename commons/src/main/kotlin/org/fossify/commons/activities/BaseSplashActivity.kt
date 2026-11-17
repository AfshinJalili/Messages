package org.fossify.commons.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.fossify.commons.R
import org.fossify.commons.extensions.*

abstract class BaseSplashActivity : AppCompatActivity() {
    abstract fun initActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        syncGlobalConfig {
            baseConfig.apply {
                if (isAutoTheme()) {
                    val isUsingSystemDarkTheme = isSystemInDarkMode()
                    textColor = resources.getColor(if (isUsingSystemDarkTheme) R.color.m3_on_surface_dark else R.color.m3_on_surface)
                    backgroundColor =
                        resources.getColor(if (isUsingSystemDarkTheme) R.color.m3_surface_dark else R.color.m3_surface)
                }
            }

            initActivity()
        }
    }
}

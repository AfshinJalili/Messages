package org.fossify.messages

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.helpers.Config
import org.fossify.messages.helpers.cobaltTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignChecks {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withIsolatedPreferences(block: (Context, Config) -> Unit) {
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String?, mode: Int) =
                target.getSharedPreferences("design-checks", mode)
        }
        val prefs = context.getSharedPreferences("design-checks", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            block(context, Config(context))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun oldDefaultMigratesOnceAndKeepsLaterChoices() = withIsolatedPreferences { context, config ->
        config.primaryColor = ContextCompat.getColor(context, org.fossify.commons.R.color.md_green_900)
        config.accentColor = config.primaryColor
        config.isSystemThemeEnabled = false
        config.backgroundColor = ContextCompat.getColor(context, R.color.surface_light)
        config.applyCobaltDefaults()
        check(context.getProperPrimaryColor() == ContextCompat.getColor(context, R.color.brand_cobalt))
        check(context.cobaltTheme() == R.style.AppTheme_Cobalt_Light)
        val custom = ContextCompat.getColor(context, org.fossify.commons.R.color.md_orange_700)
        config.primaryColor = custom
        config.accentColor = custom
        config.applyCobaltDefaults()
        check(context.getProperPrimaryColor() == custom)
        check(context.cobaltTheme() == null)
    }

    @Test
    fun explicitCustomColorAndSystemThemeArePreserved() = withIsolatedPreferences { context, config ->
        val custom = ContextCompat.getColor(context, org.fossify.commons.R.color.md_purple_700)
        config.primaryColor = custom
        config.accentColor = custom
        config.isSystemThemeEnabled = true
        config.applyCobaltDefaults()
        check(config.primaryColor == custom && config.accentColor == custom)
        check(config.isSystemThemeEnabled)
        check(context.cobaltTheme() == null)
    }

    @Test
    fun bothThemesUseReadableCobaltRoles() {
        for ((style, accent) in listOf(
            R.style.AppTheme_Cobalt_Light to R.color.brand_cobalt,
            R.style.AppTheme_Cobalt_Dark to R.color.brand_cobalt_dark
        )) {
            val context = ContextThemeWrapper(target, style)
            fun attr(id: Int): Int = TypedValue().also { check(context.theme.resolveAttribute(id, it, true)) }.data
            val primary = attr(androidx.appcompat.R.attr.colorPrimary)
            val surface = attr(com.google.android.material.R.attr.colorSurface)
            val onPrimary = attr(com.google.android.material.R.attr.colorOnPrimary)
            check(primary == ContextCompat.getColor(context, accent))
            check(ColorUtils.calculateContrast(primary, surface) >= 4.5)
            check(ColorUtils.calculateContrast(onPrimary, primary) >= 4.5)
        }
    }

    @Test
    fun activityWidgetsMatchRuntimeAccent() {
        ActivityScenario.launch<MainActivity>(Intent(target, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.cobaltTheme() != null) { "Installed app has not adopted cobalt" }
                val primary = TypedValue().also {
                    activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true)
                }.data
                check(primary == activity.getProperPrimaryColor()) { "XML widgets and runtime colors disagree" }
            }
        }
    }
}

package com.afsh.messages

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
import androidx.compose.ui.graphics.toArgb
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getThemeId
import com.afsh.messages.activities.MainActivity
import com.afsh.messages.helpers.Config
import com.afsh.messages.helpers.afshXmlTheme
import com.afsh.messages.helpers.primaryRoleColor
import com.afsh.messages.ui.afshColorScheme
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
    fun anOlderStoredAccentMovesToThePrimaryRole() = withIsolatedPreferences { context, config ->
        val older = ContextCompat.getColor(context, org.fossify.commons.R.color.md_indigo_700)
        config.primaryColor = older
        config.accentColor = older
        config.isSystemThemeEnabled = false
        config.backgroundColor = ContextCompat.getColor(context, R.color.surface_light)
        config.applyThemeDefaults()
        val role = context.afshColorScheme(dark = false).primary.toArgb()
        check(context.getProperPrimaryColor() == role) { "Commons accent is not the light primary role" }
        check(config.accentColor == role)
        check(context.afshXmlTheme() == R.style.AppTheme_Afsh_Light)
    }

    @Test
    fun switchingToDarkMovesTheAccentToTheDarkRole() = withIsolatedPreferences { context, config ->
        config.isSystemThemeEnabled = false
        config.backgroundColor = ContextCompat.getColor(context, R.color.surface_light)
        config.applyThemeDefaults()
        config.backgroundColor = ContextCompat.getColor(context, R.color.surface_dark)
        config.syncAccentWithTheme()
        val role = context.afshColorScheme(dark = true).primary.toArgb()
        check(context.getProperPrimaryColor() == role) { "Commons accent is not the dark primary role" }
        check(context.primaryRoleColor() == role)
        check(context.afshXmlTheme() == R.style.AppTheme_Afsh_Dark)
    }

    @Test
    fun xmlThemesMirrorTheComposeRoles() {
        for ((style, dark) in listOf(R.style.AppTheme_Afsh_Light to false, R.style.AppTheme_Afsh_Dark to true)) {
            val context = ContextThemeWrapper(target, style)
            val scheme = context.afshColorScheme(dark)
            fun attr(id: Int): Int = TypedValue().also { check(context.theme.resolveAttribute(id, it, true)) }.data
            for ((attrId, role) in listOf(
                androidx.appcompat.R.attr.colorPrimary to scheme.primary,
                com.google.android.material.R.attr.colorOnPrimary to scheme.onPrimary,
                com.google.android.material.R.attr.colorPrimaryContainer to scheme.primaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer to scheme.onPrimaryContainer,
                com.google.android.material.R.attr.colorSecondaryContainer to scheme.secondaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer to scheme.onSecondaryContainer,
                com.google.android.material.R.attr.colorSurface to scheme.surface,
                com.google.android.material.R.attr.colorOnSurface to scheme.onSurface,
            )) {
                check(attr(attrId) == role.toArgb()) {
                    "${context.resources.getResourceEntryName(attrId)} in ${if (dark) "dark" else "light"} is not its Compose role"
                }
            }
            val primary = scheme.primary.toArgb()
            check(ColorUtils.calculateContrast(primary, scheme.surface.toArgb()) >= 4.5)
            check(ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), primary) >= 4.5)
        }
    }

    @Test
    fun appAndCommonsScreensUseTheThemeRoles() {
        ActivityScenario.launch<MainActivity>(Intent(target, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val primary = TypedValue().also {
                    activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true)
                }.data
                check(primary == activity.primaryRoleColor()) { "XML widgets are not on the primary role" }
                check(activity.getProperPrimaryColor() == primary) { "Commons accent and XML widgets disagree" }
                // App lock and every other Commons screen pick their theme through getThemeId.
                check(activity.getThemeId(showTransparentTop = true) == activity.afshXmlTheme()) { "Commons screens use their own palette" }
            }
        }
    }
}

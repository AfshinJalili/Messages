package org.fossify.messages

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.fossify.messages.ui.OpenLineTheme
import java.util.Locale

/** The variants every redesigned screen is captured in: light, dark, and Persian right to left. */
enum class DesignVariant(val suffix: String, val dark: Boolean, val locale: Locale?) {
    LIGHT("", dark = false, locale = null),
    DARK("-dark", dark = true, locale = null),
    PERSIAN("-fa", dark = false, locale = Locale.forLanguageTag("fa-IR")),
}

/**
 * Renders [content] in the Open Line theme for [variant]. Persian swaps the resources and the
 * layout direction, so strings, digits and mirroring match what the app shows in fa-IR.
 */
@Composable
fun DesignVariantContent(variant: DesignVariant, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val context = remember(variant) {
        variant.locale?.let { locale ->
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
        } ?: base
    }
    val direction = if (variant.locale != null) LayoutDirection.Rtl else LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalLayoutDirection provides direction,
    ) {
        OpenLineTheme(dark = variant.dark, content = content)
    }
}

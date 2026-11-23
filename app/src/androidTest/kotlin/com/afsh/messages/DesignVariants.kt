package com.afsh.messages

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.afsh.messages.ui.AfshTheme
import java.util.Locale

/** The variants every redesigned screen is captured in: light, dark, and Persian right to left. */
enum class DesignVariant(val suffix: String, val dark: Boolean, val locale: Locale?) {
    LIGHT("", dark = false, locale = null),
    DARK("-dark", dark = true, locale = null),
    PERSIAN("-fa", dark = false, locale = Locale.forLanguageTag("fa-IR")),
}

/**
 * Renders [content] in the AFSH theme for [variant]. Persian swaps the resources and the
 * layout direction, so strings, digits and mirroring match what the app shows in fa-IR.
 */
@Composable
fun DesignVariantContent(variant: DesignVariant, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val context = remember(variant, baseConfiguration) {
        variant.locale?.let { locale ->
            base.createConfigurationContext(Configuration(baseConfiguration).apply { setLocale(locale) })
        } ?: base
    }
    val direction = if (variant.locale != null) LayoutDirection.Rtl else LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalLayoutDirection provides direction,
    ) {
        AfshTheme(dark = variant.dark, content = content)
    }
}

/**
 * Captures pages in design variants as QA evidence. [show] sets the content once; [capture] and
 * [captureEach] switch the page and variant underneath it, and leave the screen in light English
 * so the next step can find its labels.
 */
class VariantCapture(private val compose: ComposeContentTestRule) {
    var variant by mutableStateOf(DesignVariant.LIGHT)
    var page by mutableIntStateOf(0)

    fun show(content: @Composable (page: Int) -> Unit) = compose.setContent { DesignVariantContent(variant) { content(page) } }

    /** The current page as [name] in each of [variants]. */
    fun capture(name: String, variants: List<DesignVariant> = DesignVariant.entries) {
        variants.forEach {
            variant = it
            compose.waitForIdle()
            Screenshots.capture(name + it.suffix)
        }
        variant = DesignVariant.LIGHT
        compose.waitForIdle()
    }

    /** Page i as names[i], in every variant. */
    fun captureEach(names: List<String>) = names.forEachIndexed { index, name ->
        page = index
        capture(name)
    }
}

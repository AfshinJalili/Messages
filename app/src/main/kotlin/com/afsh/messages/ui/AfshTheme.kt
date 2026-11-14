package com.afsh.messages.ui

import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.ColorUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getTextSize
import com.afsh.messages.R

private const val DARK_LUMINANCE = 0.5

/** Light or dark follows the app's background setting, not the system night mode. */
fun Context.afshDark() = ColorUtils.calculateLuminance(getProperBackgroundColor()) < DARK_LUMINANCE

/** The app's own font-size setting, relative to its default. */
fun Context.afshTextScale() = getTextSize() / resources.getDimension(org.fossify.commons.R.dimen.normal_text_size)

/** A surface color and the color of what sits on it. */
@Immutable
data class ColorRole(val container: Color, val content: Color) {
    companion object {
        val Unspecified = ColorRole(Color.Unspecified, Color.Unspecified)
    }
}

/**
 * Roles the AFSH design uses that Material 3 has no slot for. Each job that used to share
 * `primaryContainer` has its own [ColorRole], so recoloring one never recolors the others.
 */
@Immutable
data class AfshColors(
    val accent: Color,
    val onAccent: Color,
    val onBandVariant: Color,
    val avatars: List<Color>,
    val onAvatar: Color,
    /** Secondary text on lilac panels (`on-secondary-container-variant`). */
    val onSecondaryContainerVariant: Color = Color.Unspecified,
    /** Actions on inverse surfaces such as the snackbar (`inverse-accent`). */
    val inverseAccent: Color = Color.Unspecified,
    /** Behind a held message or a focused task (`scrim`). */
    val scrim: Color = Color.Unspecified,
    /** Outline of the focused control (`focus-ring`). */
    val focusRing: Color = Color.Unspecified,
    /** Page, inbox and conversation headers. */
    val header: ColorRole = ColorRole.Unspecified,
    /** Messages the user sent, in the conversation and in message details. */
    val sentBubble: ColorRole = ColorRole.Unspecified,
    /** Secondary buttons and the jump-to-latest button. */
    val secondaryButton: ColorRole = ColorRole.Unspecified,
    /** The panel behind a non-destructive swipe action such as Archive. */
    val swipeAction: ColorRole = ColorRole.Unspecified,
    /** The selected filter chip in the inbox and in search. */
    val selected: ColorRole = ColorRole.Unspecified,
    /** The unread count and attachment type icons. */
    val badge: ColorRole = ColorRole.Unspecified,
)

val LocalAfshColors = staticCompositionLocalOf {
    AfshColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, emptyList(), Color.Unspecified)
}

/** The shared spacing scale for gaps and padding; see Spacing in DESIGN.md for when to use each step. */
@Immutable
data class AfshSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
)

val LocalAfshSpacing = staticCompositionLocalOf { AfshSpacing() }

object Afsh {
    val colors: AfshColors
        @Composable get() = LocalAfshColors.current
    val spacing: AfshSpacing
        @Composable get() = LocalAfshSpacing.current
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val display = FontFamily((400..800 step 100).map { variableFont(R.font.funnel_sans, it) })
private val body = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)
private val persian = FontFamily((400..800 step 100).map { variableFont(R.font.vazirmatn, it) })

// Include joining controls and inherited marks so a Persian word remains one shaping run.
private val persianRun = Regex("[\\p{IsArabic}\\p{InArabic}][\\p{IsArabic}\\p{InArabic}\\p{M}\u200C\u200D]*")

/** Content fonts follow script, independently of the language used by interface labels. */
internal fun AnnotatedString.withContentFonts(): AnnotatedString = buildAnnotatedString {
    append(this@withContentFonts)
    addStyle(SpanStyle(fontFamily = body), 0, length)
    persianRun.findAll(this@withContentFonts.text).forEach {
        addStyle(SpanStyle(fontFamily = persian), it.range.first, it.range.last + 1)
    }
}

internal fun TextStyle.forContent(text: CharSequence): TextStyle =
    copy(fontFamily = body, lineHeight = if (persianRun.containsMatchIn(text)) 1.7.em else 1.45.em)

/**
 * What people wrote (a message, a name) reads in its own direction, not the interface's: English
 * text in the Persian interface keeps its full stop at the end.
 */
internal fun TextStyle.forUserText(text: CharSequence): TextStyle = forContent(text).copy(textDirection = TextDirection.ContentOrLtr)

/** A phone number or address isolated left to right, so "555 0142" never reads "0142 555" in Persian. */
fun ltr(text: String): String = if (text.isEmpty()) text else "\u2066$text\u2069"

private fun typography(rtl: Boolean): Typography {
    val displayFamily = if (rtl) persian else display
    val bodyFamily = if (rtl) persian else body
    val leading = if (rtl) 1.7.em else 1.45.em
    fun style(family: FontFamily, size: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, lineHeight = leading)
    return Typography(
        headlineMedium = style(displayFamily, 31, FontWeight.ExtraBold).copy(lineHeight = 1.25.em),
        // Dialog titles (text-title-lg).
        headlineSmall = style(displayFamily, 25, FontWeight.Normal).copy(lineHeight = 1.3.em),
        titleLarge = style(bodyFamily, 20, FontWeight.Bold),
        titleMedium = style(bodyFamily, 18, FontWeight.Bold),
        bodyLarge = style(bodyFamily, 18, FontWeight.Normal),
        bodyMedium = style(bodyFamily, 16, FontWeight.Normal),
        bodySmall = style(bodyFamily, 14, FontWeight.Normal),
        labelLarge = style(bodyFamily, 16, FontWeight.ExtraBold),
        labelMedium = style(bodyFamily, 14, FontWeight.Bold),
        labelSmall = style(bodyFamily, 14, FontWeight.Normal),
    )
}

/**
 * The one palette: Compose screens read it through [AfshTheme], and the XML theme, Commons'
 * stored accent and notifications read the same roles through [com.afsh.messages.helpers.primaryRoleColor].
 */
fun Context.afshColorScheme(dark: Boolean): ColorScheme {
    fun color(id: Int) = Color(getColor(id))
    val paper = color(R.color.afsh_paper)
    val ink = color(R.color.afsh_ink)
    val pine = color(R.color.afsh_pine)
    val white = color(R.color.design_white)
    val lilac = color(R.color.afsh_lilac)
    val lime = color(R.color.afsh_lime)
    return if (dark) {
        darkColorScheme(
            primary = color(R.color.afsh_primary_dark),
            onPrimary = ink,
            primaryContainer = color(R.color.afsh_pine_2),
            onPrimaryContainer = white,
            secondaryContainer = color(R.color.afsh_lilac_dark),
            onSecondaryContainer = lilac,
            background = color(R.color.afsh_night),
            surface = color(R.color.afsh_night),
            surfaceContainer = color(R.color.afsh_night_surface),
            surfaceContainerHigh = color(R.color.afsh_surface_high_dark),
            onSurface = paper,
            onSurfaceVariant = color(R.color.afsh_muted_dark),
            outlineVariant = color(R.color.afsh_line_dark),
            error = color(R.color.afsh_coral),
            onError = ink,
            errorContainer = color(R.color.afsh_error_container_dark),
            onErrorContainer = color(R.color.afsh_error_container),
            inverseSurface = paper,
            inverseOnSurface = ink,
            inversePrimary = pine,
        )
    } else {
        lightColorScheme(
            primary = pine,
            onPrimary = white,
            primaryContainer = pine,
            onPrimaryContainer = white,
            secondaryContainer = lilac,
            onSecondaryContainer = ink,
            background = paper,
            surface = paper,
            surfaceContainer = white,
            surfaceContainerHigh = color(R.color.afsh_surface_high),
            onSurface = ink,
            onSurfaceVariant = color(R.color.afsh_muted),
            outlineVariant = color(R.color.afsh_line),
            error = color(R.color.afsh_error),
            onError = white,
            errorContainer = color(R.color.afsh_error_container),
            onErrorContainer = ink,
            inverseSurface = ink,
            inverseOnSurface = white,
            inversePrimary = lime,
        )
    }
}

/**
 * [textScale] carries the app's own font-size setting on top of the system font scale, so Compose
 * screens honour it the same way the XML screens do.
 */
@Composable
fun AfshTheme(dark: Boolean, textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val context = LocalContext.current
    val scheme = remember(context, dark) { context.afshColorScheme(dark) }
    // Every role resolves to today's pine container; give a role its own value to recolor only that job.
    val pine = ColorRole(scheme.primaryContainer, scheme.onPrimaryContainer)
    val extras = AfshColors(
        accent = colorResource(R.color.afsh_lime),
        onAccent = colorResource(R.color.afsh_ink),
        onBandVariant = colorResource(R.color.afsh_band_variant),
        avatars = listOf(colorResource(R.color.afsh_lilac), colorResource(R.color.afsh_lime)),
        onAvatar = colorResource(R.color.afsh_pine),
        onSecondaryContainerVariant = colorResource(
            if (dark) R.color.afsh_on_lilac_variant_dark else R.color.afsh_on_lilac_variant,
        ),
        inverseAccent = colorResource(if (dark) R.color.afsh_pine else R.color.afsh_lime),
        scrim = colorResource(if (dark) R.color.afsh_scrim_dark else R.color.afsh_scrim),
        focusRing = colorResource(if (dark) R.color.afsh_lime else R.color.afsh_pine),
        header = pine,
        sentBubble = pine,
        secondaryButton = pine,
        swipeAction = pine,
        selected = pine,
        badge = pine,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typography(rtl),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
    ) {
        CompositionLocalProvider(
            LocalAfshColors provides extras,
            LocalAfshSpacing provides AfshSpacing(),
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            content = content,
        )
    }
}

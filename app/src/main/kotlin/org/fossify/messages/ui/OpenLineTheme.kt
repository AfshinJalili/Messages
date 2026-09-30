package org.fossify.messages.ui

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.messages.R

private const val DARK_LUMINANCE = 0.5

/** Light or dark follows the app's background setting, not the system night mode. */
fun Context.openLineDark() = ColorUtils.calculateLuminance(getProperBackgroundColor()) < DARK_LUMINANCE

/** The app's own font-size setting, relative to its default. */
fun Context.openLineTextScale() = getTextSize() / resources.getDimension(org.fossify.commons.R.dimen.normal_text_size)

/** Roles the Open Line design uses that Material 3 has no slot for. */
@Immutable
data class OpenLineColors(
    val accent: Color,
    val onAccent: Color,
    val onBandVariant: Color,
    val avatars: List<Color>,
    val onAvatar: Color,
    val onSecondaryVariant: Color = Color.Unspecified,
    val inverseAccent: Color = Color.Unspecified,
    val statePressed: Color = Color.Unspecified,
    val focusRing: Color = Color.Unspecified,
    val skeleton: Color = Color.Unspecified,
    val skeletonHigh: Color = Color.Unspecified,
    val lineDraft: Color = Color.Unspecified,
    val lineSending: Color = Color.Unspecified,
    val lineBlocked: Color = Color.Unspecified,
)

val LocalOpenLineColors = staticCompositionLocalOf {
    OpenLineColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, emptyList(), Color.Unspecified)
}

object OpenLine {
    val colors: OpenLineColors
        @Composable get() = LocalOpenLineColors.current

    val brand: TextStyle
        @Composable get() = TextStyle(
            fontFamily = if (LocalLayoutDirection.current == LayoutDirection.Rtl) persian else display,
            fontSize = 76.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 1.em,
        )
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

private fun typography(rtl: Boolean): Typography {
    val displayFamily = if (rtl) persian else display
    val bodyFamily = if (rtl) persian else body
    val leading = if (rtl) 1.7.em else 1.45.em
    fun style(family: FontFamily, size: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, lineHeight = leading)
    return Typography(
        displaySmall = style(displayFamily, 45, FontWeight.ExtraBold).copy(lineHeight = if (rtl) leading else 1.1.em),
        headlineSmall = style(displayFamily, 25, FontWeight.ExtraBold).copy(lineHeight = if (rtl) leading else 1.3.em),
        headlineMedium = style(displayFamily, 31, FontWeight.ExtraBold).copy(lineHeight = if (rtl) leading else 1.25.em),
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

@Composable
private fun colorScheme(dark: Boolean): ColorScheme {
    val paper = colorResource(R.color.open_line_paper)
    val ink = colorResource(R.color.open_line_ink)
    val pine = colorResource(R.color.open_line_pine)
    val white = colorResource(R.color.design_white)
    val lilac = colorResource(R.color.open_line_lilac)
    val lime = colorResource(R.color.open_line_lime)
    return if (dark) {
        darkColorScheme(
            primary = colorResource(R.color.open_line_primary_dark),
            onPrimary = ink,
            primaryContainer = colorResource(R.color.open_line_pine_2),
            onPrimaryContainer = white,
            secondaryContainer = colorResource(R.color.open_line_lilac_dark),
            onSecondaryContainer = lilac,
            background = colorResource(R.color.open_line_night),
            surface = colorResource(R.color.open_line_night),
            surfaceContainer = colorResource(R.color.open_line_night_surface),
            surfaceContainerHigh = colorResource(R.color.open_line_surface_high_dark),
            onSurface = paper,
            onSurfaceVariant = colorResource(R.color.open_line_muted_dark),
            outlineVariant = colorResource(R.color.open_line_line_dark),
            error = colorResource(R.color.open_line_coral),
            onError = ink,
            errorContainer = colorResource(R.color.open_line_error_container_dark),
            onErrorContainer = colorResource(R.color.open_line_error_container),
            inverseSurface = paper,
            inverseOnSurface = ink,
            inversePrimary = pine,
            scrim = colorResource(R.color.open_line_scrim_dark),
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
            surfaceContainerHigh = colorResource(R.color.open_line_surface_high),
            onSurface = ink,
            onSurfaceVariant = colorResource(R.color.open_line_muted),
            outlineVariant = colorResource(R.color.open_line_line),
            error = colorResource(R.color.open_line_error),
            onError = white,
            errorContainer = colorResource(R.color.open_line_error_container),
            onErrorContainer = ink,
            inverseSurface = ink,
            inverseOnSurface = white,
            inversePrimary = lime,
            scrim = colorResource(R.color.open_line_scrim),
        )
    }
}

/**
 * [textScale] carries the app's own font-size setting on top of the system font scale, so Compose
 * screens honour it the same way the XML screens do.
 */
@Composable
fun OpenLineTheme(dark: Boolean, textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val extras = OpenLineColors(
        accent = colorResource(R.color.open_line_lime),
        onAccent = colorResource(R.color.open_line_ink),
        onBandVariant = colorResource(R.color.open_line_band_variant),
        avatars = listOf(colorResource(R.color.open_line_lilac), colorResource(R.color.open_line_lime)),
        onAvatar = colorResource(R.color.open_line_pine),
        onSecondaryVariant = colorResource(if (dark) R.color.open_line_on_secondary_variant_dark else R.color.open_line_on_secondary_variant),
        inverseAccent = colorResource(if (dark) R.color.open_line_pine else R.color.open_line_lime),
        statePressed = colorResource(if (dark) R.color.open_line_state_pressed_dark else R.color.open_line_state_pressed),
        focusRing = colorResource(if (dark) R.color.open_line_focus_dark else R.color.open_line_focus),
        skeleton = colorResource(R.color.open_line_skeleton),
        skeletonHigh = colorResource(R.color.open_line_skeleton_high),
        lineDraft = colorResource(R.color.open_line_lilac),
        lineSending = colorResource(R.color.open_line_lime),
        lineBlocked = colorResource(R.color.open_line_coral),
    )
    MaterialTheme(
        colorScheme = colorScheme(dark),
        typography = typography(rtl),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
    ) {
        CompositionLocalProvider(
            LocalOpenLineColors provides extras,
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            content = content,
        )
    }
}

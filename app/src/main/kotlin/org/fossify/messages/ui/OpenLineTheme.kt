package org.fossify.messages.ui

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.fossify.messages.R

/** Roles the Open Line design uses that Material 3 has no slot for. */
@Immutable
data class OpenLineColors(
    val accent: Color,
    val onAccent: Color,
    val onBandVariant: Color,
    val avatars: List<Color>,
    val onAvatar: Color,
)

val LocalOpenLineColors = staticCompositionLocalOf {
    OpenLineColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, emptyList(), Color.Unspecified)
}

object OpenLine {
    val colors: OpenLineColors
        @Composable get() = LocalOpenLineColors.current
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

private fun typography(rtl: Boolean): Typography {
    val displayFamily = if (rtl) persian else display
    val bodyFamily = if (rtl) persian else body
    val leading = if (rtl) 1.7.em else 1.45.em
    fun style(family: FontFamily, size: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, lineHeight = leading)
    return Typography(
        headlineMedium = style(displayFamily, 31, FontWeight.ExtraBold).copy(lineHeight = 1.25.em),
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
            surfaceContainerHigh = colorResource(R.color.open_line_night_surface),
            onSurface = paper,
            onSurfaceVariant = colorResource(R.color.open_line_muted_dark),
            outlineVariant = colorResource(R.color.open_line_line_dark),
            error = colorResource(R.color.open_line_coral),
            onError = ink,
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
            surfaceContainerHigh = white,
            onSurface = ink,
            onSurfaceVariant = colorResource(R.color.open_line_muted),
            outlineVariant = colorResource(R.color.open_line_line),
            error = colorResource(R.color.open_line_error),
            onError = white,
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
fun OpenLineTheme(dark: Boolean, textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val extras = OpenLineColors(
        accent = colorResource(R.color.open_line_lime),
        onAccent = colorResource(R.color.open_line_ink),
        onBandVariant = colorResource(R.color.open_line_band_variant),
        avatars = listOf(colorResource(R.color.open_line_lilac), colorResource(R.color.open_line_lime)),
        onAvatar = colorResource(R.color.open_line_pine),
    )
    MaterialTheme(
        colorScheme = colorScheme(dark),
        typography = typography(rtl),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
    ) {
        CompositionLocalProvider(
            LocalOpenLineColors provides extras,
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            content = content,
        )
    }
}

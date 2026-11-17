package com.afsh.messages.ui

import androidx.compose.ui.text.style.TextDirection
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
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
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getTextSize
import com.afsh.messages.R

private const val DARK_LUMINANCE = 0.5

/**
 * Light or dark: follows the system night mode when Appearance → System is on; otherwise the
 * stored light/dark background. Wallpaper / Material You colors are not used.
 */
fun Context.afshDark(): Boolean = if (baseConfig.isSystemThemeEnabled) {
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
} else {
    ColorUtils.calculateLuminance(baseConfig.backgroundColor) < DARK_LUMINANCE
}

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
    /**
     * High-emphasis actions (send, FAB, primary buttons): Material primary / onPrimary, matching
     * Google Messages’ FAB and send emphasis on the primary role.
     */
    val accent: Color,
    val onAccent: Color,
    val onBandVariant: Color,
    val avatars: List<Color>,
    val onAvatar: Color,
    /** Secondary text on secondary-container panels. */
    val onSecondaryContainerVariant: Color = Color.Unspecified,
    /** Actions on inverse surfaces such as the snackbar (`inversePrimary`). */
    val inverseAccent: Color = Color.Unspecified,
    /** Behind a held message or a focused task (`scrim` with dim alpha). */
    val scrim: Color = Color.Unspecified,
    /** Over the conversation and its header while a message is lifted for actions. */
    val liftScrim: Color = Color.Unspecified,
    /** Outline of the focused control (`primary`). */
    val focusRing: Color = Color.Unspecified,
    /** Fully transparent; for “no indicator” / clear surfaces — not a brand color. */
    val transparent: Color = Color.Unspecified,
    /** Opaque black mask stop (`scrim`); for blend masks, not fills. */
    val mask: Color = Color.Unspecified,
    /** Page, inbox and conversation headers: surface / onSurface (Messages chrome is not primary-filled). */
    val header: ColorRole = ColorRole.Unspecified,
    /** Messages the user sent: primaryContainer / onPrimaryContainer. */
    val sentBubble: ColorRole = ColorRole.Unspecified,
    /** Received bubbles: surfaceContainerHighest / onSurface. */
    val receivedBubble: ColorRole = ColorRole.Unspecified,
    /** Secondary buttons and the jump-to-latest button. */
    val secondaryButton: ColorRole = ColorRole.Unspecified,
    /** The panel behind a non-destructive swipe action such as Archive. */
    val swipeAction: ColorRole = ColorRole.Unspecified,
    /** The selected filter chip in the inbox and in search. */
    val selected: ColorRole = ColorRole.Unspecified,
    /** The unread count and attachment type icons (`primary`). */
    val badge: ColorRole = ColorRole.Unspecified,
    /** The theme picker's pictures of each theme: a page ([ColorRole.container]) and its line. */
    val themePreview: ThemePreview = ThemePreview.Unspecified,
)

/** How the light and dark themes look in a small picture of each. */
@Immutable
data class ThemePreview(val light: ColorRole, val dark: ColorRole) {
    companion object {
        val Unspecified = ThemePreview(ColorRole.Unspecified, ColorRole.Unspecified)
    }
}

val LocalAfshColors = staticCompositionLocalOf {
    AfshColors(
        accent = Color.Unspecified,
        onAccent = Color.Unspecified,
        onBandVariant = Color.Unspecified,
        avatars = emptyList(),
        onAvatar = Color.Unspecified,
    )
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
 * Always the static GM3 tokens in `commons/.../res/values/colors.xml` — never wallpaper / Material You.
 */
fun Context.afshColorScheme(dark: Boolean): ColorScheme {
    fun color(id: Int) = Color(getColor(id))
    return if (dark) {
        darkColorScheme(
            primary = color(org.fossify.commons.R.color.m3_primary_dark),
            onPrimary = color(org.fossify.commons.R.color.m3_on_primary_dark),
            primaryContainer = color(org.fossify.commons.R.color.m3_primary_container_dark),
            onPrimaryContainer = color(org.fossify.commons.R.color.m3_on_primary_container_dark),
            secondary = color(org.fossify.commons.R.color.m3_secondary_dark),
            onSecondary = color(org.fossify.commons.R.color.m3_on_secondary_dark),
            secondaryContainer = color(org.fossify.commons.R.color.m3_secondary_container_dark),
            onSecondaryContainer = color(org.fossify.commons.R.color.m3_on_secondary_container_dark),
            tertiary = color(org.fossify.commons.R.color.m3_tertiary_dark),
            onTertiary = color(org.fossify.commons.R.color.m3_on_tertiary_dark),
            tertiaryContainer = color(org.fossify.commons.R.color.m3_tertiary_container_dark),
            onTertiaryContainer = color(org.fossify.commons.R.color.m3_on_tertiary_container_dark),
            background = color(org.fossify.commons.R.color.m3_background_dark),
            onBackground = color(org.fossify.commons.R.color.m3_on_background_dark),
            surface = color(org.fossify.commons.R.color.m3_surface_dark),
            onSurface = color(org.fossify.commons.R.color.m3_on_surface_dark),
            surfaceVariant = color(org.fossify.commons.R.color.m3_surface_variant_dark),
            onSurfaceVariant = color(org.fossify.commons.R.color.m3_on_surface_variant_dark),
            surfaceTint = color(org.fossify.commons.R.color.m3_surface_tint_dark),
            inverseSurface = color(org.fossify.commons.R.color.m3_inverse_surface_dark),
            inverseOnSurface = color(org.fossify.commons.R.color.m3_inverse_on_surface_dark),
            inversePrimary = color(org.fossify.commons.R.color.m3_inverse_primary_dark),
            error = color(org.fossify.commons.R.color.m3_error_dark),
            onError = color(org.fossify.commons.R.color.m3_on_error_dark),
            errorContainer = color(org.fossify.commons.R.color.m3_error_container_dark),
            onErrorContainer = color(org.fossify.commons.R.color.m3_on_error_container_dark),
            outline = color(org.fossify.commons.R.color.m3_outline_dark),
            outlineVariant = color(org.fossify.commons.R.color.m3_outline_variant_dark),
            scrim = color(org.fossify.commons.R.color.m3_scrim),
            surfaceBright = color(org.fossify.commons.R.color.m3_surface_bright_dark),
            surfaceDim = color(org.fossify.commons.R.color.m3_surface_dim_dark),
            surfaceContainer = color(org.fossify.commons.R.color.m3_surface_container_dark),
            surfaceContainerHigh = color(org.fossify.commons.R.color.m3_surface_container_high_dark),
            surfaceContainerHighest = color(org.fossify.commons.R.color.m3_surface_container_highest_dark),
            surfaceContainerLow = color(org.fossify.commons.R.color.m3_surface_container_low_dark),
            surfaceContainerLowest = color(org.fossify.commons.R.color.m3_surface_container_lowest_dark),
        )
    } else {
        lightColorScheme(
            primary = color(org.fossify.commons.R.color.m3_primary),
            onPrimary = color(org.fossify.commons.R.color.m3_on_primary),
            primaryContainer = color(org.fossify.commons.R.color.m3_primary_container),
            onPrimaryContainer = color(org.fossify.commons.R.color.m3_on_primary_container),
            secondary = color(org.fossify.commons.R.color.m3_secondary),
            onSecondary = color(org.fossify.commons.R.color.m3_on_secondary),
            secondaryContainer = color(org.fossify.commons.R.color.m3_secondary_container),
            onSecondaryContainer = color(org.fossify.commons.R.color.m3_on_secondary_container),
            tertiary = color(org.fossify.commons.R.color.m3_tertiary),
            onTertiary = color(org.fossify.commons.R.color.m3_on_tertiary),
            tertiaryContainer = color(org.fossify.commons.R.color.m3_tertiary_container),
            onTertiaryContainer = color(org.fossify.commons.R.color.m3_on_tertiary_container),
            background = color(org.fossify.commons.R.color.m3_background),
            onBackground = color(org.fossify.commons.R.color.m3_on_background),
            surface = color(org.fossify.commons.R.color.m3_surface),
            onSurface = color(org.fossify.commons.R.color.m3_on_surface),
            surfaceVariant = color(org.fossify.commons.R.color.m3_surface_variant),
            onSurfaceVariant = color(org.fossify.commons.R.color.m3_on_surface_variant),
            surfaceTint = color(org.fossify.commons.R.color.m3_surface_tint),
            inverseSurface = color(org.fossify.commons.R.color.m3_inverse_surface),
            inverseOnSurface = color(org.fossify.commons.R.color.m3_inverse_on_surface),
            inversePrimary = color(org.fossify.commons.R.color.m3_inverse_primary),
            error = color(org.fossify.commons.R.color.m3_error),
            onError = color(org.fossify.commons.R.color.m3_on_error),
            errorContainer = color(org.fossify.commons.R.color.m3_error_container),
            onErrorContainer = color(org.fossify.commons.R.color.m3_on_error_container),
            outline = color(org.fossify.commons.R.color.m3_outline),
            outlineVariant = color(org.fossify.commons.R.color.m3_outline_variant),
            scrim = color(org.fossify.commons.R.color.m3_scrim),
            surfaceBright = color(org.fossify.commons.R.color.m3_surface_bright),
            surfaceDim = color(org.fossify.commons.R.color.m3_surface_dim),
            surfaceContainer = color(org.fossify.commons.R.color.m3_surface_container),
            surfaceContainerHigh = color(org.fossify.commons.R.color.m3_surface_container_high),
            surfaceContainerHighest = color(org.fossify.commons.R.color.m3_surface_container_highest),
            surfaceContainerLow = color(org.fossify.commons.R.color.m3_surface_container_low),
            surfaceContainerLowest = color(org.fossify.commons.R.color.m3_surface_container_lowest),
        )
    }
}

/**
 * [textScale] carries the app's own font-size setting on top of the system font scale, so Compose
 * screens honour it the same way the XML screens do.
 */
/**
 * The shape scale. Material slots carry the app's corners; extraSmall and extraLarge are set
 * explicitly so they stay at the values the app has always drawn.
 */
val AfshShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corners that have no Material slot. Each value is the one the app has always drawn. */
object AfshCorners {
    /** Message bubble corners, and the tail corner of a bubble. */
    private val Bubble = 16.dp
    private val BubbleTail = 4.dp

    /** Top of the bottom sheet that holds the attachment and thread actions. */
    val Sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** The SIM badge: square-ish with a cut top-right corner, which tells SIM 1 from SIM 2 without colour. */
    val SimTile: Shape = AbsoluteRoundedCornerShape(topLeft = 2.dp, topRight = 5.dp, bottomRight = 2.dp, bottomLeft = 2.dp)

    /**
     * A message bubble. The tail corner sits at the bottom on the sender's side; a bubble followed
     * by one from the same sender drops the tail.
     */
    fun bubble(incoming: Boolean, groupedBelow: Boolean = false): Shape {
        val corner = Bubble
        val tail = if (groupedBelow) corner else BubbleTail
        return if (incoming) {
            RoundedCornerShape(corner, corner, corner, tail)
        } else {
            RoundedCornerShape(corner, corner, tail, corner)
        }
    }
}

@Composable
fun AfshTheme(dark: Boolean, textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val context = LocalContext.current
    val scheme = remember(context, dark) { context.afshColorScheme(dark) }
    val primaryContainer = ColorRole(scheme.primaryContainer, scheme.onPrimaryContainer)
    val extras = AfshColors(
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        onBandVariant = scheme.onSurfaceVariant,
        avatars = listOf(scheme.secondaryContainer, scheme.tertiaryContainer, scheme.primaryContainer),
        onAvatar = scheme.onSurface,
        onSecondaryContainerVariant = scheme.onSecondaryContainer,
        inverseAccent = scheme.inversePrimary,
        scrim = colorResource(if (dark) org.fossify.commons.R.color.m3_scrim_dim_dark else org.fossify.commons.R.color.m3_scrim_dim),
        liftScrim = colorResource(org.fossify.commons.R.color.m3_lift_scrim),
        focusRing = scheme.primary,
        transparent = colorResource(org.fossify.commons.R.color.m3_transparent),
        mask = scheme.scrim,
        header = ColorRole(scheme.surface, scheme.onSurface),
        sentBubble = primaryContainer,
        receivedBubble = ColorRole(scheme.surfaceContainerHighest, scheme.onSurface),
        secondaryButton = ColorRole(scheme.secondaryContainer, scheme.onSecondaryContainer),
        swipeAction = primaryContainer,
        selected = ColorRole(scheme.secondaryContainer, scheme.onSecondaryContainer),
        badge = ColorRole(scheme.primary, scheme.onPrimary),
        themePreview = ThemePreview(
            light = ColorRole(colorResource(org.fossify.commons.R.color.m3_surface), colorResource(org.fossify.commons.R.color.m3_primary)),
            dark = ColorRole(colorResource(org.fossify.commons.R.color.m3_surface_dark), colorResource(org.fossify.commons.R.color.m3_primary_dark)),
        ),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typography(rtl),
        shapes = AfshShapes,
    ) {
        CompositionLocalProvider(
            LocalAfshColors provides extras,
            LocalAfshSpacing provides AfshSpacing(),
            LocalDensity provides Density(density.density, density.fontScale * textScale),
            content = content,
        )
    }
}

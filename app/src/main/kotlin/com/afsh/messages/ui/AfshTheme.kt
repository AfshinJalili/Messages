package com.afsh.messages.ui

import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.getTextSize
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
fun Context.afshTextScale() = getTextSize() / resources.getDimension(com.afsh.commons.R.dimen.normal_text_size)

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

private fun isArabicLetter(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c)
    return block == Character.UnicodeBlock.ARABIC ||
        block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
        block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
}

private fun isPersianRunContinuation(c: Char): Boolean {
    if (c == '\u200C' || c == '\u200D') return true
    val type = Character.getType(c)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt()
}

/** True when a gap between Arabic runs may share one Vazirmatn span (whitespace / punctuation). */
private fun canMergePersianGap(c: Char): Boolean {
    if (c.isWhitespace() || c == '\u200C' || c == '\u200D') return true
    if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z') return false
    return !isArabicLetter(c)
}

/**
 * Persian font ranges for [text]. Adjacent Arabic words separated only by whitespace or
 * punctuation become one span so text layout does not switch fonts at every space.
 * ASCII letters/digits keep the body font, matching the previous per-run Regex behavior.
 */
internal fun persianFontRanges(text: CharSequence): List<IntRange> {
    val raw = ArrayList<IntRange>()
    var i = 0
    val n = text.length
    while (i < n) {
        if (!isArabicLetter(text[i])) {
            i++
            continue
        }
        val start = i++
        while (i < n && (isArabicLetter(text[i]) || isPersianRunContinuation(text[i]))) i++
        raw += start until i
    }
    if (raw.isEmpty()) return emptyList()
    val merged = ArrayList<IntRange>(raw.size)
    var current = raw[0]
    for (index in 1 until raw.size) {
        val next = raw[index]
        val gapStart = current.last + 1
        val gapEnd = next.first
        var merge = true
        var g = gapStart
        while (g < gapEnd) {
            if (!canMergePersianGap(text[g])) {
                merge = false
                break
            }
            g++
        }
        current = if (merge) current.first..next.last else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

internal fun hasArabicLetter(text: CharSequence): Boolean {
    for (i in text.indices) if (isArabicLetter(text[i])) return true
    return false
}

/** Survives LazyColumn disposal so recycled inbox rows skip script scanning for the same body. */
private val contentFontCache = object : android.util.LruCache<String, AnnotatedString>(512) {}

private fun AnnotatedString.withScriptFonts(baseFamily: FontFamily): AnnotatedString {
    val source = this
    if (source.isEmpty()) return source
    val cacheable = baseFamily == body && source.spanStyles.isEmpty() && source.getStringAnnotations(0, source.length).isEmpty()
    if (cacheable) contentFontCache.get(source.text)?.let { return it }
    val ranges = persianFontRanges(source.text)
    // Latin-only: the base TextStyle already sets the Latin font — no AnnotatedString rebuild.
    if (ranges.isEmpty()) {
        if (cacheable) contentFontCache.put(source.text, source)
        return source
    }
    val styled = buildAnnotatedString {
        append(source)
        val persianStyle = SpanStyle(fontFamily = persian)
        if (ranges.size == 1 && ranges[0].first == 0 && ranges[0].last == source.lastIndex) {
            addStyle(persianStyle, 0, length)
        } else {
            addStyle(SpanStyle(fontFamily = baseFamily), 0, length)
            for (range in ranges) {
                addStyle(persianStyle, range.first, range.last + 1)
            }
        }
    }
    if (cacheable) contentFontCache.put(source.text, styled)
    return styled
}

/** Content fonts follow script, independently of the language used by interface labels. */
internal fun AnnotatedString.withContentFonts(): AnnotatedString = withScriptFonts(body)

/**
 * Returns the cached Persian-spanned [AnnotatedString] when [text] contains Arabic script, or null
 * for Latin-only strings so callers can take Compose's fast `Text(String)` path.
 */
internal fun contentAnnotatedOrNull(text: String): AnnotatedString? {
    if (text.isEmpty()) return null
    val plain = AnnotatedString(text)
    val styled = plain.withContentFonts()
    return if (styled === plain) null else styled
}

/** Fixed-width LTR pattern spans: ASCII regex syntax stays monospace while Persian words use Vazirmatn. */
internal fun AnnotatedString.withPatternFonts(): AnnotatedString = withScriptFonts(FontFamily.Monospace)

/**
 * Adds Vazirmatn spans to Arabic-script runs without changing the base [TextStyle]'s font family
 * (keeps Funnel Sans on English dialog titles and UI typography on interpolated hints).
 */
internal fun persianSafeOrNull(text: String): AnnotatedString? {
    if (text.isEmpty()) return null
    val ranges = persianFontRanges(text)
    if (ranges.isEmpty()) return null
    return buildAnnotatedString {
        append(text)
        val persianStyle = SpanStyle(fontFamily = persian)
        for (range in ranges) {
            addStyle(persianStyle, range.first, range.last + 1)
        }
    }
}

/** Script-font visual transformation for any editable or selectable user-content field. */
val ContentFontVisualTransformation: VisualTransformation = VisualTransformation {
    TransformedText(it.withContentFonts(), OffsetMapping.Identity)
}

/** Script-font visual transformation for fixed-width regex pattern fields. */
val PatternFontVisualTransformation: VisualTransformation = VisualTransformation {
    TransformedText(it.withPatternFonts(), OffsetMapping.Identity)
}

/** Tabular (equal-width) figures in the active theme font — never system monospace. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Fixed-width LTR style for regex patterns; pair with [PatternText] or [PatternFontVisualTransformation]. */
fun TextStyle.forPattern(text: CharSequence = ""): TextStyle = copy(
    fontFamily = FontFamily.Monospace,
    textDirection = TextDirection.Ltr,
    lineHeight = if (hasArabicLetter(text)) 1.7.em else lineHeight,
)

internal fun TextStyle.forContent(text: CharSequence): TextStyle =
    if (text.isEmpty()) this else forContentScript(hasArabicLetter(text))

/** Same as [forContent] when the caller already knows whether the text needs Persian leading. */
internal fun TextStyle.forContentScript(persianLeading: Boolean): TextStyle =
    copy(fontFamily = body, lineHeight = if (persianLeading) 1.7.em else 1.45.em)

/**
 * Script-aware style for text fields: keeps the UI locale's style while empty (so cursor and
 * placeholder share one line box), switches to content script fonts once typed, and never shrinks
 * leading below the base style (English typed in a Persian UI keeps 1.7em so the field never jumps).
 */
internal fun TextStyle.forFieldContent(text: CharSequence): TextStyle {
    if (text.isEmpty()) return this
    val content = forContent(text)
    return if (content.lineHeightSp().value < lineHeightSp().value) {
        content.copy(lineHeight = lineHeight)
    } else {
        content
    }
}

/**
 * Line height as an absolute sp value. The theme stores leading in em; never call [TextUnit.toDp]
 * on an em [lineHeight] — multiply by [fontSize] first.
 */
internal fun TextStyle.lineHeightSp(): TextUnit = when {
    lineHeight.isEm -> fontSize * lineHeight.value
    lineHeight.isSp -> lineHeight
    else -> fontSize
}

/**
 * What people wrote (a message, a name) reads in its own direction, not the interface's: English
 * text in the Persian interface keeps its full stop at the end.
 */
internal fun TextStyle.forUserText(text: CharSequence): TextStyle = forContent(text).copy(textDirection = TextDirection.ContentOrLtr)

/** A phone number or address isolated left to right, so "555 0142" never reads "0142 555" in Persian. */
fun ltr(text: String): String = if (text.isEmpty()) text else "\u2066$text\u2069"


/** A label with no letters is a number, so "+1555…" must not put the + at the other end in Persian. */
fun CharSequence.isNumberLabel(): Boolean = none { it.isLetter() } && any { it.isDigit() }

fun TextStyle.forLabel(text: CharSequence, layout: LayoutDirection): TextStyle = forContent(text).withLabelDirection(text, layout)

/**
 * A label (a name, a title, a date, a number) reads in its own order but lines up with the interface around it.
 * Compose lays out an unspecified direction strictly in the interface's direction, so "BING-POT!!" in the Persian
 * interface drew "!!BING-POT"; its first letter decides instead, and a letter-free label such as "+1 555…" stays
 * left to right. Start and End are the interface's edges, so the English name still starts where Persian ones do.
 */
fun TextStyle.withLabelDirection(text: CharSequence, layout: LayoutDirection): TextStyle = copy(
    textDirection = if (text.isNumberLabel()) TextDirection.Ltr else TextDirection.Content,
    textAlign = textAlign.fromInterfaceEdge(layout),
)

/** Start and End measured from the interface's edges rather than from the text's own direction. */
internal fun TextAlign.fromInterfaceEdge(layout: LayoutDirection): TextAlign {
    val rtl = layout == LayoutDirection.Rtl
    return when (this) {
        TextAlign.Unspecified, TextAlign.Start -> if (rtl) TextAlign.Right else TextAlign.Left
        TextAlign.End -> if (rtl) TextAlign.Left else TextAlign.Right
        else -> this
    }
}

private fun typography(rtl: Boolean): Typography {
    val displayFamily = if (rtl) persian else display
    val bodyFamily = if (rtl) persian else body
    val leading = if (rtl) 1.7.em else 1.45.em
    // Center extra leading so Vazirmatn's descent (and Atkinson's) does not sit entirely below the glyph.
    val centeredLeading = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    fun style(family: FontFamily, size: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, lineHeight = leading)
    return Typography(
        displayLarge = style(displayFamily, 57, FontWeight.Normal).copy(lineHeight = 1.15.em),
        displayMedium = style(displayFamily, 45, FontWeight.Normal).copy(lineHeight = 1.2.em),
        displaySmall = style(displayFamily, 36, FontWeight.Normal).copy(lineHeight = 1.25.em),
        headlineLarge = style(displayFamily, 32, FontWeight.Normal).copy(lineHeight = 1.25.em),
        headlineMedium = style(displayFamily, 31, FontWeight.ExtraBold).copy(lineHeight = 1.25.em),
        // Dialog titles (text-title-lg).
        headlineSmall = style(displayFamily, 25, FontWeight.Normal).copy(lineHeight = 1.3.em),
        titleLarge = style(bodyFamily, 20, FontWeight.Bold),
        titleMedium = style(bodyFamily, 18, FontWeight.Bold),
        titleSmall = style(bodyFamily, 14, FontWeight.Bold),
        // Composer / fields read bodyLarge; keep the style focused here so other roles stay screenshot-stable.
        bodyLarge = style(bodyFamily, 18, FontWeight.Normal).copy(lineHeightStyle = centeredLeading),
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
            primary = color(com.afsh.commons.R.color.m3_primary_dark),
            onPrimary = color(com.afsh.commons.R.color.m3_on_primary_dark),
            primaryContainer = color(com.afsh.commons.R.color.m3_primary_container_dark),
            onPrimaryContainer = color(com.afsh.commons.R.color.m3_on_primary_container_dark),
            secondary = color(com.afsh.commons.R.color.m3_secondary_dark),
            onSecondary = color(com.afsh.commons.R.color.m3_on_secondary_dark),
            secondaryContainer = color(com.afsh.commons.R.color.m3_secondary_container_dark),
            onSecondaryContainer = color(com.afsh.commons.R.color.m3_on_secondary_container_dark),
            tertiary = color(com.afsh.commons.R.color.m3_tertiary_dark),
            onTertiary = color(com.afsh.commons.R.color.m3_on_tertiary_dark),
            tertiaryContainer = color(com.afsh.commons.R.color.m3_tertiary_container_dark),
            onTertiaryContainer = color(com.afsh.commons.R.color.m3_on_tertiary_container_dark),
            background = color(com.afsh.commons.R.color.m3_background_dark),
            onBackground = color(com.afsh.commons.R.color.m3_on_background_dark),
            surface = color(com.afsh.commons.R.color.m3_surface_dark),
            onSurface = color(com.afsh.commons.R.color.m3_on_surface_dark),
            surfaceVariant = color(com.afsh.commons.R.color.m3_surface_variant_dark),
            onSurfaceVariant = color(com.afsh.commons.R.color.m3_on_surface_variant_dark),
            surfaceTint = color(com.afsh.commons.R.color.m3_surface_tint_dark),
            inverseSurface = color(com.afsh.commons.R.color.m3_inverse_surface_dark),
            inverseOnSurface = color(com.afsh.commons.R.color.m3_inverse_on_surface_dark),
            inversePrimary = color(com.afsh.commons.R.color.m3_inverse_primary_dark),
            error = color(com.afsh.commons.R.color.m3_error_dark),
            onError = color(com.afsh.commons.R.color.m3_on_error_dark),
            errorContainer = color(com.afsh.commons.R.color.m3_error_container_dark),
            onErrorContainer = color(com.afsh.commons.R.color.m3_on_error_container_dark),
            outline = color(com.afsh.commons.R.color.m3_outline_dark),
            outlineVariant = color(com.afsh.commons.R.color.m3_outline_variant_dark),
            scrim = color(com.afsh.commons.R.color.m3_scrim),
            surfaceBright = color(com.afsh.commons.R.color.m3_surface_bright_dark),
            surfaceDim = color(com.afsh.commons.R.color.m3_surface_dim_dark),
            surfaceContainer = color(com.afsh.commons.R.color.m3_surface_container_dark),
            surfaceContainerHigh = color(com.afsh.commons.R.color.m3_surface_container_high_dark),
            surfaceContainerHighest = color(com.afsh.commons.R.color.m3_surface_container_highest_dark),
            surfaceContainerLow = color(com.afsh.commons.R.color.m3_surface_container_low_dark),
            surfaceContainerLowest = color(com.afsh.commons.R.color.m3_surface_container_lowest_dark),
        )
    } else {
        lightColorScheme(
            primary = color(com.afsh.commons.R.color.m3_primary),
            onPrimary = color(com.afsh.commons.R.color.m3_on_primary),
            primaryContainer = color(com.afsh.commons.R.color.m3_primary_container),
            onPrimaryContainer = color(com.afsh.commons.R.color.m3_on_primary_container),
            secondary = color(com.afsh.commons.R.color.m3_secondary),
            onSecondary = color(com.afsh.commons.R.color.m3_on_secondary),
            secondaryContainer = color(com.afsh.commons.R.color.m3_secondary_container),
            onSecondaryContainer = color(com.afsh.commons.R.color.m3_on_secondary_container),
            tertiary = color(com.afsh.commons.R.color.m3_tertiary),
            onTertiary = color(com.afsh.commons.R.color.m3_on_tertiary),
            tertiaryContainer = color(com.afsh.commons.R.color.m3_tertiary_container),
            onTertiaryContainer = color(com.afsh.commons.R.color.m3_on_tertiary_container),
            background = color(com.afsh.commons.R.color.m3_background),
            onBackground = color(com.afsh.commons.R.color.m3_on_background),
            surface = color(com.afsh.commons.R.color.m3_surface),
            onSurface = color(com.afsh.commons.R.color.m3_on_surface),
            surfaceVariant = color(com.afsh.commons.R.color.m3_surface_variant),
            onSurfaceVariant = color(com.afsh.commons.R.color.m3_on_surface_variant),
            surfaceTint = color(com.afsh.commons.R.color.m3_surface_tint),
            inverseSurface = color(com.afsh.commons.R.color.m3_inverse_surface),
            inverseOnSurface = color(com.afsh.commons.R.color.m3_inverse_on_surface),
            inversePrimary = color(com.afsh.commons.R.color.m3_inverse_primary),
            error = color(com.afsh.commons.R.color.m3_error),
            onError = color(com.afsh.commons.R.color.m3_on_error),
            errorContainer = color(com.afsh.commons.R.color.m3_error_container),
            onErrorContainer = color(com.afsh.commons.R.color.m3_on_error_container),
            outline = color(com.afsh.commons.R.color.m3_outline),
            outlineVariant = color(com.afsh.commons.R.color.m3_outline_variant),
            scrim = color(com.afsh.commons.R.color.m3_scrim),
            surfaceBright = color(com.afsh.commons.R.color.m3_surface_bright),
            surfaceDim = color(com.afsh.commons.R.color.m3_surface_dim),
            surfaceContainer = color(com.afsh.commons.R.color.m3_surface_container),
            surfaceContainerHigh = color(com.afsh.commons.R.color.m3_surface_container_high),
            surfaceContainerHighest = color(com.afsh.commons.R.color.m3_surface_container_highest),
            surfaceContainerLow = color(com.afsh.commons.R.color.m3_surface_container_low),
            surfaceContainerLowest = color(com.afsh.commons.R.color.m3_surface_container_lowest),
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
        scrim = colorResource(if (dark) com.afsh.commons.R.color.m3_scrim_dim_dark else com.afsh.commons.R.color.m3_scrim_dim),
        liftScrim = colorResource(com.afsh.commons.R.color.m3_lift_scrim),
        focusRing = scheme.primary,
        transparent = colorResource(com.afsh.commons.R.color.m3_transparent),
        mask = scheme.scrim,
        header = ColorRole(scheme.surface, scheme.onSurface),
        sentBubble = primaryContainer,
        receivedBubble = ColorRole(scheme.surfaceContainerHighest, scheme.onSurface),
        secondaryButton = ColorRole(scheme.secondaryContainer, scheme.onSecondaryContainer),
        swipeAction = primaryContainer,
        selected = ColorRole(scheme.secondaryContainer, scheme.onSecondaryContainer),
        badge = ColorRole(scheme.primary, scheme.onPrimary),
        themePreview = ThemePreview(
            light = ColorRole(colorResource(com.afsh.commons.R.color.m3_surface), colorResource(com.afsh.commons.R.color.m3_primary)),
            dark = ColorRole(colorResource(com.afsh.commons.R.color.m3_surface_dark), colorResource(com.afsh.commons.R.color.m3_primary_dark)),
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

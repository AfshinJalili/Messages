package com.afsh.messages.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.afsh.messages.ui.contentAnnotatedOrNull
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.forLabel
import com.afsh.messages.ui.forPattern
import com.afsh.messages.ui.forUserText
import com.afsh.messages.ui.fromInterfaceEdge
import com.afsh.messages.ui.persianSafeOrNull
import com.afsh.messages.ui.withContentFonts
import com.afsh.messages.ui.withPatternFonts

/**
 * Reading-direction policy for [ContentText]:
 * - [Text]: message bodies, captions, previews, and drafts (`TextDirection.ContentOrLtr`, so
 *   English text in a Persian interface keeps its punctuation on the right, and lines align with
 *   the text's own direction).
 * - [Label]: names, titles, senders, phrases, and phone numbers ([com.afsh.messages.ui.withLabelDirection]:
 *   the first letter decides, letter-free numbers like `+1 555…` stay left to right, and the line starts at
 *   the interface's start edge).
 * - [Inherit]: keeps the caller's [TextStyle.textDirection], for interface sentences that
 *   interpolate a value (isolate the value with [com.afsh.messages.ui.isolate]).
 *
 * A `textAlign` passed to [ContentText] measures Start and End from the interface's edges, so a
 * preview in a row (`TextAlign.Start`) lines up with the row whatever its own direction.
 */
enum class ContentDirection { Text, Label, Inherit }

private fun TextStyle.resolveContentStyle(
    text: CharSequence,
    direction: ContentDirection,
    textAlign: TextAlign?,
    layout: LayoutDirection,
): TextStyle {
    val aligned = if (textAlign != null) copy(textAlign = textAlign.fromInterfaceEdge(layout)) else this
    return when (direction) {
        ContentDirection.Text -> aligned.forUserText(text)
        ContentDirection.Label -> aligned.forLabel(text, layout)
        ContentDirection.Inherit -> aligned.forContent(text)
    }
}

/**
 * Single owner for displaying user-authored or dynamic text (messages, contact names, captions,
 * search queries, filenames) with AFSH content fonts: Atkinson Hyperlegible for Latin, Vazirmatn
 * for Arabic script, and 1.7em/1.45em leading regardless of app language.
 *
 * Latin-only strings use Compose's fast `Text(String)` path; Arabic-script strings use the cached
 * Persian span builder in `AfshTheme.kt`.
 */
@Composable
fun ContentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    direction: ContentDirection = ContentDirection.Label,
) {
    val layout = LocalLayoutDirection.current
    val resolvedStyle = remember(text, style, direction, textAlign, layout) { style.resolveContentStyle(text, direction, textAlign, layout) }
    val annotated = remember(text) { contentAnnotatedOrNull(text) }
    if (annotated != null) {
        Text(
            text = annotated,
            modifier = modifier,
            color = color,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            style = resolvedStyle,
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            style = resolvedStyle,
        )
    }
}

/**
 * [ContentText] overload for pre-styled [AnnotatedString]s (links, search highlights, draft
 * prefixes). Pass [leadingSource] when line-height leading should follow a substring (such as the
 * user's draft text rather than the localized "Draft:" prefix).
 */
@Composable
fun ContentText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    direction: ContentDirection = ContentDirection.Label,
    leadingSource: CharSequence = text,
) {
    val styled = remember(text) { text.withContentFonts() }
    val layout = LocalLayoutDirection.current
    val resolvedStyle = remember(leadingSource, style, direction, textAlign, layout) {
        style.resolveContentStyle(leadingSource, direction, textAlign, layout)
    }
    Text(
        text = styled,
        modifier = modifier,
        color = color,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = resolvedStyle,
    )
}

/**
 * Renders a regex pattern left-to-right in monospace for ASCII syntax while applying Vazirmatn to
 * any Arabic-script words inside the pattern so they never fall back to the system Arabic font.
 */
@Composable
fun PatternText(
    pattern: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val styled = remember(pattern) { AnnotatedString(pattern).withPatternFonts() }
    val resolvedStyle = remember(pattern, style) { style.forPattern(pattern) }
    Text(
        text = styled,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        style = resolvedStyle,
    )
}

/**
 * Renders interface text that may interpolate a user/contact name (such as a dialog title or
 * composer hint): preserves [style]'s font family (e.g. Funnel Sans on English headings) while
 * applying Vazirmatn to any Arabic-script runs so Persian names never fall back to system fonts.
 */
@Composable
fun MixedLocaleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotated = remember(text) { persianSafeOrNull(text) }
    if (annotated != null) {
        Text(
            text = annotated,
            modifier = modifier,
            color = color,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            style = style,
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            style = style,
        )
    }
}

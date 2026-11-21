package com.afsh.messages.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.afsh.messages.ui.contentAnnotatedOrNull
import com.afsh.messages.ui.forContent
import com.afsh.messages.ui.forLabel
import com.afsh.messages.ui.forPattern
import com.afsh.messages.ui.forUserText
import com.afsh.messages.ui.persianSafeOrNull
import com.afsh.messages.ui.withContentFonts
import com.afsh.messages.ui.withPatternFonts

/**
 * Reading-direction policy for [ContentText]:
 * - [Text]: message bodies, captions, previews, and drafts (`TextDirection.ContentOrLtr`, so
 *   English text in a Persian interface keeps its punctuation on the right).
 * - [Label]: names, titles, senders, and phone numbers (`withNumberOrder`, so letter-free numbers
 *   like `+1 555…` never reverse in Persian).
 * - [Inherit]: keeps the caller's [TextStyle.textDirection].
 */
enum class ContentDirection { Text, Label, Inherit }

private fun TextStyle.resolveContentStyle(text: CharSequence, direction: ContentDirection): TextStyle =
    when (direction) {
        ContentDirection.Text -> forUserText(text)
        ContentDirection.Label -> forLabel(text)
        ContentDirection.Inherit -> forContent(text)
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
    val resolvedStyle = remember(text, style, direction) { style.resolveContentStyle(text, direction) }
    val annotated = remember(text) { contentAnnotatedOrNull(text) }
    if (annotated != null) {
        Text(
            text = annotated,
            modifier = modifier,
            color = color,
            textAlign = textAlign,
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
            textAlign = textAlign,
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
    val resolvedStyle = remember(leadingSource, style, direction) {
        style.resolveContentStyle(leadingSource, direction)
    }
    Text(
        text = styled,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
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

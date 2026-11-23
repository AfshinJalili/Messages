package com.afsh.messages.ui.thread

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.core.text.util.LinkifyCompat

/** A link in a message body: web links and email addresses open, standalone numbers copy. [end] is exclusive. */
internal data class DetectedLink(val start: Int, val end: Int, val value: String, val isUrl: Boolean)

private val linkScanCache = object : LruCache<String, List<DetectedLink>>(512) {}

/** The links in [body], cached per body, found with the platform's web and email scanner. */
internal fun detectLinks(body: String): List<DetectedLink> {
    if (body.isEmpty()) return emptyList()
    linkScanCache.get(body)?.let { return it }
    val links = detectLinks(body, ::platformUrls)
    linkScanCache.put(body, links)
    return links
}

/**
 * The links in [body], in order. [findUrls] runs only when the body has a '.', '@' or ':', and numbers are looked
 * for only when it has a digit. A number inside a URL belongs to the URL.
 */
internal fun detectLinks(body: String, findUrls: (String) -> List<DetectedLink>): List<DetectedLink> {
    var hasUrlHint = false
    var hasDigit = false
    for (i in body.indices) {
        val c = body[i]
        if (c == '.' || c == '@' || c == ':') hasUrlHint = true
        else if (c.isDigit()) hasDigit = true
        if (hasUrlHint && hasDigit) break
    }
    if (!hasUrlHint && !hasDigit) return emptyList()
    val urls = if (hasUrlHint) findUrls(body) else emptyList()
    val numbers = if (hasDigit) {
        NUMBER.findAll(body)
            .filter { match -> urls.none { match.range.first < it.end && match.range.last >= it.start } }
            .map { DetectedLink(it.range.first, it.range.last + 1, it.value, isUrl = false) }
            .toList()
    } else {
        emptyList()
    }
    return when {
        urls.isEmpty() -> numbers
        numbers.isEmpty() -> urls
        else -> (urls + numbers).sortedBy { it.start }
    }
}

private fun platformUrls(body: String): List<DetectedLink> {
    val spannable = SpannableString(body)
    LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
    return spannable.getSpans(0, body.length, URLSpan::class.java)
        .map { DetectedLink(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url, isUrl = true) }
}

// A match is the whole number or nothing. Isolating only part of it, as "000,000" out of
// "مانده70,000,000", leaves a separator outside the isolate and the groups read "000,000,70".
private const val NUMBER_SEPARATOR = "[ .,:/()\\-\u066B\u066C]"
private val NUMBER = Regex(
    "(?<![\\p{L}\\p{N}]|\\p{N}$NUMBER_SEPARATOR)\\+?\\p{Nd}+(?:$NUMBER_SEPARATOR\\p{Nd}+)*(?![\\p{L}\\p{N}]|$NUMBER_SEPARATOR\\p{N})"
)

/** [body] with each of [links] bold, underlined and isolated left to right; a URL opens, a number calls [onCopy]. */
internal fun buildLinkifiedText(
    body: String,
    links: List<DetectedLink>,
    linkColor: Color = Color.Unspecified,
    onCopy: (String) -> Unit,
): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var cursor = 0
        links.forEach { (start, end, value, isUrl) ->
            append(body, cursor, start)
            val link = if (isUrl) {
                LinkAnnotation.Url(value, style)
            } else {
                LinkAnnotation.Clickable(value, style) { onCopy(value) }
            }
            // Isolated left to right, so a spaced phone number or a URL keeps its order inside Persian text.
            append('\u2066')
            withLink(link) { append(body, start, end) }
            append('\u2069')
            cursor = end
        }
        append(body, cursor, body.length)
    }
}

internal fun linkify(body: String, linkColor: Color = Color.Unspecified, onCopy: (String) -> Unit): AnnotatedString {
    val links = detectLinks(body)
    return if (links.isEmpty()) AnnotatedString(body) else buildLinkifiedText(body, links, linkColor, onCopy)
}

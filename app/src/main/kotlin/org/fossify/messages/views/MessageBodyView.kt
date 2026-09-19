package org.fossify.messages.views

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.fossify.commons.views.MyTextView
import androidx.core.text.util.LinkifyCompat
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.showErrorToast

/** Keeps native click/long-press handling, but routes exact text hits before the bubble click. */
class MessageBodyView : MyTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    var specialClicksEnabled: () -> Boolean = { true }
    private var pressedSpan: ClickableSpan? = null

    fun setMessageBody(body: String) {
        pressedSpan = null
        val content = SpannableString(body)
        LinkifyCompat.addLinks(content, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        // Keep digits inside URLs/email addresses owned by the link, including query parameters.
        NUMBER.findAll(body).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (content.getSpans(start, end, URLSpan::class.java).isEmpty()) {
                content.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = context.copyToClipboard(match.value)
                    override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = true }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        text = content
        // TextView's native movement method also fires the ordinary click. Route it in performClick instead.
        movementMethod = null
    }

    // The superclass detects clicks and calls our performClick, including accessibility clicks.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedSpan = if (specialClicksEnabled()) spanAt(event) else null
            MotionEvent.ACTION_MOVE -> if (spanAt(event) !== pressedSpan) pressedSpan = null
            MotionEvent.ACTION_CANCEL -> pressedSpan = null
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        val span = pressedSpan
        pressedSpan = null
        if (span != null && specialClicksEnabled()) {
            try { span.onClick(this) } catch (e: Exception) { context.showErrorToast(e) }
            return true
        }
        return super.performClick()
    }

    override fun performLongClick(): Boolean {
        pressedSpan = null
        return super.performLongClick()
    }

    private fun spanAt(event: MotionEvent): ClickableSpan? {
        val layout = layout ?: return null
        val content = text as? Spanned ?: return null
        val x = event.x - totalPaddingLeft + scrollX
        val y = event.y - totalPaddingTop + scrollY
        if (y < 0 || y >= layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        if (x < layout.getLineLeft(line) || x >= layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x)
        return content.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull {
            offset >= content.getSpanStart(it) && offset < content.getSpanEnd(it)
        }
    }

    companion object {
        private val NUMBER = Regex("(?<![\\p{L}\\p{N}])\\+?\\p{Nd}+(?:[ .()\\-]\\p{Nd}+)*(?![\\p{L}\\p{N}])")
    }
}

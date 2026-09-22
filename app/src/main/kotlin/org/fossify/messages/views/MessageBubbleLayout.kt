package org.fossify.messages.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import org.fossify.messages.R
import kotlin.math.max

/** Keeps metadata beside the last text line only when their measured bounds cannot overlap. */
class MessageBubbleLayout(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private var inlineFooter = false
    private var singleLine = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        inlineFooter = false
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val body = findViewById<MessageBodyView>(R.id.thread_message_body)
        val footer = findViewById<View>(R.id.thread_message_footer)
        val attachments = findViewById<View>(R.id.thread_message_attachments_holder)
        val otp = findViewById<View>(R.id.thread_message_otp)
        val textLayout = body.layout ?: return
        if (body.visibility != VISIBLE || attachments.visibility == VISIBLE || otp.visibility == VISIBLE ||
            body.compoundDrawables.any { it != null } || textLayout.lineCount == 0) return

        val gap = resources.getDimensionPixelSize(R.dimen.message_metadata_gap)
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val footerParams = footer.layoutParams as LayoutParams
        singleLine = textLayout.lineCount == 1
        if (singleLine) {
            val desired = paddingLeft + body.measuredWidth + gap + footer.measuredWidth + paddingRight
            if (desired > maxWidth) return
            super.onMeasure(MeasureSpec.makeMeasureSpec(desired, MeasureSpec.EXACTLY), heightMeasureSpec)
            inlineFooter = true
        } else {
            val last = textLayout.lineCount - 1
            val footerLeft = measuredWidth - paddingRight - footer.measuredWidth
            val bodyLeft = if (layoutDirection == LAYOUT_DIRECTION_RTL) measuredWidth - paddingRight - body.measuredWidth else paddingLeft
            val textRight = bodyLeft + body.totalPaddingLeft + textLayout.getLineRight(last)
            inlineFooter = textRight + gap <= footerLeft && footer.measuredHeight <= textLayout.getLineBottom(last) - textLayout.getLineTop(last)
        }
        if (inlineFooter) {
            setMeasuredDimension(measuredWidth,
                max(paddingTop + max(body.measuredHeight, footer.measuredHeight) + paddingBottom,
                    measuredHeight - footer.measuredHeight - footerParams.topMargin - footerParams.bottomMargin))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!inlineFooter) return
        val body = findViewById<MessageBodyView>(R.id.thread_message_body)
        val footer = findViewById<View>(R.id.thread_message_footer)
        if (singleLine) {
            // Keep the text and metadata in separate physical columns even for Persian text.
            body.layout(paddingLeft, paddingTop, paddingLeft + body.measuredWidth, paddingTop + body.measuredHeight)
        }
        val footerLeft = width - paddingRight - footer.measuredWidth
        val footerBottom = height - paddingBottom
        footer.layout(footerLeft, footerBottom - footer.measuredHeight, footerLeft + footer.measuredWidth, footerBottom)
    }
}

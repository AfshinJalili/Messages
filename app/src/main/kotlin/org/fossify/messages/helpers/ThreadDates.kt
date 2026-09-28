package org.fossify.messages.helpers

import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.messages.R
import org.joda.time.DateTime

object ThreadDates {
    fun label(seconds: Int, now: DateTime = DateTime.now()): String {
        val date = DateTime(seconds * 1000L)
        // Android's DateFormat, not Joda or java.text: only it prints Persian digits (۲۴ سپتامبر) as the timestamps do.
        return DateFormat.format(if (date.year == now.year) "d MMMM" else "d MMMM yyyy", date.toDate()).toString()
    }

    fun style(view: TextView, activity: BaseSimpleActivity) {
        view.setTextColor(activity.getProperTextColor())
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
            activity.getTextSize() * activity.resources.designFloat(R.dimen.type_scale_metadata))
        view.background = GradientDrawable().apply {
            setColor(activity.tonalSurfaceColor())
            cornerRadius = activity.resources.getDimension(R.dimen.message_bubble_radius)
        }
    }
}

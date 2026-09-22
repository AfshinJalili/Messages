package org.fossify.messages.helpers

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.messages.R
import org.joda.time.DateTime
import java.util.Locale

object ThreadDates {
    fun label(seconds: Int, now: DateTime = DateTime.now()): String {
        val date = DateTime(seconds * 1000L)
        return date.toString(if (date.year == now.year) "d MMMM" else "d MMMM yyyy", Locale.getDefault())
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

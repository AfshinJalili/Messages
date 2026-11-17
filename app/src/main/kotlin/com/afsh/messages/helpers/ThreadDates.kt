package com.afsh.messages.helpers

import android.text.format.DateFormat
import org.joda.time.DateTime

object ThreadDates {
    fun label(seconds: Int, now: DateTime = DateTime.now()): String {
        val date = DateTime(seconds * 1000L)
        // Android's DateFormat, not Joda or java.text: only it prints Persian digits (۲۴ سپتامبر) as the timestamps do.
        return DateFormat.format(if (date.year == now.year) "d MMMM" else "d MMMM yyyy", date.toDate()).toString()
    }
}

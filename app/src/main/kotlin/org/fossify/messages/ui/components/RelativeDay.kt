package org.fossify.messages.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.messages.R
import java.util.Calendar

enum class RelativeDay { TODAY, YESTERDAY, EARLIER }

fun relativeDay(millis: Long, now: Calendar = Calendar.getInstance()): RelativeDay {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun Calendar.sameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    return when {
        day.sameDay(now) -> RelativeDay.TODAY
        day.sameDay(yesterday) -> RelativeDay.YESTERDAY
        else -> RelativeDay.EARLIER
    }
}

/**
 * "18 Sep", with the year when it is not this year. Android's DateFormat localizes the digits
 * (۱۸ سپتامبر).
 */
fun shortDate(millis: Long, now: Calendar = Calendar.getInstance()): String {
    val year = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    return DateFormat.format(if (year == now.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy", millis).toString()
}

/** Today, Yesterday, or [shortDate]. */
fun dayLabel(context: Context, millis: Long, now: Calendar = Calendar.getInstance()): String =
    when (relativeDay(millis, now)) {
        RelativeDay.TODAY -> context.getString(R.string.inbox_section_today)
        RelativeDay.YESTERDAY -> context.getString(R.string.inbox_section_yesterday)
        RelativeDay.EARLIER -> shortDate(millis, now)
    }

/** "Today · 12:41", "18 Sep · 09:12": the day and the time in the app's 12 or 24 hour setting. */
@Composable
fun rememberDayAndTime(millis: Long): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(millis, configuration) {
        val time = DateFormat.format(context.getTimeFormat(), millis).toString()
        context.getString(R.string.day_and_time, dayLabel(context, millis), time)
    }
}

/** For send times: Today, Tomorrow, or "Fri 25 Sep" (with the year when it is not this year). */
fun upcomingDayLabel(context: Context, millis: Long, now: Calendar = Calendar.getInstance()): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    fun Calendar.sameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    return when {
        day.sameDay(now) -> context.getString(R.string.inbox_section_today)
        day.sameDay(tomorrow) -> context.getString(R.string.day_tomorrow)
        day.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> DateFormat.format("EEE d MMM", millis).toString()
        else -> DateFormat.format("EEE d MMM yyyy", millis).toString()
    }
}

/** "Tomorrow · 09:00": a send time in the app's 12 or 24 hour setting. */
fun upcomingDayAndTime(context: Context, millis: Long): String =
    context.getString(R.string.day_and_time, upcomingDayLabel(context, millis), DateFormat.format(context.getTimeFormat(), millis).toString())

package com.afsh.messages.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.afsh.commons.extensions.getTimeFormat
import com.afsh.messages.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

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

/**
 * Compact Gregorian date for conversation and search rows: short month+day this year (skeleton
 * MMMd), numeric yyMd for older years. Patterns come from [DateFormat.getBestDateTimePattern] on
 * Android; unit tests pass the same skeletons' resolved forms.
 */
fun compactListDate(
    millis: Long,
    now: Calendar = Calendar.getInstance(),
    locale: Locale = Locale.getDefault(),
    sameYearPattern: String,
    priorYearPattern: String,
): String {
    val dayYear = GregorianCalendar().apply { timeInMillis = millis }.get(Calendar.YEAR)
    val nowYear = GregorianCalendar().apply { timeInMillis = now.timeInMillis }.get(Calendar.YEAR)
    val pattern = if (dayYear == nowYear) sameYearPattern else priorYearPattern
    return SimpleDateFormat(pattern, locale).apply { calendar = GregorianCalendar() }.format(millis)
}

/**
 * Inbox, Archive and Search row timestamps: clock time today, Yesterday, then [compactListDate].
 * Pure entry for JVM tests; [timeText] is only used for [RelativeDay.TODAY].
 */
fun conversationListTimestamp(
    millis: Long,
    now: Calendar,
    timeText: String,
    yesterdayLabel: String,
    locale: Locale,
    sameYearPattern: String,
    priorYearPattern: String,
): String = when (relativeDay(millis, now)) {
    RelativeDay.TODAY -> timeText
    RelativeDay.YESTERDAY -> yesterdayLabel
    RelativeDay.EARLIER -> compactListDate(millis, now, locale, sameYearPattern, priorYearPattern)
}

/** Conversation and search list timestamps. Keeps the app's 12/24-hour time setting for today. */
fun conversationListTimestamp(
    context: Context,
    millis: Long,
    now: Calendar = Calendar.getInstance(),
): String {
    val locale = context.resources.configuration.locales[0]
    return conversationListTimestamp(
        millis = millis,
        now = now,
        timeText = DateFormat.format(context.getTimeFormat(), millis).toString(),
        yesterdayLabel = context.getString(R.string.inbox_section_yesterday),
        locale = locale,
        sameYearPattern = DateFormat.getBestDateTimePattern(locale, "MMMd"),
        priorYearPattern = DateFormat.getBestDateTimePattern(locale, "yyMd"),
    )
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

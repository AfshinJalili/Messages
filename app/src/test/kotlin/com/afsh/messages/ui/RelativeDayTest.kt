package com.afsh.messages.ui

import com.afsh.messages.ui.components.RelativeDay
import com.afsh.messages.ui.components.compactListDate
import com.afsh.messages.ui.components.conversationListTimestamp
import com.afsh.messages.ui.components.relativeDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

class RelativeDayTest {

    private val now = GregorianCalendar(2026, Calendar.NOVEMBER, 21, 16, 0, 0)
    private val today = GregorianCalendar(2026, Calendar.NOVEMBER, 21, 9, 15, 0).timeInMillis
    private val yesterday = GregorianCalendar(2026, Calendar.NOVEMBER, 20, 18, 0, 0).timeInMillis
    private val earlierThisYear = GregorianCalendar(2026, Calendar.SEPTEMBER, 21, 15, 30, 0).timeInMillis
    private val priorYear = GregorianCalendar(2023, Calendar.DECEMBER, 18, 10, 0, 0).timeInMillis

    @Test
    fun relativeDayBuckets() {
        assertEquals(RelativeDay.TODAY, relativeDay(today, now))
        assertEquals(RelativeDay.YESTERDAY, relativeDay(yesterday, now))
        assertEquals(RelativeDay.EARLIER, relativeDay(earlierThisYear, now))
        assertEquals(RelativeDay.EARLIER, relativeDay(priorYear, now))
    }

    @Test
    fun englishConversationListTimestamp() {
        val locale = Locale.US
        assertEquals(
            "9:15 AM",
            conversationListTimestamp(today, now, "9:15 AM", "Yesterday", locale, "MMM d", "M/d/yy"),
        )
        assertEquals(
            "Yesterday",
            conversationListTimestamp(yesterday, now, "9:15 AM", "Yesterday", locale, "MMM d", "M/d/yy"),
        )
        assertEquals(
            "Sep 21",
            conversationListTimestamp(earlierThisYear, now, "9:15 AM", "Yesterday", locale, "MMM d", "M/d/yy"),
        )
        val prior = conversationListTimestamp(priorYear, now, "9:15 AM", "Yesterday", locale, "MMM d", "M/d/yy")
        assertEquals("12/18/23", prior)
        assertFalse(prior.contains("December"))
        assertTrue(prior.length <= "12/18/23".length)
    }

    @Test
    fun persianConversationListTimestamp() {
        val locale = Locale.forLanguageTag("fa")
        assertEquals(
            "۰۹:۱۵",
            conversationListTimestamp(today, now, "۰۹:۱۵", "دیروز", locale, "d MMM", "yy/M/d"),
        )
        assertEquals(
            "دیروز",
            conversationListTimestamp(yesterday, now, "۰۹:۱۵", "دیروز", locale, "d MMM", "yy/M/d"),
        )
        val sameYear = conversationListTimestamp(
            earlierThisYear, now, "۰۹:۱۵", "دیروز", locale, "d MMM", "yy/M/d",
        )
        assertEquals("۲۱ سپتامبر", sameYear)
        assertFalse(sameYear.contains("2026"))

        val prior = conversationListTimestamp(
            priorYear, now, "۰۹:۱۵", "دیروز", locale, "d MMM", "yy/M/d",
        )
        assertEquals("۲۳/۱۲/۱۸", prior)
        assertFalse(prior.contains("دسامبر"))
        assertFalse(prior.contains("December"))
        assertTrue(prior.contains("۲۳"))
    }

    @Test
    fun compactListDateStaysGregorianForPersianLocale() {
        val locale = Locale.forLanguageTag("fa")
        val sameYear = compactListDate(earlierThisYear, now, locale, "d MMM", "yy/M/d")
        assertEquals("۲۱ سپتامبر", sameYear)

        val prior = compactListDate(priorYear, now, locale, "d MMM", "yy/M/d")
        assertEquals("۲۳/۱۲/۱۸", prior)
        // December 2023 is month 12 in Gregorian; Jalali would be a different month number.
        assertTrue(prior.startsWith("۲۳/") || prior.contains("/۱۲/"))
    }

    @Test
    fun englishGbUsesDayMonthOrder() {
        val locale = Locale.UK
        val sameYear = compactListDate(earlierThisYear, now, locale, "d MMM", "dd/MM/yy")
        assertTrue(sameYear.startsWith("21 "))
        assertTrue(sameYear.contains("Sep"))
        assertEquals("18/12/23", compactListDate(priorYear, now, locale, "d MMM", "dd/MM/yy"))
    }
}

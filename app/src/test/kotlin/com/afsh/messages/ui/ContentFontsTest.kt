package com.afsh.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host checks for the scroll-sensitive content-font range scanner. */
class ContentFontsTest {

    @Test
    fun emptyAndLatinHaveNoPersianRanges() {
        assertEquals(emptyList<IntRange>(), persianFontRanges(""))
        assertEquals(emptyList<IntRange>(), persianFontRanges("Latin message only"))
        assertEquals(emptyList<IntRange>(), persianFontRanges("Hello 123 https://example.com"))
    }

    @Test
    fun purePersianMergesAcrossSpaces() {
        val text = "سلام فردا ساعت سه"
        val ranges = persianFontRanges(text)
        assertEquals(listOf(0 until text.length), ranges)
    }

    @Test
    fun mixedKeepsLatinOutsidePersianSpans() {
        val text = "Hello پیام فارسی 123"
        val ranges = persianFontRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("پیام فارسی", text.substring(ranges[0]))
    }

    @Test
    fun asciiDigitsBreakPersianMerge() {
        val text = "مبلغ 123 تومان"
        val ranges = persianFontRanges(text)
        assertEquals(2, ranges.size)
        assertEquals("مبلغ", text.substring(ranges[0]))
        assertEquals("تومان", text.substring(ranges[1]))
    }

    @Test
    fun joiningMarksStayInsideOneRun() {
        val text = "می‌روم"
        val ranges = persianFontRanges(text)
        assertEquals(1, ranges.size)
        assertEquals(text, text.substring(ranges[0]))
        assertTrue(ranges[0].last - ranges[0].first + 1 == text.length)
    }
}

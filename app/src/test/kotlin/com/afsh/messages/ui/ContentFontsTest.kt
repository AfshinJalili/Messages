package com.afsh.messages.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host checks for the scroll-sensitive content-font range scanner and typography helpers. */
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

    @Test
    fun persianDigitsAndPercentAreInArabicRange() {
        val text = "۵۵٪"
        val ranges = persianFontRanges(text)
        assertEquals(listOf(0 until text.length), ranges)
        assertTrue(hasArabicLetter("100٪"))
    }

    @Test
    fun tabularKeepsFontFamilyAndEnablesTnum() {
        val base = TextStyle(fontSize = 14.sp, lineHeight = 1.7.em)
        val tab = base.tabular()
        assertEquals("tnum", tab.fontFeatureSettings)
        assertEquals(base.fontFamily, tab.fontFamily)
    }

    @Test
    fun forPatternUsesMonospaceLtrAndAppliesPersianLeadingWhenNeeded() {
        val base = TextStyle(fontSize = 16.sp, lineHeight = 1.45.em)
        val ascii = base.forPattern("\\b\\d{4,6}\\b")
        assertEquals(FontFamily.Monospace, ascii.fontFamily)
        assertEquals(TextDirection.Ltr, ascii.textDirection)
        assertEquals(1.45.em, ascii.lineHeight)

        val persianPattern = base.forPattern("\\b(رمز|کد)\\b")
        assertEquals(FontFamily.Monospace, persianPattern.fontFamily)
        assertEquals(TextDirection.Ltr, persianPattern.textDirection)
        assertEquals(1.7.em, persianPattern.lineHeight)
    }

    @Test
    fun withPatternFontsKeepsAsciiMonospaceAndSpansPersianRuns() {
        val pattern = "\\b(رمز|کد)\\b"
        val styled = AnnotatedString(pattern).withPatternFonts()
        assertEquals(pattern, styled.text)
        assertTrue(styled.spanStyles.any { it.start == 0 && it.end == pattern.length && it.item.fontFamily == FontFamily.Monospace })
        val persianStart = pattern.indexOf("رمز")
        val persianEnd = pattern.indexOf("کد") + "کد".length
        assertTrue(styled.spanStyles.any {
            it.start <= persianStart && it.end >= persianEnd && it.item.fontFamily != null && it.item.fontFamily != FontFamily.Monospace
        })
    }

    @Test
    fun forFieldContentPreservesBaseStyleWhenEmptyAndNeverShrinksLeading() {
        val rtlBase = TextStyle(fontSize = 18.sp, lineHeight = 1.7.em)
        assertEquals(rtlBase, rtlBase.forFieldContent(""))
        assertEquals(1.7.em, rtlBase.forFieldContent("English in Persian UI").lineHeight)

        val ltrBase = TextStyle(fontSize = 18.sp, lineHeight = 1.45.em)
        assertEquals(ltrBase, ltrBase.forFieldContent(""))
        assertEquals(1.7.em, ltrBase.forFieldContent("سلام").lineHeight)
    }

    @Test
    fun persianSafeOrNullSkipsLatinAndSpansOnlyArabicRuns() {
        assertNull(persianSafeOrNull(""))
        assertNull(persianSafeOrNull("Message Mina"))
        val mixed = persianSafeOrNull("Message سارا")
        assertNotNull(mixed)
        assertEquals(1, mixed!!.spanStyles.size)
        val span = mixed.spanStyles[0]
        assertEquals("سارا", mixed.text.substring(span.start, span.end))
        assertNotNull(span.item.fontFamily)
    }
}

package com.afsh.messages.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** A name, title or number reads in its own order and still lines up with the interface around it. */
class LabelDirectionTest {
    private val base = TextStyle()

    @Test
    fun latinNameInPersianInterfaceKeepsTrailingPunctuationAtItsEnd() {
        // "BING-POT!!" laid out right to left drew "!!BING-POT".
        assertEquals(TextDirection.Content, base.withLabelDirection("BING-POT!!", LayoutDirection.Rtl).textDirection)
        assertEquals(TextDirection.Content, base.withLabelDirection("سلام!", LayoutDirection.Ltr).textDirection)
    }

    @Test
    fun letterFreeNumbersStayLeftToRight() {
        for (number in listOf("+989904528570", "555 0142", "70,000,000", "+1 555-000-0016")) {
            assertEquals(number, TextDirection.Ltr, base.withLabelDirection(number, LayoutDirection.Rtl).textDirection)
        }
    }

    @Test
    fun lineStartsAtTheInterfaceStartWhateverTheTextDirection() {
        assertEquals(TextAlign.Right, base.withLabelDirection("BING-POT!!", LayoutDirection.Rtl).textAlign)
        assertEquals(TextAlign.Right, base.withLabelDirection("+989904528570", LayoutDirection.Rtl).textAlign)
        assertEquals(TextAlign.Left, base.withLabelDirection("سلام", LayoutDirection.Ltr).textAlign)
        assertEquals(TextAlign.Right, base.copy(textAlign = TextAlign.Start).withLabelDirection("Dan", LayoutDirection.Rtl).textAlign)
    }

    @Test
    fun endMeansTheInterfaceEndAndCenterStaysCentered() {
        assertEquals(TextAlign.Left, base.copy(textAlign = TextAlign.End).withLabelDirection("Dan", LayoutDirection.Rtl).textAlign)
        assertEquals(TextAlign.Right, base.copy(textAlign = TextAlign.End).withLabelDirection("دان", LayoutDirection.Ltr).textAlign)
        assertEquals(TextAlign.Center, base.copy(textAlign = TextAlign.Center).withLabelDirection("Dan", LayoutDirection.Rtl).textAlign)
    }
}

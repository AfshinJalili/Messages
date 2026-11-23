package com.afsh.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberLabelTest {
    @Test
    fun phoneNumberKeepsItsPlusOnTheLeft() {
        assertTrue("+15550000016".isNumberLabel())
        assertTrue("+1 555-000-0016".isNumberLabel())
    }

    @Test
    fun namesStayInTheirOwnDirection() {
        assertFalse("Dan".isNumberLabel())
        assertFalse("پیام".isNumberLabel())
        assertFalse("".isNumberLabel())
    }
}

package com.afsh.messages.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoveDiacriticsTest {

    @Test
    fun `simple characters strip accents`() {
        assertEquals("Creme brulee", "Crème brûlée".removeDiacriticsIfNeeded(useSimpleCharacters = true))
    }

    @Test
    fun `text is unchanged when simple characters are off`() {
        assertEquals("Crème brûlée", "Crème brûlée".removeDiacriticsIfNeeded(useSimpleCharacters = false))
    }
}

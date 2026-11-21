package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ListDetailTest {

    @Test
    fun `a phone width expands even when the embedding rule says the constraints passed`() {
        assertEquals(ListDetailMode.Expand, listDetailMode(widthDp = 411, constraintsSatisfied = false))
        assertEquals(ListDetailMode.Expand, listDetailMode(widthDp = 411, constraintsSatisfied = true))
        assertEquals(ListDetailMode.Expand, listDetailMode(widthDp = 839, constraintsSatisfied = true))
    }

    @Test
    fun `a wide window splits only when the embedding constraints pass`() {
        assertEquals(ListDetailMode.Split, listDetailMode(widthDp = 840, constraintsSatisfied = true))
        assertEquals(ListDetailMode.Split, listDetailMode(widthDp = 1280, constraintsSatisfied = true))
        assertEquals(ListDetailMode.Expand, listDetailMode(widthDp = 1280, constraintsSatisfied = false))
    }

    @Test
    fun `the list pane stays the rail plus a 360 dp column`() {
        assertEquals(456f / 1280f, listPaneRatio(1280), 0.0001f)
        assertEquals(456f / 840f, listPaneRatio(840), 0.0001f)
        assertEquals(0.9f, listPaneRatio(400), 0.0001f)
        assertEquals(0.1f, listPaneRatio(10_000), 0.0001f)
    }
}

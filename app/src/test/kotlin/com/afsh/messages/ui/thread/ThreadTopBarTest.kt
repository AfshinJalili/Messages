package com.afsh.messages.ui.thread

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTopBarTest {
    private val one = message(1)

    @Test
    fun `the bar shows the conversation by default`() {
        assertEquals(ThreadTopBarMode.CONVERSATION, ThreadUiState().topBarMode)
    }

    @Test
    fun `search replaces the contact`() {
        assertEquals(ThreadTopBarMode.SEARCH, ThreadUiState(searching = true).topBarMode)
    }

    @Test
    fun `selection wins over search`() {
        val state = ThreadUiState(items = listOf(one), selected = setOf(one.getStableId()), searching = true)
        assertEquals(ThreadTopBarMode.SELECTION, state.topBarMode)
    }

    @Test
    fun `the bar keeps its design height for one line of title and subtitle`() {
        assertEquals(72.dp, threadTopBarHeight(compact = false, title = 29.dp, subtitle = 22.dp, padding = 8.dp))
        assertEquals(56.dp, threadTopBarHeight(compact = true, title = 29.dp, subtitle = null, padding = 8.dp))
    }

    @Test
    fun `the bar grows with a large font so the name is never clipped`() {
        assertEquals(126.dp, threadTopBarHeight(compact = false, title = 64.dp, subtitle = 46.dp, padding = 8.dp))
        assertEquals(80.dp, threadTopBarHeight(compact = true, title = 64.dp, subtitle = null, padding = 8.dp))
    }

    @Test
    fun `the avatar sets the floor when the text is short`() {
        assertEquals(72.dp, threadTopBarHeight(compact = false, title = 10.dp, subtitle = null, padding = 8.dp))
        assertEquals(60.dp, threadTopBarHeight(compact = true, title = 10.dp, subtitle = null, padding = 8.dp))
    }

    @Test
    fun `only delete and block are drawn as destructive`() {
        assertEquals(setOf(ThreadMenuAction.DELETE, ThreadMenuAction.BLOCK), ThreadMenuAction.entries.filter { it.destructive }.toSet())
    }
}

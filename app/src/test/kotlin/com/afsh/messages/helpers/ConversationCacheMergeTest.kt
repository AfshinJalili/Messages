package com.afsh.messages.helpers

import com.afsh.messages.models.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCacheMergeTest {

    private fun conversation(
        date: Int = 1_000,
        snippet: String = "Old message",
        read: Boolean = true,
        unreadCount: Int = 0,
        messageCount: Int = 5,
        title: String = "Mina",
        usesCustomTitle: Boolean = false,
        isScheduled: Boolean = false,
        previewSnippet: String? = null,
        previewDate: Int? = null,
        allSpam: Boolean = false,
    ) = Conversation(
        threadId = 42L,
        snippet = snippet,
        date = date,
        read = read,
        title = title,
        photoUri = "",
        isGroupConversation = false,
        phoneNumber = "+15550100",
        isScheduled = isScheduled,
        usesCustomTitle = usesCustomTitle,
        isArchived = false,
        unreadCount = unreadCount,
        messageCount = messageCount,
        previewSnippet = previewSnippet,
        previewDate = previewDate,
        allSpam = allSpam,
    )

    @Test
    fun `incoming message on a previously read conversation surfaces unread state and count immediately`() {
        val cached = conversation(date = 1_000, snippet = "Old", read = true, unreadCount = 0, messageCount = 5)
        val incoming = conversation(date = 1_010, snippet = "New message", read = false, unreadCount = 1, messageCount = 6)

        val merged = mergeConversationWithCache(incoming, cached)

        assertFalse(merged.read)
        assertEquals(1, merged.unreadCount)
        assertEquals(1_010, merged.date)
        assertEquals("New message", merged.snippet)
    }

    @Test
    fun `same-second incoming message with incremented message count surfaces unread state immediately`() {
        val cached = conversation(date = 1_000, snippet = "Sent", read = true, unreadCount = 0, messageCount = 5)
        val incoming = conversation(date = 1_000, snippet = "Reply", read = false, unreadCount = 1, messageCount = 6)

        val merged = mergeConversationWithCache(incoming, cached)

        assertFalse(merged.read)
        assertEquals(1, merged.unreadCount)
    }

    @Test
    fun `stale provider reconcile snapshot does not undo a local mark-read`() {
        val cached = conversation(date = 1_000, snippet = "Already read", read = true, unreadCount = 0, messageCount = 5)
        val staleProvider = conversation(date = 1_000, snippet = "Already read", read = false, unreadCount = 2, messageCount = 5)

        val merged = mergeConversationWithCache(staleProvider, cached)

        assertTrue(merged.read)
        assertEquals(0, merged.unreadCount)
    }

    @Test
    fun `older optimistic send bump keeps local preview and preserves read state`() {
        val cached = conversation(date = 2_000, snippet = "Just sent", read = true, unreadCount = 0, messageCount = 5)
        val laggingProvider = conversation(date = 1_500, snippet = "Earlier", read = false, unreadCount = 1, messageCount = 5)

        val merged = mergeConversationWithCache(laggingProvider, cached)

        assertEquals(2_000, merged.date)
        assertEquals("Just sent", merged.snippet)
        assertTrue(merged.read)
        assertEquals(0, merged.unreadCount)
    }

    @Test
    fun `custom title and denormalized preview columns survive merge`() {
        val cached = conversation(
            title = "Team Chat",
            usesCustomTitle = true,
            previewSnippet = "Cached preview",
            previewDate = 1_000,
            allSpam = false,
        )
        val incoming = conversation(
            date = 1_010,
            snippet = "Hello",
            read = false,
            unreadCount = 2,
            messageCount = 6,
            title = "+15550100",
        )

        val merged = mergeConversationWithCache(incoming, cached)

        assertEquals("Team Chat", merged.title)
        assertTrue(merged.usesCustomTitle)
        assertEquals("Cached preview", merged.previewSnippet)
        assertEquals(1_000, merged.previewDate)
        assertFalse(merged.read)
        assertEquals(2, merged.unreadCount)
    }
}

package com.afsh.messages.ui.thread

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Link detection and the linked text a bubble shows, with a stand-in for the platform URL scanner. */
class MessageLinkifierTest {
    // Stands in for Android's Linkify: any https:// run up to the next space is a web link.
    private val urls: (String) -> List<DetectedLink> = { body ->
        Regex("https://\\S+").findAll(body).map { DetectedLink(it.range.first, it.range.last + 1, it.value, isUrl = true) }.toList()
    }
    private var scans = 0
    private val countingUrls: (String) -> List<DetectedLink> = { scans++; urls(it) }

    private fun numbers(body: String) = detectLinks(body, urls).filterNot { it.isUrl }.map { it.value }

    @Test
    fun `plain words have no links and skip the url scan`() {
        assertEquals(emptyList<DetectedLink>(), detectLinks("A plain recycled message", countingUrls))
        assertEquals(emptyList<DetectedLink>(), detectLinks("", countingUrls))
        assertEquals(0, scans)
    }

    @Test
    fun `digits alone skip the url scan`() {
        assertEquals(listOf("123456"), detectLinks("Code 123456 here", countingUrls).map { it.value })
        assertEquals(0, scans)
    }

    @Test
    fun `a standalone number is a copy link`() {
        assertEquals(listOf(DetectedLink(4, 10, "123456", isUrl = false)), detectLinks("Use 123456 here", urls))
    }

    @Test
    fun `a spaced phone number is one link`() {
        assertEquals(listOf("+98 912 345 6789"), numbers("call +98 912 345 6789 now"))
    }

    @Test
    fun `grouped amounts and times stay whole`() {
        assertEquals(listOf("70,000,000", "13:25"), numbers("مبلغ: 70,000,000 ساعت 13:25."))
        assertEquals(listOf("۱۲۳۴۵۶"), numbers("کد ۱۲۳۴۵۶، تمام"))
    }

    @Test
    fun `a number glued to a word is not linked at all`() {
        assertEquals(emptyList<String>(), numbers("مانده70,000,000 ریال"))
        assertEquals(emptyList<String>(), numbers("abc123"))
    }

    @Test
    fun `numbers inside a url belong to the url`() {
        val links = detectLinks("visit https://example.com/123?q=45 or 99", urls)
        assertEquals(listOf("https://example.com/123?q=45" to true, "99" to false), links.map { it.value to it.isUrl })
    }

    @Test
    fun `linked text isolates each link left to right and keeps the rest`() {
        val body = "Code 4829 at https://example.com ok"
        val text = buildLinkifiedText(body, detectLinks(body, urls)) {}
        assertEquals("Code ⁦4829⁩ at ⁦https://example.com⁩ ok", text.text)
        val annotations = text.getLinkAnnotations(0, text.length).map { it.item }
        assertEquals(2, annotations.size)
        assertEquals("4829", (annotations[0] as LinkAnnotation.Clickable).tag)
        assertEquals("https://example.com", (annotations[1] as LinkAnnotation.Url).url)
    }

    @Test
    fun `tapping a number copies it`() {
        val copied = mutableListOf<String>()
        val body = "Code 4829"
        val text = buildLinkifiedText(body, detectLinks(body, urls)) { copied += it }
        val clickable = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        clickable.linkInteractionListener!!.onClick(clickable)
        assertEquals(listOf("4829"), copied)
    }

    @Test
    fun `no links leaves the text unchanged`() {
        val text = buildLinkifiedText("hello", emptyList()) {}
        assertEquals("hello", text.text)
        assertTrue(text.getLinkAnnotations(0, text.length).isEmpty())
    }
}

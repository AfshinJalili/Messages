package com.afsh.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.PageAppBar
import com.afsh.messages.ui.components.SettingItem
import com.afsh.messages.ui.components.SnippetText
import com.afsh.messages.ui.ltr
import com.afsh.messages.ui.thread.MessageText
import com.afsh.messages.ui.thread.linkify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** #65: a grouped number keeps its groups in order next to Persian letters. Renders only; no provider access. */
@RunWith(AndroidJUnit4::class)
class NumberOrderChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepSyntheticActivityVisible() {
        compose.activityRule.scenario.onActivity { it.keepOnScreen() }
    }

    private val samples = listOf(
        "مانده70,000,000",
        "مانده 70,000,000",
        "مبلغ1,250ریال",
        "مبلغ1,250,000ریال",
        "برداشت70,000,000 مانده1,234,567",
        "مانده۷۰,۰۰۰,۰۰۰",
        "مانده٧٠٬٠٠٠٬٠٠٠",
        "مبلغ۱٬۲۵۰٬۰۰۰ریال",
        "مبلغ ۱٬۲۵۰٬۰۰۰",
        "مبلغ۱٬۲۵۰٫۵۰ریال",
        "مبلغ1.250.000ریال",
    )
    private val directions = listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)

    /** Every digit of a grouped number sits right of the digit before it, whatever runs or isolates surround it. */
    private fun assertInOrder(where: String, text: String, left: (Int) -> Float) {
        GROUPED.findAll(text).forEach { number ->
            val xs = number.range.filter { text[it].isDigit() }.map(left)
            assertTrue("$where: \"${number.value}\" in \"$text\" drawn at $xs", xs.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test
    fun bubbleAndInboxKeepGroupsInOrder() {
        compose.setContent {
            AfshTheme(dark = false) {
                Column(Modifier.wrapContentHeight(unbounded = true)) {
                    directions.forEach { direction ->
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            samples.forEachIndexed { i, body ->
                                Box(Modifier.width(360.dp).testTag("bubble-$direction-$i")) {
                                    MessageText(body, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, links = true) {}
                                }
                                Box(Modifier.width(360.dp).testTag("inbox-$direction-$i")) {
                                    SnippetText(inboxRow(fixtureConversation(i.toLong(), "KESHAVARZI", snippet = body, phone = "KESHAVARZI")), unread = false)
                                }
                            }
                        }
                    }
                }
            }
        }
        for (direction in directions) for (i in samples.indices) for (view in listOf("bubble", "inbox")) {
            val layout = compose.onNodeWithTag("$view-$direction-$i").onChild().textLayout()
            // Selection paths follow drawn runs; getBoundingBox can invert a box at a font/bidi boundary.
            assertInOrder("$view $direction", layout.layoutInput.text.text) { layout.getPathForRange(it, it + 1).getBounds().left }
        }
    }

    @Test
    fun groupedNumbersAreLinkedAsAWholeOrNotAtAll() {
        for (separator in listOf(",", ".", "٬", "٫")) {
            val number = "70${separator}000${separator}000"
            for (body in listOf("مانده$number", "${number}ریال", "مبلغ${number}ریال")) {
                val text = linkify(body) {}
                assertEquals("A glued number must not be partially isolated", body, text.text)
                assertTrue(text.getLinkAnnotations(0, text.length).isEmpty())
            }
            val copied = mutableListOf<String>()
            val text = linkify("مانده $number ریال") { copied += it }
            val link = text.getLinkAnnotations(0, text.length).single()
            assertEquals(number, text.substring(link.start, link.end))
            val annotation = link.item as LinkAnnotation.Clickable
            annotation.linkInteractionListener!!.onClick(annotation)
            assertEquals(listOf(number), copied)
        }
    }

    /**
     * A Latin name in the Persian interface keeps its "!!" after its last letter wherever the app shows it. Compares
     * drawn pixels with the same component given left-to-right isolates of the right and the wrong order: the
     * semantics text layout of a plain string skips the layout-direction resolution that drawing applies, so glyph
     * positions read from it miss this bug.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun latinNameKeepsTrailingPunctuationInPersian() {
        val name = "BING-POT!!"
        // The subject, then how it must look, then how the bug drew it.
        val shown = listOf(name, ltr(name), ltr("!!BING-POT"))
        val places: Map<String, @Composable (String) -> Unit> = mapOf(
            "bar" to { PageAppBar(title = it) },
            "row" to { SettingItem("عنوان گفتگو", value = it) },
            "content" to { ContentText(it, Modifier.fillMaxWidth()) },
        )
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AfshTheme(dark = false) {
                    Column(Modifier.width(360.dp)) {
                        places.forEach { (place, content) ->
                            shown.forEachIndexed { i, text -> Box(Modifier.testTag("$place-$i")) { content(text) } }
                        }
                    }
                }
            }
        }
        for (place in places.keys) {
            val (subject, right, wrong) = shown.indices.map { i ->
                compose.onNode(hasText(shown[i]) and hasAnyAncestor(hasTestTag("$place-$i")), useUnmergedTree = true).captureToImage().toPixelMap()
            }
            val toRight = subject.distanceTo(right)
            val toWrong = subject.distanceTo(wrong)
            assertTrue("$place: \"$name\" drawn out of order ($toRight from right, $toWrong from wrong)", toRight * 3 < toWrong)
        }
    }

    /** Summed brightness difference at the best horizontal shift of up to 2px, which absorbs sub-pixel placement. */
    private fun PixelMap.distanceTo(other: PixelMap): Float {
        fun PixelMap.brightness(x: Int, y: Int): Float =
            if (x in 0 until width && y in 0 until height) this[x, y].let { it.red + it.green + it.blue } else this[0, 0].let { it.red + it.green + it.blue }
        return (-2..2).minOf { dx ->
            var sum = 0f
            for (x in 0 until width) for (y in 0 until height) sum += abs(brightness(x, y) - other.brightness(x + dx, y))
            sum
        }
    }

    private companion object {
        // Bidi isolates may sit inside a number: the bug wrapped "000,000" of "70,000,000" in one.
        val GROUPED = Regex("\\p{Nd}+(?:[\u2066-\u2069]*[,٫٬.][\u2066-\u2069]*\\p{Nd}+)+")
    }
}

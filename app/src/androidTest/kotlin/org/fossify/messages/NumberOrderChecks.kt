package org.fossify.messages

import android.content.res.Configuration
import android.graphics.Path
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.SnippetText
import org.fossify.messages.ui.thread.MessageText
import org.fossify.messages.ui.thread.linkify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** #65: a grouped number keeps its groups in order next to Persian letters. Renders only; no provider access. */
@RunWith(AndroidJUnit4::class)
class NumberOrderChecks {
    @get:Rule
    val compose = createComposeRule()

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
            OpenLineTheme(dark = false) {
                Column(Modifier.wrapContentHeight(unbounded = true)) {
                    directions.forEach { direction ->
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            samples.forEachIndexed { i, body ->
                                Box(Modifier.width(360.dp).testTag("bubble-$direction-$i")) {
                                    MessageText(body, Color.Black, Color.Blue, links = true) {}
                                }
                                Box(Modifier.width(360.dp).testTag("inbox-$direction-$i")) {
                                    SnippetText(InboxRow(Conversation(i.toLong(), body, 1, true, "KESHAVARZI", "", false, "KESHAVARZI"), draft = null, pinned = false, muted = false), unread = false)
                                }
                            }
                        }
                    }
                }
            }
        }
        for (direction in directions) for (i in samples.indices) for (view in listOf("bubble", "inbox")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("$view-$direction-$i").onChild().fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertInOrder("$view $direction", layout.layoutInput.text.text) { layout.getBoundingBox(it).left }
        }
    }

    /** Archive and Recycle Bin still draw rows with item_conversation.xml. */
    @Test
    fun viewRowKeepsGroupsInOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (rtl in listOf(false, true)) for (body in samples) {
                val configuration = Configuration(context.resources.configuration).apply { setLayoutDirection(Locale(if (rtl) "fa" else "en")) }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.AppTheme)
                val binding = ItemConversationBinding.inflate(LayoutInflater.from(themed))
                binding.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                binding.conversationBodyShort.text = body
                val width = (360 * themed.resources.displayMetrics.density).toInt()
                binding.root.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                binding.root.layout(0, 0, width, binding.root.measuredHeight)
                val layout = binding.conversationBodyShort.layout
                val path = Path()
                val bounds = RectF()
                assertInOrder("view row rtl=$rtl", body) { offset ->
                    path.reset()
                    layout.getSelectionPath(offset, offset + 1, path)
                    path.computeBounds(bounds, true)
                    bounds.left
                }
            }
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

    private companion object {
        // Bidi isolates may sit inside a number: the bug wrapped "000,000" of "70,000,000" in one.
        val GROUPED = Regex("\\p{Nd}+(?:[\u2066-\u2069]*[,٫٬.][\u2066-\u2069]*\\p{Nd}+)+")
    }
}

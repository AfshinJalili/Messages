package org.fossify.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.SnippetText
import org.fossify.messages.ui.thread.MessageText
import org.fossify.messages.ui.thread.COMPOSER_FIELD_TAG
import org.fossify.messages.ui.thread.ComposerState
import org.fossify.messages.ui.thread.ThreadComposer
import org.fossify.messages.ui.thread.linkify
import org.fossify.messages.ui.withContentFonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic Compose content only: no messages, preferences, or app/system locale changes. */
@RunWith(AndroidJUnit4::class)
class ContentFontChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepSyntheticActivityVisible() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private val samples = listOf(
        "پیام فارسی با پ چ ژ گ ک ی",
        "می\u200Cروم سَلَام کشـیده، ۱۲۳",
        "Hello پیام فارسی https://example.com 123",
        "Persian پیام\nخط دوم فارسی",
        "Latin message only",
    )

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).onChild().fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    @Test
    fun bubblesPreviewsAndDraftsKeepMetricsAcrossUiDirectionsAndScales() {
        compose.setContent {
            val density = LocalDensity.current
            Column(Modifier.wrapContentHeight(unbounded = true)) {
                for (scale in listOf(1f, 1.6f)) for (direction in LayoutDirection.entries) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, scale),
                        LocalLayoutDirection provides direction,
                    ) {
                        OpenLineTheme(dark = false, textScale = 1.2f) {
                            samples.forEachIndexed { i, body ->
                                Box(Modifier.width(280.dp).testTag("bubble-$scale-$direction-$i")) {
                                    MessageText(body, Color.Black, Color.Blue, links = true) {}
                                }
                                for (draft in listOf(false, true)) {
                                    Box(Modifier.width(280.dp).testTag("preview-$draft-$scale-$direction-$i")) {
                                        SnippetText(InboxRow(
                                            Conversation(i.toLong(), body, 1, true, "Synthetic", "", false, "Synthetic"),
                                            draft = if (draft) body else null, pinned = false, muted = false,
                                        ), unread = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.6f)) for (i in samples.indices) {
            // Draft labels are localized UI; compare their content fonts separately below.
            for (surface in listOf("bubble", "preview-false")) {
                val ltr = layout("$surface-$scale-Ltr-$i")
                val rtl = layout("$surface-$scale-Rtl-$i")
                assertEquals(ltr.layoutInput.style.lineHeight, rtl.layoutInput.style.lineHeight)
                assertEquals(ltr.lineCount, rtl.lineCount)
                assertEquals(ltr.size.height, rtl.size.height)
                for (offset in ltr.layoutInput.text.indices.filter { ltr.layoutInput.text[it] != '\n' }) {
                    assertEquals("$surface sample $i offset $offset", ltr.getPathForRange(offset, offset + 1).getBounds().width,
                        rtl.getPathForRange(offset, offset + 1).getBounds().width, 0.1f)
                }
                if (surface == "bubble") assertFalse(ltr.hasVisualOverflow)
            }
            for (direction in LayoutDirection.entries) {
                val draft = layout("preview-true-$scale-$direction-$i")
                assertTrue(draft.layoutInput.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
                assertEquals(if (i == samples.lastIndex) 1.45.em else 1.7.em, draft.layoutInput.style.lineHeight)
            }
        }
        assertTrue(layout("bubble-1.6-Ltr-0").size.height > layout("bubble-1.0-Ltr-0").size.height)
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    @Test
    fun persianGlyphMetricsMatchBundledVazirmatnAtNormalAndBoldWeights() {
        compose.setContent {
            val measurer = rememberTextMeasurer()
            for (direction in LayoutDirection.entries) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    OpenLineTheme(dark = false) {
                        for (weight in listOf(FontWeight.Normal, FontWeight.Bold)) {
                            val text = AnnotatedString(samples[0])
                            val style = MaterialTheme.typography.bodyLarge.forContent(text).copy(fontWeight = weight)
                            val actual = measurer.measure(text.withContentFonts(), style)
                            val expected = measurer.measure(text, style.copy(fontFamily = FontFamily(
                                Font(R.font.vazirmatn, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))),
                            )))
                            for (i in text.indices.filter { text[it] != ' ' }) {
                                assertEquals(expected.getPathForRange(i, i + 1).getBounds().width, actual.getPathForRange(i, i + 1).getBounds().width, 0.1f)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun scriptSpansPreserveJoiningMarksLinksAndBoldWithoutChangingText() {
        val linked = linkify("Hello می\u200Cروم سَلَام کشـیده، https://example.com 123") {}
        val original = buildAnnotatedString {
            append(linked)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 6, 13)
        }
        val styled = original.withContentFonts()
        assertEquals(original.text, styled.text)
        assertEquals(original.getLinkAnnotations(0, original.length), styled.getLinkAnnotations(0, styled.length))
        assertTrue(styled.spanStyles.containsAll(original.spanStyles))
        val base = styled.spanStyles.first { it.item.fontFamily != null }.item.fontFamily
        for (sample in listOf("می\u200Cروم", "سَلَام", "کشـیده،")) {
            val start = styled.text.indexOf(sample)
            assertTrue(styled.spanStyles.any {
                it.start <= start && it.end >= start + sample.length && it.item.fontFamily != null && it.item.fontFamily != base
            })
        }
        assertEquals(1.7.em, TextStyle().forContent("فارسی").lineHeight)
        assertEquals(1.45.em, TextStyle().forContent("English").lineHeight)
        assertEquals("", AnnotatedString("").withContentFonts().text)
    }

    @Test
    fun composerKeepsEditableTextAndScriptFontsAcrossUiDirections() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val value = mutableStateOf(TextFieldValue())
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                OpenLineTheme(dark = false) {
                    ThreadComposer(ComposerState(), "", value.value, sendOnEnter = false,
                        onTextChange = { value.value = it }, onEvent = { error("Unexpected composer action: $it") })
                }
            }
        }
        val sample = "Hello می\u200Cروم سَلَام 123"
        for (uiDirection in LayoutDirection.entries) {
            compose.runOnIdle { direction.value = uiDirection }
            compose.onNodeWithTag(COMPOSER_FIELD_TAG).performTextReplacement(sample)
            compose.runOnIdle {
                assertEquals(sample, value.value.text)
                assertEquals(sample.length, value.value.selection.end)
            }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(COMPOSER_FIELD_TAG).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val result = layouts.single()
            assertEquals(sample, result.layoutInput.text.text)
            assertEquals(1.7.em, result.layoutInput.style.lineHeight)
            assertEquals(AnnotatedString(sample).withContentFonts().spanStyles, result.layoutInput.text.spanStyles)
        }
    }
}

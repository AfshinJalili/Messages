package com.afsh.messages.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.afsh.messages.ui.lineHeightSp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [fieldMinHeightFor] is the natural one-line OutlinedTextField height: line box + Material's
 * unlabeled vertical content padding (16+16), floored at 56.dp. Font scale grows only the line
 * box — not the padding — so multi-line fields keep no wasted bottom slack.
 *
 * Line-box dp at a given scale is `18sp * leadingEm * fontScale` under linear scaling (density 1).
 * [fieldMinHeight] applies that conversion through [androidx.compose.ui.unit.Density]; these tests
 * pin the pure height rule itself.
 */
class FieldMinHeightTest {
    private val verticalPadding = 32.dp
    private val floor = 56.dp
    private val bodySp = 18f

    private fun heightAt(leadingEm: Float, fontScale: Float): androidx.compose.ui.unit.Dp {
        val lineHeight = (bodySp * leadingEm * fontScale).dp
        return fieldMinHeightFor(lineHeight = lineHeight, verticalPadding = verticalPadding, floor = floor)
    }

    @Test
    fun `LTR 1_45em equals max of 56dp and line height plus 32dp`() {
        assertEquals(58.1.dp, heightAt(1.45f, 1.0f))
        assertEquals(65.93.dp, heightAt(1.45f, 1.3f))
        assertEquals(71.15.dp, heightAt(1.45f, 1.5f))
        assertEquals(84.2.dp, heightAt(1.45f, 2.0f))
    }

    @Test
    fun `RTL 1_7em equals max of 56dp and line height plus 32dp`() {
        assertEquals(62.6.dp, heightAt(1.7f, 1.0f))
        assertEquals(71.78.dp, heightAt(1.7f, 1.3f))
        assertEquals(77.9.dp, heightAt(1.7f, 1.5f))
        assertEquals(93.2.dp, heightAt(1.7f, 2.0f))
    }

    @Test
    fun `large font scale no longer wastes the old scaled padding`() {
        // Former FieldMin * fontScale at 2.0x was 112.dp; natural height leaves no bottom slack.
        assertEquals(84.2.dp, heightAt(1.45f, 2.0f))
        assertEquals(93.2.dp, heightAt(1.7f, 2.0f))
        assertEquals(27.8f, 112f - heightAt(1.45f, 2.0f).value, 0.01f)
        assertEquals(18.8f, 112f - heightAt(1.7f, 2.0f).value, 0.01f)
    }

    @Test
    fun `em line height converts through font size never as raw em`() {
        val style = TextStyle(fontSize = 18.sp, lineHeight = 1.45.em)
        assertEquals(26.1.sp, style.lineHeightSp())
        assertEquals(30.6.sp, TextStyle(fontSize = 18.sp, lineHeight = 1.7.em).lineHeightSp())
        assertEquals(18.sp, TextStyle(fontSize = 18.sp).lineHeightSp())
    }

    @Test
    fun `floor holds when the line box is shorter than 56dp`() {
        assertEquals(56.dp, fieldMinHeightFor(lineHeight = 20.dp, verticalPadding = 32.dp))
        assertEquals(60.dp, fieldMinHeightFor(lineHeight = 28.dp, verticalPadding = 32.dp))
    }
}

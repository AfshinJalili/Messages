package com.afsh.messages.ui.applock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 3×3 pattern lock grid. Drag to connect dots (0-8 in reading order). [onPatternComplete] receives
 * the SHA-1 hash when the user lifts their finger. [state] shows correct (green), wrong (red), or
 * default. [enabled] disables input during lockout or wrong-pattern delay.
 */
@Composable
fun PatternInput(
    onPatternComplete: (hash: String) -> Unit,
    state: PatternState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val selectedDots = remember { mutableStateListOf<Int>() }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }
    val haptic = LocalHapticFeedback.current

    val lineColor = when (state) {
        PatternState.Correct -> primary
        PatternState.Wrong -> error
        PatternState.Default -> primary
    }
    val dotColor = onSurface
    val dotRadius = with(LocalDensity.current) { 12.dp.toPx() }

    Box(modifier.fillMaxWidth().aspectRatio(1f).padding(Afsh.spacing.xl).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(
            onDragStart = { offset ->
                selectedDots.clear()
                currentOffset = offset
                findDot(offset, size.width.toFloat())?.let {
                    selectedDots.add(it)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { _, offset ->
                currentOffset = currentOffset?.plus(offset)
                currentOffset?.let { curr ->
                    findDot(curr, size.width.toFloat())?.let { dot ->
                        if (dot !in selectedDots) {
                            selectedDots.add(dot)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            },
            onDragEnd = {
                currentOffset = null
                if (selectedDots.size >= 4) {
                    onPatternComplete(patternToHash(selectedDots))
                }
                selectedDots.clear()
            },
            onDragCancel = {
                currentOffset = null
                selectedDots.clear()
            },
        )
    }) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val positions = (0..8).map { i ->
                val row = i / 3
                val col = i % 3
                Offset(width * (col + 1) / 4f, width * (row + 1) / 4f)
            }

            // Draw lines between selected dots
            for (i in 0 until selectedDots.size - 1) {
                drawLine(lineColor, positions[selectedDots[i]], positions[selectedDots[i + 1]], strokeWidth = 8f, cap = StrokeCap.Round)
            }
            // Draw line to current drag position
            if (selectedDots.isNotEmpty()) {
                currentOffset?.let { curr ->
                    drawLine(lineColor, positions[selectedDots.last()], curr, strokeWidth = 8f, cap = StrokeCap.Round)
                }
            }

            // Draw all dots
            positions.forEachIndexed { i, pos ->
                val filled = i in selectedDots
                if (filled) {
                    drawCircle(lineColor, dotRadius * 1.5f, pos)
                } else {
                    drawCircle(dotColor, dotRadius, pos, style = Stroke(4f))
                }
            }
        }
    }
}

enum class PatternState { Default, Correct, Wrong }

private fun findDot(offset: Offset, gridWidth: Float): Int? {
    val threshold = gridWidth / 8f
    for (i in 0..8) {
        val row = i / 3
        val col = i % 3
        val dotX = gridWidth * (col + 1) / 4f
        val dotY = gridWidth * (row + 1) / 4f
        val distance = sqrt((offset.x - dotX).pow(2) + (offset.y - dotY).pow(2))
        if (distance < threshold) return i
    }
    return null
}

private fun patternToHash(pattern: List<Int>): String {
    val string = pattern.joinToString("")
    val md = MessageDigest.getInstance("SHA-1")
    md.update(string.toByteArray())
    val digest = md.digest()
    val bigInt = BigInteger(1, digest)
    return String.format("%0${digest.size * 2}x", bigInt).lowercase()
}

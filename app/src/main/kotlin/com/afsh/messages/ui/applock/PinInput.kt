package com.afsh.messages.ui.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon

/**
 * PIN input pad with number buttons 0-9, backspace, and confirm. Displays the entered PIN as dots.
 * [pin] is the current value, [onPinChange] updates it, [onConfirm] submits, [enabled] disables during lockout.
 */
@Composable
fun PinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val feedback = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.lg)) {
        Text(
            "•".repeat(pin.length),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Afsh.spacing.xl),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm, Alignment.CenterHorizontally)) {
                listOf("1", "2", "3").forEach { number ->
                    PinButton(number, enabled) { onPinChange(pin + number); feedback() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm, Alignment.CenterHorizontally)) {
                listOf("4", "5", "6").forEach { number ->
                    PinButton(number, enabled) { onPinChange(pin + number); feedback() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm, Alignment.CenterHorizontally)) {
                listOf("7", "8", "9").forEach { number ->
                    PinButton(number, enabled) { onPinChange(pin + number); feedback() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.sm, Alignment.CenterHorizontally)) {
                PinButton("", false) {} // spacer
                PinButton("0", enabled) { onPinChange(pin + "0"); feedback() }
                FilledTonalIconButton(
                    onClick = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)); feedback() },
                    enabled = enabled && pin.isNotEmpty(),
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                ) {
                    Icon(AfshIcon.Delete.painter(), contentDescription = null)
                }
            }
        }

        FilledTonalIconButton(
            onClick = { onConfirm(); feedback() },
            enabled = enabled && pin.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(0.6f).padding(top = Afsh.spacing.md),
        ) {
            Icon(AfshIcon.Check.painter(), stringResource(android.R.string.ok))
        }
    }
}

@Composable
private fun PinButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    if (label.isEmpty()) {
        // Spacer to keep grid alignment
        FilledTonalIconButton({}, enabled = false, modifier = Modifier.weight(1f).aspectRatio(1f), colors = IconButtonDefaults.filledTonalIconButtonColors(disabledContainerColor = MaterialTheme.colorScheme.surface)) {
            Text("")
        }
    } else {
        FilledTonalIconButton(onClick, enabled, Modifier.weight(1f).aspectRatio(1f)) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.afsh.messages.ui.Afsh

/**
 * Component / text field, on the stock Material 3 filled [TextField]: a surfaceContainer slab with the
 * value in title type. [label] is the field's Material label inside the slab, uppercase as the design's
 * caption; leave it null when the page labels the field itself. The indicator is the design's
 * `focus-ring` (pine, lime in dark) while focused, and the error colour when [isError] (42).
 */
@Composable
fun TextFieldSlab(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    accessibilityLabel: String? = label ?: placeholder,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    isError: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { accessibilityLabel?.let { contentDescription = it } },
        textStyle = textStyle,
        label = label?.let { { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium) } },
        placeholder = placeholder?.let { { Text(it, style = textStyle.copy(fontWeight = FontWeight.Normal)) } },
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(containerColor),
    )
}

/**
 * The colours of every shared field: one [containerColor] in every state, no indicator at rest, the
 * design's focus ring as the focused indicator, and the error colour for an error.
 */
@Composable
fun afshFieldColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainer): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor,
        errorContainerColor = containerColor,
        focusedIndicatorColor = Afsh.colors.focusRing,
        unfocusedIndicatorColor = Afsh.colors.transparent,
        disabledIndicatorColor = Afsh.colors.transparent,
        errorIndicatorColor = colors.error,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        cursorColor = colors.primary,
        focusedLabelColor = colors.onSurfaceVariant,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedPlaceholderColor = colors.onSurfaceVariant,
        unfocusedPlaceholderColor = colors.onSurfaceVariant,
        focusedLeadingIconColor = colors.onSurfaceVariant,
        unfocusedLeadingIconColor = colors.onSurfaceVariant,
    )
}

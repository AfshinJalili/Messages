package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.ContentFontVisualTransformation
import com.afsh.messages.ui.forFieldContent
import com.afsh.messages.ui.lineHeightSp

private val FieldMin = 56.dp

/**
 * Natural one-line [OutlinedTextField] height: the [bodyLarge][MaterialTheme.typography] line box
 * plus Material's unlabeled vertical content padding, never below the 56 dp touch floor.
 *
 * Pure so JVM tests can pin the math without Compose. Do not scale [verticalPadding] by font scale —
 * only the line box grows with text size; multiplying the whole former 56.dp floor left multi-line
 * fields with dead space under a top-aligned cursor.
 */
fun fieldMinHeightFor(lineHeight: Dp, verticalPadding: Dp, floor: Dp = FieldMin): Dp =
    maxOf(floor, lineHeight + verticalPadding)

/** One line of a field at the current text size. A button beside a field uses this for its height. */
@Composable
fun fieldMinHeight(): Dp {
    val lineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeightSp().toDp()
    }
    // Unlabeled Material default: 16.dp top + 16.dp bottom. Prefer the API over a 32.dp literal.
    val padding = OutlinedTextFieldDefaults.contentPadding()
    return fieldMinHeightFor(
        lineHeight = lineHeight,
        verticalPadding = padding.calculateTopPadding() + padding.calculateBottomPadding(),
    )
}

/**
 * Component / text field, on the stock Material 3 [OutlinedTextField]: a filled slab in the medium shape.
 * The stroke is [secondaryContainer] at rest, the focus ring while focused, and the error colour when
 * [isError]. [label] is the field's Material label, uppercase as the design's caption; leave it null when
 * the page labels the field itself.
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
    visualTransformation: VisualTransformation = ContentFontVisualTransformation,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    isError: Boolean = false,
) {
    val fieldStyle = remember(value, textStyle) { textStyle.forFieldContent(value) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = fieldMinHeight())
            .semantics { accessibilityLabel?.let { contentDescription = it } },
        textStyle = fieldStyle,
        label = label?.let { { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium) } },
        placeholder = placeholder?.let { { Text(it, style = textStyle.copy(fontWeight = FontWeight.Normal)) } },
        visualTransformation = visualTransformation,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(containerColor),
    )
}

/**
 * The colours of every shared field: one [containerColor] in every state. The stroke is the quiet
 * secondary container at rest, the focus ring while focused, and the error colour for an error.
 * Pass these to an [OutlinedTextField]; on a filled field the same colours become a bottom bar.
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
        unfocusedIndicatorColor = colors.secondaryContainer,
        disabledIndicatorColor = colors.secondaryContainer,
        errorIndicatorColor = colors.error,
        focusedTrailingIconColor = colors.onSurfaceVariant,
        unfocusedTrailingIconColor = colors.onSurfaceVariant,
        errorTrailingIconColor = colors.onSurfaceVariant,
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

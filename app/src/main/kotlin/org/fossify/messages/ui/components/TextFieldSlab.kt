package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.ui.OpenLine

private val FieldMinHeight = 56.dp
private val FocusRingWidth = 2.dp

/**
 * Component / text field: a white slab with the value in title type. [label] sits inside the slab
 * as the design's uppercase caption; leave it null when the page labels the field itself. The
 * focus ring is the design's `focus-ring` (pine, lime in dark).
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
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .border(
                FocusRingWidth,
                if (focused) OpenLine.colors.focusRing else colors.surfaceContainer,
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label != null) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { accessibilityLabel?.let { contentDescription = it } },
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = style.copy(color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal),
                        )
                    }
                    field()
                }
            },
        )
    }
}

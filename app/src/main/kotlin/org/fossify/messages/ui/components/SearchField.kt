package org.fossify.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine

private val SearchFieldHeight = 56.dp
private val SearchIconSize = 24.dp
private val FocusRingWidth = 2.dp

/**
 * Component / search field: a search glyph, the query in body-sm, and a clear button once there is
 * text. The design's focus ring shows while it has focus.
 */
@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SearchFieldHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .border(FocusRingWidth, if (focused) OpenLine.colors.focusRing else colors.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_lucide_search),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(SearchIconSize),
        )
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp)
                .semantics { contentDescription = hint },
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            val clear = stringResource(R.string.search_clear)
            IconButton(onClick = { onQuery("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = null, tint = colors.onSurfaceVariant)
            }
        } else {
            Box(Modifier.size(4.dp))
        }
    }
}

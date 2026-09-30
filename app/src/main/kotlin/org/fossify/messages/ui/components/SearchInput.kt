package org.fossify.messages.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.fossify.messages.R

@Composable
fun SearchInput(
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.inbox_search_hint)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        border = BorderStroke(if (focused) 2.dp else 1.dp,
            if (focused) org.fossify.messages.ui.OpenLine.colors.focusRing else colors.outline),
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_ol_arrow_left),
                    stringResource(R.string.search_back),
                    tint = colors.onSurface,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f)
                    .padding(vertical = 12.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusRequester(focus)
                    .semantics { contentDescription = searchLabel },
                decorationBox = { input ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_query_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        input()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQuery("")
                    focus.requestFocus()
                    keyboard?.show()
                }) {
                    Icon(
                        painterResource(R.drawable.ic_ol_x),
                        stringResource(R.string.search_clear),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

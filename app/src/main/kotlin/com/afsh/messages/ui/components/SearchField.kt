package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.afsh.messages.R

private val SearchIconSize = 24.dp

/**
 * Component / search field, on the shared outlined field: a leading search glyph, the query in body-md,
 * the hint as its placeholder, and a clear button once there is text. The stroke comes from [afshFieldColors].
 * [leadingIcon] replaces the search glyph (for example Back on the search page). [onClear] replaces the
 * default clear that only empties the query.
 */
@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val clearQuery = onClear ?: { onQuery("") }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = fieldMinHeight())
            .semantics { contentDescription = hint },
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = leadingIcon ?: {
            Icon(AfshIcon.Search.painter(), contentDescription = null, modifier = Modifier.size(SearchIconSize))
        },
        trailingIcon = if (query.isEmpty()) null else ({
            val clear = stringResource(R.string.search_clear)
            IconButton(onClick = clearQuery, modifier = Modifier.semantics { contentDescription = clear }) {
                Icon(AfshIcon.Close.painter(), contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(),
    )
}

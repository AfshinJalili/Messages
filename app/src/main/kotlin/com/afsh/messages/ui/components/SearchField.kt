package com.afsh.messages.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.afsh.messages.R

private val SearchIconSize = 24.dp

/**
 * Component / search field, on the stock Material 3 [TextField]: a leading search glyph, the query in
 * body-md, the hint as its placeholder, and a clear button once there is text. The field's indicator
 * is the design's focus ring ([afshFieldColors]).
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
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = hint },
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_lucide_search), contentDescription = null, modifier = Modifier.size(SearchIconSize))
        },
        trailingIcon = if (query.isEmpty()) null else ({
            val clear = stringResource(R.string.search_clear)
            IconButton(onClick = { onQuery("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(),
    )
}

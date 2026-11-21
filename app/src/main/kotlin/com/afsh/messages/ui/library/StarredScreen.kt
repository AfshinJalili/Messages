package com.afsh.messages.ui.library

import com.afsh.messages.ui.Afsh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ContentDirection
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.rememberDayAndTime

private const val PREVIEW_LINES = 3

/** 18 · Starred / saved messages. */
@Composable
fun StarredScreen(
    state: StarredUiState,
    navigation: PageNavigation,
    onBack: () -> Unit,
    onOpen: (StarredMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        title = stringResource(R.string.starred_messages),
        modifier = modifier,
        onBack = onBack,
        navigation = navigation,
    ) {
        LazyColumn(
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.messages.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = AfshIcon.Star,
                        title = stringResource(R.string.no_starred_messages),
                        text = stringResource(R.string.starred_empty_text),
                    )
                }

                else -> items(state.messages, key = { "${it.isMms}:${it.messageId}" }) { message ->
                    StarredCard(message, onClick = { onOpen(message) })
                }
            }
        }
    }
}

@Composable
private fun StarredCard(message: StarredMessage, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stamp = rememberDayAndTime(message.millis)
    val starred = stringResource(R.string.starred_messages)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(message.sender, message.body, stamp, starred).joinToString(", ")
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer, contentColor = colors.onSurface),
    ) {
        Column(
            Modifier.padding(horizontal = Afsh.spacing.lg, vertical = Afsh.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ContentText(
                    message.sender,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    AfshIcon.Star.painter(filled = true),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            ContentText(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
                direction = ContentDirection.Inherit,
            )
            Text(stamp, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Preview(name = "18 · Starred")
@Composable
private fun StarredPreview() = AfshTheme(dark = false) {
    StarredScreen(
        StarredUiState(loading = false, messages = LibraryPreviewData.starred),
        LibraryPreviewData.navigation,
        onBack = {},
        onOpen = {},
    )
}

@Preview(name = "18 · Starred · empty, dark")
@Composable
private fun StarredEmptyPreview() = AfshTheme(dark = true) {
    StarredScreen(StarredUiState(loading = false), LibraryPreviewData.navigation, onBack = {}, onOpen = {})
}

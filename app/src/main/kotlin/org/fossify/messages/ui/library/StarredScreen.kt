package org.fossify.messages.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.EmptyState
import org.fossify.messages.ui.components.PageContentPadding
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.rememberDayAndTime
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.withContentFonts

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
        subtitle = stringResource(R.string.starred_subtitle),
        onBack = onBack,
        navigation = navigation,
    ) {
        LazyColumn(
            contentPadding = PageContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.messages.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_lucide_star,
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(message.sender, message.body, stamp, starred).joinToString(", ")
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                remember(message.sender) { AnnotatedString(message.sender).withContentFonts() },
                style = MaterialTheme.typography.bodyMedium.forContent(message.sender)
                    .copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_lucide_star),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            remember(message.body) { AnnotatedString(message.body).withContentFonts() },
            style = MaterialTheme.typography.bodyMedium.forContent(message.body),
            color = colors.onSurface,
            maxLines = PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stamp, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Preview(name = "18 · Starred")
@Composable
private fun StarredPreview() = OpenLineTheme(dark = false) {
    StarredScreen(
        StarredUiState(loading = false, messages = LibraryPreviewData.starred),
        LibraryPreviewData.navigation,
        onBack = {},
        onOpen = {},
    )
}

@Preview(name = "18 · Starred · empty, dark")
@Composable
private fun StarredEmptyPreview() = OpenLineTheme(dark = true) {
    StarredScreen(StarredUiState(loading = false), LibraryPreviewData.navigation, onBack = {}, onOpen = {})
}

package org.fossify.messages.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.DestinationCard
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.PageAppBar
import org.fossify.messages.ui.components.PageContentPadding
import org.fossify.messages.ui.inbox.LibraryDestination

/** 17 · Library / destinations. [destinations] are the ones the current settings make available. */
@Composable
fun LibraryScreen(
    destinations: List<LibraryDestination>,
    unreadSpam: Int,
    onOpen: (LibraryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageAppBar(stringResource(R.string.inbox_nav_library), subtitle = stringResource(R.string.library_subtitle))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            destinations.forEach { destination ->
                DestinationCard(
                    icon = destination.icon,
                    label = stringResource(destination.label),
                    description = stringResource(destination.description),
                    onClick = { onOpen(destination) },
                    badge = if (destination == LibraryDestination.SPAM) unreadSpam else 0,
                )
            }
            InfoPanel(
                title = stringResource(R.string.library_principle_title),
                body = stringResource(R.string.library_principle_body),
            )
        }
    }
}

@Preview(name = "17 · Library")
@Composable
private fun LibraryPreview() = OpenLineTheme(dark = false) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        LibraryScreen(LibraryDestination.entries, unreadSpam = 3, onOpen = {})
    }
}

@Preview(name = "17 · Library · dark")
@Composable
private fun LibraryDarkPreview() = OpenLineTheme(dark = true) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        LibraryScreen(LibraryDestination.entries, unreadSpam = 0, onOpen = {})
    }
}

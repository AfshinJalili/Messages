package com.afsh.messages.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.BarAction
import com.afsh.messages.ui.components.ConversationRow
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.InfoPanel
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.PageSelection
import com.afsh.messages.ui.components.SwipeGesture
import com.afsh.messages.ui.components.SwipeTone
import com.afsh.messages.ui.components.SwipeableRow
import com.afsh.messages.ui.inbox.InboxRow

private val SideMargin = 20.dp

/** Everything 19 · Archive can ask its activity to do. */
sealed interface ArchiveEvent {
    data object Back : ArchiveEvent
    data class Open(val row: InboxRow) : ArchiveEvent
    data class ToggleSelection(val row: InboxRow) : ArchiveEvent
    data object SelectAll : ArchiveEvent
    data object ClearSelection : ArchiveEvent
    data class Unarchive(val conversations: List<Conversation>) : ArchiveEvent
    data class Delete(val conversations: List<Conversation>) : ArchiveEvent
    data object DeleteAll : ArchiveEvent
}

/**
 * 19 · Archive / conversations. A swipe in either direction unarchives; long-press selects
 * rows for Unarchive or Delete.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onEvent: (ArchiveEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unarchive = SwipeGesture(R.string.unarchive, R.drawable.ic_lucide_inbox, SwipeTone.PRIMARY)
    PageScaffold(
        title = stringResource(R.string.archived_conversations),
        modifier = modifier,
        subtitle = stringResource(R.string.archive_subtitle),
        onBack = { onEvent(ArchiveEvent.Back) },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
        selection = if (state.selecting) {
            val selected = state.selectedConversations
            PageSelection(
                count = state.selected.size,
                onClear = { onEvent(ArchiveEvent.ClearSelection) },
                onSelectAll = { onEvent(ArchiveEvent.SelectAll) },
                actions = listOf(
                    BarAction(stringResource(R.string.unarchive), R.drawable.ic_lucide_inbox) { onEvent(ArchiveEvent.Unarchive(selected)) },
                    BarAction(stringResource(org.fossify.commons.R.string.delete), R.drawable.ic_lucide_trash_2, destructive = true) {
                        onEvent(ArchiveEvent.Delete(selected))
                    },
                ),
            )
        } else {
            null
        },
        actions = {
            if (state.rows.isNotEmpty()) {
                PageAppBarAction(
                    AfshIcon.Delete,
                    stringResource(R.string.empty_archive),
                    onClick = { onEvent(ArchiveEvent.DeleteAll) },
                )
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = SideMargin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.archive_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SideMargin),
                )
            }
            when {
                state.loading -> item(key = "loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = SideMargin))
                }

                state.rows.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_lucide_archive,
                        title = stringResource(R.string.no_archived_conversations),
                        modifier = Modifier.padding(horizontal = SideMargin),
                    )
                }

                else -> items(state.rows, key = { it.threadId }) { row ->
                    ArchivedRow(row, state, unarchive, onEvent, Modifier.animateItem())
                }
            }
            if (state.rows.isNotEmpty()) {
                item(key = "action") {
                    InfoPanel(
                        title = stringResource(R.string.archive_action_title),
                        body = stringResource(R.string.archive_action_text),
                        modifier = Modifier.padding(horizontal = SideMargin),
                        prominent = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedRow(
    row: InboxRow,
    state: ArchiveUiState,
    unarchive: SwipeGesture,
    onEvent: (ArchiveEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unarchiveLabel = stringResource(R.string.unarchive)
    val deleteLabel = stringResource(org.fossify.commons.R.string.delete)
    val conversations = listOf(row.conversation)
    val actions = remember(row, unarchiveLabel, deleteLabel) {
        listOf(
            CustomAccessibilityAction(unarchiveLabel) { onEvent(ArchiveEvent.Unarchive(conversations)); true },
            CustomAccessibilityAction(deleteLabel) { onEvent(ArchiveEvent.Delete(conversations)); true },
        )
    }
    SwipeableRow(
        left = unarchive,
        right = unarchive,
        enabled = !state.selecting,
        onSwipe = { onEvent(ArchiveEvent.Unarchive(listOf(row.conversation))) },
        modifier = modifier,
    ) {
        ConversationRow(
            row = row,
            selected = row.threadId in state.selected,
            selecting = state.selecting,
            onClick = { onEvent(if (state.selecting) ArchiveEvent.ToggleSelection(row) else ArchiveEvent.Open(row)) },
            onLongClick = { onEvent(ArchiveEvent.ToggleSelection(row)) },
            actions = actions,
        )
    }
}

@Preview(name = "19 · Archive")
@Composable
private fun ArchivePreview() = AfshTheme(dark = false) {
    ArchiveScreen(
        ArchiveUiState(loading = false, rows = LibraryPreviewData.archived),
        LibraryPreviewData.navigation,
        remember { SnackbarHostState() },
        onEvent = {},
    )
}

@Preview(name = "19 · Archive · selecting, dark")
@Composable
private fun ArchiveSelectingPreview() = AfshTheme(dark = true) {
    ArchiveScreen(
        ArchiveUiState(loading = false, rows = LibraryPreviewData.archived, selected = setOf(2)),
        LibraryPreviewData.navigation,
        remember { SnackbarHostState() },
        onEvent = {},
    )
}

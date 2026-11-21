package com.afsh.messages.ui.library

import com.afsh.messages.ui.Afsh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.afsh.messages.R
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.AfshButton
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ButtonKind
import com.afsh.messages.ui.components.ConfirmationDialog
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.TextAction
import com.afsh.messages.ui.components.RelativeDay
import com.afsh.messages.ui.components.relativeDay
import com.afsh.messages.ui.components.shortDate

/** Everything 22 · Recycle bin can ask its activity to do. */
sealed interface RecycleBinEvent {
    data object Back : RecycleBinEvent
    data class Open(val conversation: Conversation) : RecycleBinEvent
    data class Restore(val conversation: Conversation) : RecycleBinEvent
    data class DeleteForever(val conversation: Conversation) : RecycleBinEvent
    data object EmptyBin : RecycleBinEvent
}

/** Which permanent deletion is waiting for confirmation (43 · Delete / confirmation). */
private sealed interface PendingErase {
    data class One(val threadId: Long) : PendingErase
    data object All : PendingErase
}

/**
 * 22 · Recycle bin / restore. Restore is immediate; Delete forever and Empty recycle bin ask
 * first, because nothing brings those messages back.
 */
@Composable
fun RecycleBinScreen(
    state: RecycleBinUiState,
    navigation: PageNavigation,
    snackbarHostState: SnackbarHostState,
    onEvent: (RecycleBinEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingThread by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAll by rememberSaveable { mutableStateOf(false) }
    PageScaffold(
        title = stringResource(com.afsh.commons.R.string.recycle_bin),
        modifier = modifier,
        subtitle = stringResource(R.string.recycle_bin_subtitle),
        onBack = { onEvent(RecycleBinEvent.Back) },
        navigation = navigation,
        snackbarHostState = snackbarHostState,
    ) {
        LazyColumn(contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
            item(key = "note") {
                Text(
                    stringResource(R.string.recycle_bin_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.conversations.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = AfshIcon.Delete,
                        title = stringResource(R.string.no_recycle_bin_conversations),
                    )
                }

                else -> {
                    items(state.conversations, key = { it.conversation.threadId }) { recycled ->
                        RecycledCard(
                            recycled,
                            onOpen = { onEvent(RecycleBinEvent.Open(recycled.conversation)) },
                            onRestore = { onEvent(RecycleBinEvent.Restore(recycled.conversation)) },
                            onDeleteForever = { pendingThread = recycled.conversation.threadId },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "empty-bin") {
                        AfshButton(
                            stringResource(R.string.recycle_bin_empty),
                            onClick = { pendingAll = true },
                            modifier = Modifier.fillMaxWidth(),
                            kind = ButtonKind.DANGER,
                        )
                    }
                }
            }
        }
    }
    val pending = when {
        pendingAll -> PendingErase.All
        else -> pendingThread?.let { PendingErase.One(it) }
    }
    if (pending != null) {
        val dismiss = {
            pendingThread = null
            pendingAll = false
        }
        ConfirmationDialog(
            title = stringResource(
                if (pending is PendingErase.All) R.string.empty_bin_title else R.string.delete_forever_title,
            ),
            explanation = stringResource(
                if (pending is PendingErase.All) {
                    R.string.empty_recycle_bin_messages_confirmation
                } else {
                    R.string.delete_forever_conversation_text
                },
            ),
            confirmLabel = stringResource(
                if (pending is PendingErase.All) R.string.recycle_bin_empty else R.string.delete_forever,
            ),
            dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
            onConfirm = {
                dismiss()
                when (pending) {
                    PendingErase.All -> onEvent(RecycleBinEvent.EmptyBin)
                    is PendingErase.One -> state.conversations
                        .firstOrNull { it.conversation.threadId == pending.threadId }
                        ?.let { onEvent(RecycleBinEvent.DeleteForever(it.conversation)) }
                }
            },
            onDismiss = dismiss,
            destructive = true,
        )
    }
}

@Composable
private fun RecycledCard(
    recycled: RecycledConversation,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = recycled.conversation.title
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer, contentColor = colors.onSurface),
    ) {
        Column(
            Modifier.padding(start = Afsh.spacing.lg, end = Afsh.spacing.lg, top = Afsh.spacing.lg, bottom = Afsh.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.sm),
        ) {
            ContentText(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(recycledDetail(recycled), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.lg)) {
                TextAction(stringResource(R.string.restore), onClick = onRestore)
                TextAction(stringResource(R.string.delete_forever), onClick = onDeleteForever, destructive = true)
            }
        }
    }
}

/** "2 messages · deleted today". */
@Composable
private fun recycledDetail(recycled: RecycledConversation): String {
    val count = pluralStringResource(R.plurals.recycle_bin_messages, recycled.messageCount, recycled.messageCount)
    val deleted = when (relativeDay(recycled.deletedMillis)) {
        RelativeDay.TODAY -> stringResource(R.string.recycle_bin_deleted_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.recycle_bin_deleted_yesterday)
        RelativeDay.EARLIER -> stringResource(R.string.recycle_bin_deleted_on, shortDate(recycled.deletedMillis))
    }
    return stringResource(R.string.recycle_bin_detail, count, deleted)
}

@Preview(name = "22 · Recycle bin")
@Composable
private fun RecycleBinPreview() = AfshTheme(dark = false) {
    RecycleBinPreviewContent()
}

@Preview(name = "22 · Recycle bin · dark")
@Composable
private fun RecycleBinDarkPreview() = AfshTheme(dark = true) {
    RecycleBinPreviewContent()
}

@Composable
private fun RecycleBinPreviewContent() = RecycleBinScreen(
    RecycleBinUiState(loading = false, conversations = LibraryPreviewData.recycled),
    LibraryPreviewData.navigation,
    remember { SnackbarHostState() },
    onEvent = {},
)

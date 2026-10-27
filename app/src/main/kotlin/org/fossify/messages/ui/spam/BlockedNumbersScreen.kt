package org.fossify.messages.ui.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.components.EmptyState
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageContentPadding
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.TextAction
import org.fossify.messages.ui.withContentFonts

private val SenderRowHeight = 58.dp

/**
 * A blocked sender. [inSystemList] means Android's own block list holds it (messages never
 * arrive); otherwise the app files its messages in Spam.
 */
@Immutable
data class BlockedSender(val number: String, val name: String?, val inSystemList: Boolean) {
    val key get() = "${if (inSystemList) "s" else "a"}:$number"
}

@Immutable
data class BlockedNumbersUiState(val loading: Boolean = true, val senders: List<BlockedSender> = emptyList())

/** 38 · Blocked numbers / manage. Unblocking is immediate; Block a number opens the add page. */
@Composable
fun BlockedNumbersScreen(
    state: BlockedNumbersUiState,
    navigation: PageNavigation,
    onBack: () -> Unit,
    onUnblock: (BlockedSender) -> Unit,
    onBlockNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        title = stringResource(org.fossify.commons.R.string.blocked_numbers),
        modifier = modifier,
        subtitle = stringResource(R.string.blocked_numbers_subtitle),
        onBack = onBack,
        navigation = navigation,
    ) {
        LazyColumn(contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.senders.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_lucide_shield_ban,
                        title = stringResource(R.string.no_blocked_numbers),
                        text = stringResource(R.string.no_blocked_numbers_text),
                    )
                }

                else -> items(state.senders, key = { it.key }) { sender ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SenderRow(sender, onUnblock = { onUnblock(sender) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item(key = "block") {
                OpenLineButton(
                    stringResource(R.string.block_a_number),
                    onClick = onBlockNumber,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SenderRow(sender: BlockedSender, onUnblock: () -> Unit) {
    val label = sender.name ?: sender.number
    val description = listOfNotNull(sender.name, sender.number).distinct().joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SenderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            remember(label) { AnnotatedString(label).withContentFonts() },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        TextAction(stringResource(org.fossify.commons.R.string.unblock), onClick = onUnblock)
    }
}

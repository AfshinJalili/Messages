package com.afsh.messages.ui.spam

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
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshButton
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.EmptyState
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageNavigation
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.TextAction
import com.afsh.messages.ui.forLabel
import com.afsh.messages.ui.withContentFonts

private val SenderRowHeight = 58.dp

/** Which of the three Filter rules sender lists a row belongs to. */
enum class FilterSenderKind { AlwaysSpam, NeverSpam, AndroidBlocked }

/**
 * A sender on one of the Filter rules lists. [AlwaysSpam] files future messages in Spam;
 * [NeverSpam] keeps them in the inbox; [AndroidBlocked] means the phone may drop them.
 */
@Immutable
data class BlockedSender(val number: String, val name: String?, val kind: FilterSenderKind) {
    val key get() = "${kind.name}:$number"
}

@Immutable
data class BlockedNumbersUiState(
    val loading: Boolean = true,
    val senders: List<BlockedSender> = emptyList(),
)

/** 38 · Filter senders: always-spam, never-spam and Android-blocked, in three labeled sections. */
@Composable
fun BlockedNumbersScreen(
    state: BlockedNumbersUiState,
    navigation: PageNavigation,
    onBack: () -> Unit,
    onRemove: (BlockedSender) -> Unit,
    onBlockNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val always = remember(state.senders) { state.senders.filter { it.kind == FilterSenderKind.AlwaysSpam } }
    val never = remember(state.senders) { state.senders.filter { it.kind == FilterSenderKind.NeverSpam } }
    val android = remember(state.senders) { state.senders.filter { it.kind == FilterSenderKind.AndroidBlocked } }
    val empty = !state.loading && always.isEmpty() && never.isEmpty() && android.isEmpty()

    PageScaffold(
        title = stringResource(R.string.filter_senders),
        modifier = modifier,
        onBack = onBack,
        navigation = navigation,
    ) {
        LazyColumn(contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
            when {
                state.loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                empty -> item(key = "empty") {
                    EmptyState(
                        icon = AfshIcon.SpamShield,
                        title = stringResource(R.string.no_blocked_numbers),
                        text = stringResource(R.string.no_blocked_numbers_text),
                    )
                }

                else -> {
                    item(key = "always-heading") {
                        SectionHeading(stringResource(R.string.always_spam_list))
                    }
                    if (always.isEmpty()) {
                        item(key = "always-empty") {
                            Text(
                                stringResource(R.string.no_always_spam_senders),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(always, key = { it.key }) { sender ->
                            SenderSectionRow(sender, onRemove = { onRemove(sender) })
                        }
                    }

                    item(key = "never-heading") {
                        SectionHeading(stringResource(R.string.never_spam_list))
                    }
                    if (never.isEmpty()) {
                        item(key = "never-empty") {
                            Text(
                                stringResource(R.string.no_never_spam_senders),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(never, key = { it.key }) { sender ->
                            SenderSectionRow(sender, onRemove = { onRemove(sender) })
                        }
                    }

                    item(key = "android-heading") {
                        SectionHeading(stringResource(R.string.android_blocked))
                    }
                    item(key = "android-text") {
                        Text(
                            stringResource(R.string.android_blocked_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (android.isEmpty()) {
                        item(key = "android-empty") {
                            Text(
                                stringResource(R.string.no_android_blocked),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(android, key = { it.key }) { sender ->
                            SenderSectionRow(sender, onRemove = { onRemove(sender) })
                        }
                    }
                }
            }
            item(key = "block") {
                AfshButton(
                    stringResource(R.string.block_a_number),
                    onClick = onBlockNumber,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SenderSectionRow(sender: BlockedSender, onRemove: () -> Unit) {
    val label = sender.name ?: sender.number
    val description = listOfNotNull(sender.name, sender.number).distinct().joinToString(", ")
    val action = when (sender.kind) {
        FilterSenderKind.NeverSpam -> stringResource(R.string.remove_never_spam, label)
        FilterSenderKind.AlwaysSpam, FilterSenderKind.AndroidBlocked ->
            stringResource(com.afsh.commons.R.string.unblock)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SenderRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                remember(label) { AnnotatedString(label).withContentFonts() },
                style = MaterialTheme.typography.bodyMedium.forLabel(label).copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = description },
            )
            TextAction(
                when (sender.kind) {
                    FilterSenderKind.NeverSpam -> stringResource(com.afsh.commons.R.string.remove)
                    else -> stringResource(com.afsh.commons.R.string.unblock)
                },
                onClick = onRemove,
                modifier = Modifier.semantics { contentDescription = action },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

package com.afsh.messages.ui.spam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.afsh.messages.R
import com.afsh.messages.helpers.SenderFilter
import com.afsh.messages.helpers.SenderStanding
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.NavigationSetting
import com.afsh.messages.ui.components.RadioSetting
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.SwitchSetting
import com.afsh.messages.ui.withLabelDirection

/**
 * The one place a sender's spam rule and Android block are changed, opened from the conversation menu,
 * conversation details, inbox selection and Spam review. A radio group for where their messages go, a switch
 * for Block on this phone, and a way to every spam rule. Choosing closes the sheet; the caller offers Undo.
 *
 * [who] is the sender's name or "3 senders". [canBlock] is false when a group is among them: Android blocks
 * numbers, not conversations.
 */
@Composable
fun SpamAndBlockingSheet(
    who: String,
    standing: SenderStanding,
    canBlock: Boolean,
    onFilter: (SenderFilter) -> Unit,
    onBlock: (Boolean) -> Unit,
    onOpenRules: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AfshSheet(title = stringResource(R.string.filter_rules), onDismiss = onDismiss) {
        val sheet = this
        Column(Modifier.padding(horizontal = Afsh.spacing.xl)) {
            Text(
                who,
                style = MaterialTheme.typography.bodyMedium.withLabelDirection(who, LocalLayoutDirection.current),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Afsh.spacing.xs),
            )
            SectionHeading(stringResource(R.string.sender_filter_heading))
            if (standing.filter == null) {
                Text(
                    stringResource(R.string.sender_filter_mixed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Afsh.spacing.xs, vertical = Afsh.spacing.xs),
                )
            }
            SenderFilter.entries.forEach { filter ->
                RadioSetting(
                    label = stringResource(filter.label),
                    selected = standing.filter == filter,
                    value = stringResource(filter.text),
                    onClick = { sheet.dismissThen { onFilter(filter) } },
                )
            }
            if (canBlock) {
                HorizontalDivider(Modifier.padding(vertical = Afsh.spacing.sm), color = colors.outlineVariant)
                SwitchSetting(
                    label = stringResource(R.string.block_on_phone_switch),
                    checked = standing.blockedOnPhone,
                    onCheckedChange = { blocked -> sheet.dismissThen { onBlock(blocked) } },
                    icon = AfshIcon.Block,
                    value = stringResource(R.string.block_on_phone_switch_text),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Afsh.spacing.sm), color = colors.outlineVariant)
            NavigationSetting(
                stringResource(R.string.spam_rules_link),
                onClick = { sheet.dismissThen(onOpenRules) },
                icon = AfshIcon.SpamShield,
            )
        }
    }
}

/** The filter's name, in the sheet and as conversation details' value. */
internal val SenderFilter.label
    get() = when (this) {
        SenderFilter.AUTOMATIC -> R.string.sender_filter_automatic
        SenderFilter.ALWAYS_SPAM -> R.string.sender_filter_always
        SenderFilter.NEVER_SPAM -> R.string.sender_filter_never
    }

private val SenderFilter.text
    get() = when (this) {
        SenderFilter.AUTOMATIC -> R.string.sender_filter_automatic_text
        SenderFilter.ALWAYS_SPAM -> R.string.sender_filter_always_text
        SenderFilter.NEVER_SPAM -> R.string.sender_filter_never_text
    }

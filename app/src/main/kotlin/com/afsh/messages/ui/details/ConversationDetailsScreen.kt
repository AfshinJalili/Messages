package com.afsh.messages.ui.details

import com.afsh.messages.ui.ltr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Avatar
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.ActionTile
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.Choice
import com.afsh.messages.ui.components.ChoiceDialog
import com.afsh.messages.ui.components.IdentityCard
import com.afsh.messages.ui.components.NavigationSetting
import com.afsh.messages.ui.components.PageAppBarAction
import com.afsh.messages.ui.components.PageContentPadding
import com.afsh.messages.ui.components.PageScaffold
import com.afsh.messages.ui.components.SectionHeading
import com.afsh.messages.ui.components.SwitchSetting
import com.afsh.messages.ui.forLabel

private val MemberAvatarSize = 40.dp

/** One person in the conversation. */
@Immutable
data class Member(val name: String, val number: String, val photoUri: String)

/** A SIM to send from; [subscriptionId] 0 is "use the phone's default". */
@Immutable
data class SimChoice(val subscriptionId: Int, val label: String)

@Immutable
data class ConversationDetailsUiState(
    val title: String = "",
    val photoUri: String = "",
    val members: List<Member> = emptyList(),
    val isGroup: Boolean = false,
    val canDial: Boolean = false,
    val muted: Boolean = false,
    val customNotifications: Boolean = false,
    val sims: List<SimChoice> = emptyList(),
    val sim: Int = 0,
    val loading: Boolean = true,
)

sealed interface DetailsEvent {
    data object Back : DetailsEvent
    data object Call : DetailsEvent
    data object Search : DetailsEvent
    data object ToggleMute : DetailsEvent
    data class OpenContact(val member: Member) : DetailsEvent
    data object Rename : DetailsEvent
    data class SetCustomNotifications(val enabled: Boolean) : DetailsEvent
    data object OpenNotificationSettings : DetailsEvent
    data class SetSim(val subscriptionId: Int) : DetailsEvent
}

/**
 * 09 · Conversation / details. The header is [PageScaffold]'s stock Material 3 top app bar
 * (pinned scroll), then who it is, quick actions, and the settings that belong to this conversation.
 */
@Composable
fun ConversationDetailsScreen(state: ConversationDetailsUiState, onEvent: (DetailsEvent) -> Unit, modifier: Modifier = Modifier) {
    var choosingSim by rememberSaveable { mutableStateOf(false) }
    PageScaffold(
        title = state.title,
        subtitle = stringResource(R.string.conversation_details),
        onBack = { onEvent(DetailsEvent.Back) },
        actions = {
            if (state.canDial) {
                PageAppBarAction(
                    AfshIcon.Call,
                    stringResource(com.afsh.commons.R.string.call),
                    onClick = { onEvent(DetailsEvent.Call) },
                )
            }
        },
        modifier = modifier,
    ) {
        if (state.loading) return@PageScaffold
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(Afsh.spacing.lg),
        ) {
            val single = state.members.singleOrNull()
            IdentityCard(
                name = state.title,
                detail = when {
                    state.isGroup -> pluralStringResource(R.plurals.people_count, state.members.size, state.members.size)
                    single != null && single.number != state.title -> ltr(single.number)
                    else -> ""
                },
                photoUri = state.photoUri,
                isGroup = state.isGroup,
                modifier = if (single != null) {
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_contact)) {
                            onEvent(DetailsEvent.OpenContact(single))
                        }
                } else {
                    Modifier
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md)) {
                val tile = Modifier.weight(1f)
                if (state.canDial) {
                    ActionTile(AfshIcon.Call, stringResource(com.afsh.commons.R.string.call), { onEvent(DetailsEvent.Call) }, tile)
                }
                ActionTile(AfshIcon.Search, stringResource(com.afsh.commons.R.string.search), { onEvent(DetailsEvent.Search) }, tile)
                ActionTile(
                    if (state.muted) AfshIcon.Notifications else AfshIcon.NotificationsOff,
                    stringResource(if (state.muted) R.string.unmute else R.string.mute),
                    { onEvent(DetailsEvent.ToggleMute) },
                    tile,
                )
            }
            Column {
                NavigationSetting(
                    stringResource(R.string.conversation_name),
                    onClick = { onEvent(DetailsEvent.Rename) },
                    value = state.title,
                    icon = AfshIcon.Edit,
                )
                SwitchSetting(
                    stringResource(R.string.custom_notifications),
                    checked = state.customNotifications,
                    onCheckedChange = { onEvent(DetailsEvent.SetCustomNotifications(it)) },
                    icon = AfshIcon.Notifications,
                    value = stringResource(if (state.customNotifications) R.string.custom_notifications_on else R.string.custom_notifications_off),
                )
                if (state.customNotifications) {
                    NavigationSetting(
                        stringResource(R.string.sound_and_vibration),
                        onClick = { onEvent(DetailsEvent.OpenNotificationSettings) },
                        value = stringResource(R.string.sound_and_vibration_value),
                        icon = AfshIcon.Volume,
                    )
                }
                if (state.sims.size > 1) {
                    NavigationSetting(
                        stringResource(R.string.sim),
                        onClick = { choosingSim = true },
                        value = state.sims.firstOrNull { it.subscriptionId == state.sim }?.label
                            ?: stringResource(R.string.sim_default),
                        icon = AfshIcon.SimCard,
                    )
                }
            }
            if (state.isGroup) {
                Column {
                    SectionHeading(stringResource(R.string.people))
                    state.members.forEach { member -> MemberRow(member) { onEvent(DetailsEvent.OpenContact(member)) } }
                }
            }
        }
    }
    if (choosingSim) {
        ChoiceDialog(
            title = stringResource(R.string.sim),
            choices = listOf(Choice(0, stringResource(R.string.sim_default))) + state.sims.map { Choice(it.subscriptionId, it.label) },
            selected = state.sim,
            dismissLabel = stringResource(com.afsh.commons.R.string.cancel),
            onChoose = {
                choosingSim = false
                onEvent(DetailsEvent.SetSim(it))
            },
            onDismiss = { choosingSim = false },
        )
    }
}

@Composable
private fun MemberRow(member: Member, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Afsh.spacing.xs, vertical = Afsh.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
    ) {
        Avatar(member.name, member.photoUri, isGroup = false, size = MemberAvatarSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xxs)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium.forLabel(member.name), color = colors.onSurface)
            if (member.number != member.name) {
                Text(ltr(member.number), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

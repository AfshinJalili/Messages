package org.fossify.messages.ui.details

import org.fossify.messages.ui.ltr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.Avatar
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.ActionTile
import org.fossify.messages.ui.components.Choice
import org.fossify.messages.ui.components.ChoiceDialog
import org.fossify.messages.ui.components.IdentityCard
import org.fossify.messages.ui.components.NavigationSetting
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.SwitchSetting

private val HeaderMinHeight = 72.dp
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
 * 09 · Conversation / details. The header is the conversation's own (09 keeps the thread header),
 * then who it is, quick actions, and the settings that belong to this conversation.
 */
@Composable
fun ConversationDetailsScreen(state: ConversationDetailsUiState, onEvent: (DetailsEvent) -> Unit, modifier: Modifier = Modifier) {
    var choosingSim by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = { DetailsHeader(state, onEvent) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tile = Modifier.weight(1f)
                if (state.canDial) {
                    ActionTile(R.drawable.ic_lucide_phone, stringResource(org.fossify.commons.R.string.call), { onEvent(DetailsEvent.Call) }, tile)
                }
                ActionTile(R.drawable.ic_lucide_search, stringResource(org.fossify.commons.R.string.search), { onEvent(DetailsEvent.Search) }, tile)
                ActionTile(
                    if (state.muted) R.drawable.ic_lucide_bell else R.drawable.ic_lucide_bell_off,
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
                    icon = R.drawable.ic_lucide_pencil,
                )
                SwitchSetting(
                    stringResource(R.string.custom_notifications),
                    checked = state.customNotifications,
                    onCheckedChange = { onEvent(DetailsEvent.SetCustomNotifications(it)) },
                    icon = R.drawable.ic_lucide_bell,
                    value = stringResource(if (state.customNotifications) R.string.custom_notifications_on else R.string.custom_notifications_off),
                )
                if (state.customNotifications) {
                    NavigationSetting(
                        stringResource(R.string.sound_and_vibration),
                        onClick = { onEvent(DetailsEvent.OpenNotificationSettings) },
                        value = stringResource(R.string.sound_and_vibration_value),
                        icon = R.drawable.ic_lucide_volume_2,
                    )
                }
                if (state.sims.size > 1) {
                    NavigationSetting(
                        stringResource(R.string.sim),
                        onClick = { choosingSim = true },
                        value = state.sims.firstOrNull { it.subscriptionId == state.sim }?.label
                            ?: stringResource(R.string.sim_default),
                        icon = R.drawable.ic_lucide_card_sim,
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
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onChoose = {
                choosingSim = false
                onEvent(DetailsEvent.SetSim(it))
            },
            onDismiss = { choosingSim = false },
        )
    }
}

/** The conversation header of 09: back, the name over "Conversation details", and Call. */
@Composable
private fun DetailsHeader(state: ConversationDetailsUiState, onEvent: (DetailsEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .heightIn(min = HeaderMinHeight)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val back = stringResource(org.fossify.commons.R.string.back)
            IconButton(onClick = { onEvent(DetailsEvent.Back) }, modifier = Modifier.semantics { contentDescription = back }) {
                Icon(painterResource(R.drawable.ic_lucide_arrow_left), contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.conversation_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenLine.colors.onBandVariant,
                )
            }
            if (state.canDial) {
                val call = stringResource(org.fossify.commons.R.string.call)
                IconButton(onClick = { onEvent(DetailsEvent.Call) }, modifier = Modifier.semantics { contentDescription = call }) {
                    Icon(painterResource(R.drawable.ic_lucide_phone), contentDescription = null)
                }
            }
        }
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
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(member.name, member.photoUri, isGroup = false, size = MemberAvatarSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            if (member.number != member.name) {
                Text(ltr(member.number), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

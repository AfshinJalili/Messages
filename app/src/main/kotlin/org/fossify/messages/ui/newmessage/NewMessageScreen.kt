package org.fossify.messages.ui.newmessage

import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.normalizeString
import org.fossify.messages.R
import org.fossify.messages.ui.Avatar
import org.fossify.messages.ui.components.AttentionNotice
import org.fossify.messages.ui.components.Choice
import org.fossify.messages.ui.components.ChoiceDialog
import org.fossify.messages.ui.components.InfoPanel
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.SearchField
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.TextFieldSlab
import org.fossify.messages.ui.components.UpAction

private val RowAvatarSize = 44.dp
private val ChipRemoveSize = 15.dp
private val ComposerIconSize = 24.dp
private val SendSlabWidth = 64.dp
private const val MIN_TYPED_LENGTH = 3
private const val NUMBER_SEPARATORS = "+-(). "

/** One of a person's numbers: "+98 912 555 0100" labelled "Mobile". */
@Immutable
data class PhoneChoice(val number: String, val label: String)

@Immutable
data class Person(val name: String, val photoUri: String, val phones: List<PhoneChoice>)

/** [contacts] is null while they load; [canReadContacts] is false until the permission is granted. */
@Immutable
data class NewMessageUiState(
    val suggested: List<Person> = emptyList(),
    val contacts: List<Person>? = null,
    val canReadContacts: Boolean = true,
)

/** Someone to write to: the number the thread uses and the name it shows. */
@Immutable
data class Recipient(val number: String, val name: String)

sealed interface NewMessageEvent {
    data object Close : NewMessageEvent
    data class Write(val recipients: List<Recipient>) : NewMessageEvent
    data object AllowContacts : NewMessageEvent
}

private enum class Mode { PICK, GROUP, INVALID }

/**
 * 05 · New message / recipients, 16 · New group / people and 42 · Recipient / invalid number, as
 * one flow: pick a person or type a number; Start a group collects people first; a number that
 * cannot take an SMS stops at 42 instead of opening an empty thread.
 */
@Composable
fun NewMessageScreen(state: NewMessageUiState, onEvent: (NewMessageEvent) -> Unit, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(Mode.PICK) }
    // Where Edit number on 42 goes back to.
    var typedFrom by rememberSaveable { mutableStateOf(Mode.PICK) }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = rememberSaveable(saver = recipientListSaver()) { mutableStateListOf() }
    var choosingNumber by remember { mutableStateOf<Person?>(null) }

    fun pick(recipient: Recipient) {
        if (mode == Mode.GROUP) {
            if (chosen.none { it.number == recipient.number }) chosen += recipient
            query = ""
        } else {
            onEvent(NewMessageEvent.Write(listOf(recipient)))
        }
    }

    fun pick(person: Person) {
        if (person.phones.size > 1) choosingNumber = person else person.phones.firstOrNull()?.let { pick(Recipient(it.number, person.name)) }
    }

    fun submitTyped() {
        val number = query.trim()
        // A name being searched for is not a recipient to check.
        if (!number.looksLikeAddress()) return
        if (isWritableAddress(number)) {
            pick(Recipient(number, number))
        } else {
            typedFrom = mode
            mode = Mode.INVALID
        }
    }

    when (mode) {
        Mode.PICK -> PickPage(state, query, { query = it }, ::pick, ::submitTyped, onStartGroup = { mode = Mode.GROUP }, onEvent, modifier)
        Mode.GROUP -> {
            BackHandler { mode = Mode.PICK }
            GroupPage(
                state = state,
                query = query,
                onQuery = { query = it },
                chosen = chosen,
                onPick = ::pick,
                onSubmitTyped = ::submitTyped,
                onBack = { mode = Mode.PICK },
                onContinue = { onEvent(NewMessageEvent.Write(chosen.toList())) },
                modifier = modifier,
            )
        }
        Mode.INVALID -> {
            BackHandler { mode = typedFrom }
            InvalidNumberPage(query, { query = it }, onEdit = { mode = typedFrom }, modifier = modifier)
        }
    }
    choosingNumber?.let { person ->
        ChoiceDialog(
            title = person.name,
            choices = person.phones.map { Choice(it.number, it.number, it.label) },
            selected = null,
            dismissLabel = stringResource(org.fossify.commons.R.string.cancel),
            onChoose = { number ->
                choosingNumber = null
                pick(Recipient(number, person.name))
            },
            onDismiss = { choosingNumber = null },
        )
    }
}

@Composable
private fun PickPage(
    state: NewMessageUiState,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (Person) -> Unit,
    onSubmitTyped: () -> Unit,
    onStartGroup: () -> Unit,
    onEvent: (NewMessageEvent) -> Unit,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PageScaffold(
        title = stringResource(R.string.new_message),
        subtitle = stringResource(R.string.new_message_subtitle),
        onBack = { onEvent(NewMessageEvent.Close) },
        upAction = UpAction.CLOSE,
        bottomBar = { WaitingComposer() },
        modifier = modifier,
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item(key = "search") {
                SearchField(
                    query = query,
                    onQuery = onQuery,
                    hint = stringResource(R.string.name_or_phone_number),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmitTyped() }),
                )
            }
            typedNumberRow(query, R.string.write_to, onSubmitTyped)
            if (query.isBlank()) {
                if (state.suggested.isNotEmpty()) {
                    item(key = "suggested") { SectionHeading(stringResource(R.string.suggested)) }
                    items(state.suggested, key = { "s" + it.key }) { PersonRow(it) { onPick(it) } }
                }
                item(key = "group") { StartGroupRow(onStartGroup) }
            }
            contactRows(state, query, excluded = emptySet(), onPick = onPick, onEvent = onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupPage(
    state: NewMessageUiState,
    query: String,
    onQuery: (String) -> Unit,
    chosen: SnapshotStateList<Recipient>,
    onPick: (Person) -> Unit,
    onSubmitTyped: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PageScaffold(
        title = stringResource(R.string.new_group),
        subtitle = stringResource(R.string.new_group_subtitle),
        onBack = onBack,
        bottomBar = {
            Box(
                Modifier
                    .background(colors.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                OpenLineButton(
                    pluralStringResource(R.plurals.continue_with_people, chosen.size, chosen.size),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = chosen.size >= 2,
                )
            }
        },
        modifier = modifier,
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (chosen.isNotEmpty()) {
                item(key = "chosen") {
                    FlowRow(
                        Modifier.padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chosen.forEach { recipient -> ChosenChip(recipient) { chosen.remove(recipient) } }
                    }
                }
            }
            item(key = "search") {
                SearchField(
                    query = query,
                    onQuery = onQuery,
                    hint = stringResource(R.string.add_someone_else),
                    modifier = Modifier.padding(bottom = 4.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmitTyped() }),
                )
            }
            typedNumberRow(query, R.string.add_number, onSubmitTyped)
            contactRows(
                state,
                query,
                excluded = chosen.map { it.number }.toSet(),
                onPick = onPick,
                onEvent = {},
            )
        }
    }
}

@Composable
private fun InvalidNumberPage(number: String, onNumber: (String) -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    PageScaffold(
        title = stringResource(R.string.check_the_number),
        subtitle = stringResource(R.string.check_the_number_subtitle),
        onBack = onEdit,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AttentionNotice(
                title = stringResource(R.string.enter_a_valid_number),
                detail = stringResource(R.string.enter_a_valid_number_text),
                icon = R.drawable.ic_lucide_triangle_alert,
            )
            TextFieldSlab(
                value = number,
                onValueChange = onNumber,
                label = stringResource(R.string.phone_number),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = true,
            )
            OpenLineButton(stringResource(R.string.edit_number), onClick = onEdit, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "Write to 0912…" once the query could be a number. */
private fun LazyListScope.typedNumberRow(query: String, label: Int, onSubmit: () -> Unit) {
    val typed = query.trim()
    if (!typed.looksLikeAddress()) return
    item(key = "typed") {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onSubmit)
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_lucide_send), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(stringResource(label, typed), style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
    }
}

/** All contacts, or those matching [query]; with no permission, the way to grant it. */
private fun LazyListScope.contactRows(
    state: NewMessageUiState,
    query: String,
    excluded: Set<String>,
    onPick: (Person) -> Unit,
    onEvent: (NewMessageEvent) -> Unit,
) {
    if (!state.canReadContacts) {
        item(key = "permission") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPanel(
                    title = stringResource(R.string.contacts_access_title),
                    body = stringResource(R.string.contacts_access_text),
                )
                OpenLineButton(
                    stringResource(R.string.allow_contacts),
                    onClick = { onEvent(NewMessageEvent.AllowContacts) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }
    val contacts = state.contacts
    if (contacts == null) {
        item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val shown = contacts.filter { person -> person.phones.none { it.number in excluded } && person.matches(query) }
        .sortedBy { !it.name.startsWith(query.trim(), ignoreCase = true) }
    if (query.isBlank() && shown.isNotEmpty()) item(key = "contacts") { SectionHeading(stringResource(R.string.contacts)) }
    if (shown.isEmpty() && query.isNotBlank() && query.none { it.isDigit() }) {
        item(key = "none") {
            Text(
                stringResource(R.string.no_contacts_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
    }
    items(shown, key = { "c" + it.key }) { PersonRow(it) { onPick(it) } }
}

@Composable
private fun PersonRow(person: Person, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val detail = when (person.phones.size) {
        0 -> ""
        1 -> person.phones.first().let { if (it.label.isEmpty()) it.number else stringResource(R.string.detail_pair, it.label, it.number) }
        else -> pluralStringResource(R.plurals.phone_numbers_count, person.phones.size, person.phones.size)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(person.name, person.photoUri, isGroup = false, size = RowAvatarSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartGroupRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lucide_users), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.start_a_group), style = MaterialTheme.typography.titleMedium, color = colors.primary)
    }
}

@Composable
private fun ChosenChip(recipient: Recipient, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val remove = stringResource(R.string.remove_person, recipient.name)
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.secondaryContainer)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(recipient.name, style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer)
        IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = remove }) {
            Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = null, tint = colors.primary, modifier = Modifier.size(ChipRemoveSize))
        }
    }
}

/** 05's composer before there is anyone to write to: there, but switched off. */
@Composable
private fun WaitingComposer() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_lucide_plus), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(ComposerIconSize))
        }
        Text(
            stringResource(R.string.choose_recipient_first),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surface)
                .padding(12.dp),
        )
        Box(
            Modifier
                .size(SendSlabWidth, 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_lucide_send), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(ComposerIconSize))
        }
    }
}

/** Typed text meant as an address rather than a name: it has digits or is an email. */
private fun String.looksLikeAddress() = length >= MIN_TYPED_LENGTH && (any { it.isDigit() } || contains('@'))

/**
 * Whether a typed address can take a message: an email (sent as MMS), or a dialable number of
 * digits and the usual separators with at least [MIN_TYPED_LENGTH] digits, so short codes pass and
 * letters, # and * stop at 42.
 */
internal fun isWritableAddress(address: String): Boolean {
    if (Patterns.EMAIL_ADDRESS.matcher(address).matches()) return true
    return address.all { it.isDigit() || it in NUMBER_SEPARATORS } &&
        address.count { it.isDigit() } >= MIN_TYPED_LENGTH &&
        PhoneNumberUtils.isWellFormedSmsAddress(address)
}

private val Person.key get() = name + phones.joinToString { it.number }

private fun Person.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val digits = q.filter { it.isDigit() }
    return name.contains(q, true) || name.normalizeString().contains(q.normalizeString(), true) ||
        (digits.isNotEmpty() && phones.any { phone -> phone.number.filter { it.isDigit() }.contains(digits) })
}

private fun recipientListSaver() = listSaver<SnapshotStateList<Recipient>, String>(
    save = { list -> list.flatMap { listOf(it.number, it.name) } },
    restore = { saved -> mutableStateListOf(*saved.chunked(2).map { Recipient(it[0], it[1]) }.toTypedArray()) },
)

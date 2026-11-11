package com.afsh.messages.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.components.Choice
import com.afsh.messages.ui.components.ChoiceDialog
import com.afsh.messages.ui.newmessage.NewMessageUiState
import com.afsh.messages.ui.newmessage.PeoplePicker
import com.afsh.messages.ui.newmessage.Person
import com.afsh.messages.ui.newmessage.Recipient
import com.afsh.messages.ui.newmessage.isWritableAddress

/**
 * Manage people: who the conversation goes to. [people] come first; the first of them is the conversation's own
 * person and cannot be removed. [contacts] feeds the suggestions; its list is null until the address book has loaded.
 */
@Immutable
data class ManagePeopleState(
    val open: Boolean = false,
    val people: List<Recipient> = emptyList(),
    val contacts: NewMessageUiState = NewMessageUiState(),
)

/**
 * The Manage people panel over the timeline: the shared [PeoplePicker] (as on New group) with a Done check that
 * applies the change. A contact with several numbers asks which one, like New message.
 */
@Composable
fun ManagePeoplePanel(
    state: ManagePeopleState,
    onAdd: (Recipient) -> Unit,
    onRemove: (Recipient) -> Unit,
    onDone: () -> Unit,
    onAllowContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var choosingNumber by remember { mutableStateOf<Person?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun add(recipient: Recipient) {
        onAdd(recipient)
        query = ""
    }

    fun pick(person: Person) {
        if (person.phones.size > 1) choosingNumber = person else person.phones.firstOrNull()?.let { add(Recipient(it.number, person.name)) }
    }

    fun submitTyped() {
        val number = query.trim()
        if (isWritableAddress(number)) add(Recipient(number, number))
    }

    val fixed = state.people.firstOrNull()
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.people), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onDone) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_check),
                        contentDescription = stringResource(org.fossify.commons.R.string.confirm_selection),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            PeoplePicker(
                state = state.contacts,
                query = query,
                onQuery = { query = it },
                chosen = state.people,
                onRemove = onRemove,
                onPick = ::pick,
                onSubmitTyped = ::submitTyped,
                canRemove = { it != fixed },
                onAllowContacts = onAllowContacts,
                fieldModifier = Modifier.focusRequester(focus),
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
            )
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
                add(Recipient(number, person.name))
            },
            onDismiss = { choosingNumber = null },
        )
    }
}

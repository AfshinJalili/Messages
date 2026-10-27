package org.fossify.messages.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.components.EmptyState
import org.fossify.messages.ui.components.IdentityCard
import org.fossify.messages.ui.components.LabeledField
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageScaffold

private val ContactAvatarSize = 64.dp

/** What tapping a field does. */
enum class FieldAction { DIAL, EMAIL }

@Immutable
data class CardField(val label: String, val value: String, val action: FieldAction? = null)

/** One person in a shared vCard. */
@Immutable
data class ContactCard(val name: String, val fields: List<CardField>)

/** [cards] is null while the file is read; an unreadable file gives an empty list. */
@Immutable
data class ContactCardUiState(val cards: List<ContactCard>? = null, val source: String = "")

/** 14 · Contact card / viewer: who was shared, their fields, then Save to contacts. */
@Composable
fun ContactCardScreen(
    state: ContactCardUiState,
    onBack: () -> Unit,
    onField: (CardField) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        title = stringResource(R.string.contact_card),
        subtitle = stringResource(R.string.contact_card_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        val cards = state.cards
        when {
            cards == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            cards.isEmpty() -> EmptyState(
                icon = R.drawable.ic_lucide_user,
                title = stringResource(R.string.contact_card_unreadable),
                modifier = Modifier.fillMaxSize(),
            )

            else -> Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                cards.forEach { card ->
                    IdentityCard(
                        name = card.name.ifEmpty { stringResource(org.fossify.commons.R.string.unknown) },
                        detail = state.source,
                        avatarSize = ContactAvatarSize,
                    )
                    Column {
                        card.fields.forEach { field ->
                            LabeledField(field.label, field.value, onClick = field.action?.let { { onField(field) } })
                        }
                    }
                }
                OpenLineButton(
                    stringResource(R.string.save_to_contacts),
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

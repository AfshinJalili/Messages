package org.fossify.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import ezvcard.property.Email
import ezvcard.property.Organization
import ezvcard.property.Telephone
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.messages.R
import org.fossify.messages.extensions.dialNumber
import org.fossify.commons.extensions.sendEmailIntent
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.helpers.parseVCardFromUri
import org.fossify.messages.models.VCardWrapper
import org.fossify.messages.ui.message.CardField
import org.fossify.messages.ui.message.ContactCard
import org.fossify.messages.ui.message.ContactCardScreen
import org.fossify.messages.ui.message.ContactCardUiState
import org.fossify.messages.ui.message.FieldAction

/**
 * 14 · Contact card / viewer for a vCard shared in a message. [EXTRA_VCARD_SOURCE] is the optional
 * "Shared by Mina · today" line.
 */
class VCardViewerActivity : ComposePageActivity() {
    private var state by mutableStateOf(ContactCardUiState())
    private val uri by lazy { IntentCompat.getParcelableExtra(intent, EXTRA_VCARD_URI, Uri::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = state.copy(source = intent.getStringExtra(EXTRA_VCARD_SOURCE).orEmpty())
        val uri = uri ?: return finish()
        parseVCardFromUri(this, uri) { vCards ->
            val cards = vCards.map { card(VCardWrapper.from(this, it)) }
            runOnUiThread { state = state.copy(cards = cards) }
        }
    }

    @Composable
    override fun Page() = ContactCardScreen(state, onBack = ::finish, onField = ::open, onSave = ::save)

    private fun card(wrapper: VCardWrapper) = ContactCard(
        name = wrapper.fullName.orEmpty(),
        fields = wrapper.properties.filter { it.value.isNotEmpty() }.map { property ->
            when (property.property) {
                is Telephone -> CardField(property.type.ifEmpty { getString(R.string.phone_number) }, property.value, FieldAction.DIAL)
                is Email -> CardField(property.type.ifEmpty { getString(org.fossify.commons.R.string.email) }, property.value, FieldAction.EMAIL)
                is Organization -> CardField(getString(R.string.company), property.value)
                else -> CardField(property.type, property.value)
            }
        },
    )

    private fun open(field: CardField) {
        when (field.action) {
            FieldAction.DIAL -> dialNumber(field.value.normalizePhoneNumber())
            FieldAction.EMAIL -> sendEmailIntent(field.value)
            null -> Unit
        }
    }

    private fun save() {
        val uri = uri ?: return
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri)?.lowercase())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launchActivityIntent(this)
        }
    }

    companion object {
        const val EXTRA_VCARD_SOURCE = "vcard_source"
    }
}

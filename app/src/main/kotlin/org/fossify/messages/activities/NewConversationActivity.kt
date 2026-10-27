package org.fossify.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.google.gson.Gson
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.getSuggestedContacts
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.helpers.SmsIntentParser
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URI
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URIS
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TEXT
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.ui.newmessage.NewMessageEvent
import org.fossify.messages.ui.newmessage.NewMessageScreen
import org.fossify.messages.ui.newmessage.NewMessageUiState
import org.fossify.messages.ui.newmessage.Person
import org.fossify.messages.ui.newmessage.PhoneChoice
import org.fossify.messages.ui.newmessage.Recipient
import java.net.URLDecoder

/**
 * 05, 16 and 42: choose who to write to. Also the target of share and sms: links, which go
 * straight to the thread when they already name a recipient.
 */
class NewConversationActivity : ComposePageActivity() {
    private var state by mutableStateOf(NewMessageUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (openFromSmsLink()) return
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Contacts are optional: without them the page still takes a typed number.
        handlePermission(PERMISSION_READ_CONTACTS) { granted -> onContactsPermission(granted) }
    }

    @Composable
    override fun Page() = NewMessageScreen(state, ::onEvent)

    private fun onEvent(event: NewMessageEvent) {
        when (event) {
            NewMessageEvent.Close -> finish()
            is NewMessageEvent.Write -> write(event.recipients)
            NewMessageEvent.AllowContacts -> handlePermission(PERMISSION_READ_CONTACTS) { granted -> onContactsPermission(granted) }
        }
    }

    private fun onContactsPermission(granted: Boolean) {
        state = state.copy(canReadContacts = granted || hasPermission(PERMISSION_READ_CONTACTS))
        if (state.canReadContacts) loadContacts()
    }

    private fun loadContacts() {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val suggested = getSuggestedContacts(privateContacts).map { it.toPerson() }
            runOnUiThread { state = state.copy(suggested = suggested) }
            SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
                val all = (contacts + privateContacts).sorted().map { it.toPerson() }.filter { it.phones.isNotEmpty() }
                runOnUiThread { state = state.copy(contacts = all) }
            }
        }
    }

    private fun SimpleContact.toPerson() = Person(
        name = name,
        photoUri = photoUri,
        phones = phoneNumbers.distinctBy { it.normalizedNumber }.map { phone ->
            PhoneChoice(phone.normalizedNumber.ifEmpty { phone.value }, Phone.getTypeLabel(resources, phone.type, phone.label).toString())
        },
    )

    /** An sms: link or share with a recipient goes straight to its thread. */
    private fun openFromSmsLink(): Boolean {
        val (body, recipients) = SmsIntentParser.parse(intent) ?: return false
        if (body.isEmpty() && recipients.isEmpty()) return false
        launchThread(URLDecoder.decode(recipients.replace("+", "%2b").trim(), Charsets.UTF_8.name()), name = "", body = body)
        finish()
        return true
    }

    private fun write(recipients: List<Recipient>) {
        if (recipients.isEmpty()) return
        launchThread(
            phoneNumber = recipients.joinToString(";") { it.number },
            name = recipients.joinToString(", ") { it.name },
        )
    }

    private fun launchThread(phoneNumber: String, name: String, body: String = "") {
        hideKeyboard()
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, body.ifEmpty { intent.getStringExtra(Intent.EXTRA_TEXT) })
            putExtra(THREAD_NUMBER, number)
            val shared = intent.extras?.containsKey(Intent.EXTRA_STREAM) == true
            if (intent.action == Intent.ACTION_SEND && shared) {
                putExtra(THREAD_ATTACHMENT_URI, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && shared) {
                putExtra(THREAD_ATTACHMENT_URIS, IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }
            startActivity(this)
        }
    }
}

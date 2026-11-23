package com.afsh.messages.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.afsh.commons.activities.BaseSimpleActivity
import com.afsh.commons.extensions.getMimeType
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.launchActivityIntent
import com.afsh.commons.extensions.launchViewContactIntent
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.PERMISSION_CALL_PHONE
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.activities.ConversationDetailsActivity
import com.afsh.messages.helpers.THREAD_ID
import java.util.Locale

fun BaseSimpleActivity.dialNumber(phoneNumber: String, callback: (() -> Unit)? = null) {
    hideKeyboard()
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", phoneNumber, null)

            try {
                startActivity(this)
                callback?.invoke()
            } catch (_: ActivityNotFoundException) {
                toast(com.afsh.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.launchViewIntent(uri: Uri, mimetype: String, filename: String) {
    Intent().apply {
        action = Intent.ACTION_VIEW
        setDataAndType(uri, mimetype.lowercase(Locale.getDefault()))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            hideKeyboard()
            startActivity(this)
        } catch (_: ActivityNotFoundException) {
            val newMimetype = filename.getMimeType()
            if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                launchViewIntent(uri, newMimetype, filename)
            } else {
                toast(com.afsh.commons.R.string.no_app_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.startContactDetailsIntent(contact: SimpleContact) {
    ensureBackgroundThread {
        val lookupKey = SimpleContactsHelper(this)
            .getContactLookupKey(contactId = contact.rawId.toString())
        val publicUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey
        )
        runOnUiThread {
            launchViewContactIntent(publicUri)
        }
    }
}

/** 09 · Conversation details for [threadId]. */
fun Activity.launchConversationDetails(threadId: Long) {
    Intent(this, ConversationDetailsActivity::class.java).apply {
        putExtra(THREAD_ID, threadId)
        startActivity(this)
    }
}

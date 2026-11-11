package com.afsh.messages.extensions

import android.content.Context
import android.content.res.Resources
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.afsh.messages.ui.newmessage.PhoneChoice
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.SimpleContact
import androidx.core.net.toUri

fun ArrayList<SimpleContact>.getThreadTitle(): String {
    return TextUtils.join(", ", map { it.name }.toTypedArray()).orEmpty()
}

fun ArrayList<SimpleContact>.getAddresses(): List<String> {
    return flatMap { it.phoneNumbers }.map { it.normalizedNumber }
}

fun SimpleContact.toPerson(context: Context? = null): Person {
    val uri =
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactId.toString())
    val iconCompat = if (context != null) {
        loadIcon(context)
    } else {
        IconCompat.createWithContentUri(photoUri)
    }

    return Person.Builder()
        .setName(name)
        .setUri(uri.toString())
        .setIcon(iconCompat)
        .setKey(uri.toString())
        .build()
}

fun SimpleContact.loadIcon(context: Context): IconCompat {
    try {
        val stream = context.contentResolver.openInputStream(photoUri.toUri())
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        val iconCompat = IconCompat.createWithAdaptiveBitmap(bitmap)
        return iconCompat
    } catch (e: Exception) {
        return IconCompat.createWithAdaptiveBitmap(
            SimpleContactsHelper(context).getContactLetterIcon(name)
        )
    }
}


/** The contact as the people picker shows it: one row per person, each distinct number labelled "Mobile", "Work"…. */
fun SimpleContact.toPickerPerson(resources: Resources) = com.afsh.messages.ui.newmessage.Person(
    name = name,
    photoUri = photoUri,
    phones = phoneNumbers.distinctBy { it.normalizedNumber }.map { phone ->
        PhoneChoice(phone.normalizedNumber.ifEmpty { phone.value }, Phone.getTypeLabel(resources, phone.type, phone.label).toString())
    },
)

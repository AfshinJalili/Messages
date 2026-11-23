package com.afsh.messages.activities

import android.content.Intent
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.toPickerPerson
import com.afsh.messages.helpers.THREAD_ID
import com.afsh.messages.ui.newmessage.NewMessageUiState
import com.afsh.messages.ui.newmessage.Recipient

/** Manage people: a different set of people is a different conversation, which opens in this one's place. */
internal class ThreadPeopleActions(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val holder get() = visit.holder
    private val people get() = visit.people
    private var contactsLoaded = false

    fun toggle() {
        if (holder.people.open) {
            holder.closePeople()
        } else {
            holder.openPeople(people.recipients())
            loadContacts()
        }
    }

    /**
     * The address book for Manage people. It needs every contact on the phone, so it loads the first
     * time the person opens Manage people, not on every thread load and refresh.
     */
    private fun loadContacts() {
        if (contactsLoaded) return
        if (!activity.hasPermission(PERMISSION_READ_CONTACTS)) {
            holder.showPeopleContacts(NewMessageUiState(canReadContacts = false))
            return
        }
        contactsLoaded = true
        SimpleContactsHelper(activity).getAvailableContacts(false) { contacts ->
            val all = (contacts + people.privateContacts).sorted()
                .map { it.toPickerPerson(activity.resources) }
                .filter { it.phones.isNotEmpty() }
            activity.runOnUiThread {
                if (activity.isDestroyed || activity.isFinishing) return@runOnUiThread
                holder.showPeopleContacts(NewMessageUiState(contacts = all))
            }
        }
    }

    fun allowContacts() {
        activity.handlePermission(PERMISSION_READ_CONTACTS) { granted ->
            if (granted) {
                holder.showPeopleContacts(NewMessageUiState())
                loadContacts()
            }
        }
    }

    fun apply() {
        holder.closePeople()
        val newThreadId = activity.getThreadId(people.addresses.toHashSet())
        if (visit.threadId != newThreadId) {
            Intent(activity, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, newThreadId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                activity.startActivity(this)
            }
        }
    }

    fun add(recipient: Recipient) {
        if (!people.add(recipient)) return
        holder.showPeople(people.recipients())
        holder.refreshComposer()
    }

    fun remove(recipient: Recipient) {
        if (!people.remove(recipient)) return
        holder.showPeople(people.recipients())
        holder.refreshComposer()
    }
}

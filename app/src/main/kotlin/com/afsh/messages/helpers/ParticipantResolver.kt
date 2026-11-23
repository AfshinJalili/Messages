package com.afsh.messages.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.helpers.MyContactsContentProvider
import com.afsh.commons.models.PhoneNumber
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getAddresses
import com.afsh.messages.extensions.getThreadParticipants
import com.afsh.messages.extensions.indexOfFirstOrNull
import com.afsh.messages.extensions.subscriptionManagerCompat
import com.afsh.messages.messaging.isShortCodeWithLetters
import com.afsh.messages.models.Message
import com.afsh.messages.models.SIMCard
import com.afsh.messages.ui.newmessage.Recipient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** An active SIM, as the send-SIM switcher and the timeline's SIM labels need it. */
data class SimSubscription(
    val subscriptionId: Int,
    /** The physical slot from 0, or -1 when the system does not say. */
    val slotIndex: Int,
    val displayName: String,
    val number: String,
)

/**
 * Owns who a conversation is with: the people found for the thread or the opening intent, their names
 * from private contacts, Manage people's additions and removals, and which SIM sends to them.
 *
 * Not thread-safe: the conversation screen reads and changes it from the main thread and its loaders,
 * as it did when this lived in the activity.
 */
class ParticipantResolver(
    private val source: Source,
    private val isShortCode: (String) -> Boolean = ::isShortCodeWithLetters,
) {

    /** What the resolver reads from contacts, settings and the telephony system. Blocking. */
    interface Source {
        fun threadParticipants(threadId: Long): ArrayList<SimpleContact>
        /** Contacts kept privately in Simple Contacts, with phone numbers. */
        fun privateContacts(): ArrayList<SimpleContact>
        fun activeSubscriptions(): List<SimSubscription>
        /** The system's default SMS subscription, or a negative value when there is none. */
        fun defaultSmsSubscriptionId(): Int
        /** The SIM picked by hand for [number], or 0. */
        fun preferredSimId(number: String): Int
        fun savePreferredSim(number: String, subscriptionId: Int)
    }

    var participants = ArrayList<SimpleContact>()
        private set

    /** Loaded by [loadPrivateContacts]; Manage people lists them with the address book. */
    var privateContacts: List<SimpleContact> = emptyList()
        private set

    /** Every participant's normalized numbers, in order. */
    val addresses: List<String> get() = participants.getAddresses()

    /** A letter short code, which cannot receive replies. */
    val isSpecialNumber: Boolean get() = addresses.any(isShortCode)

    /** Some participant is shown by its number, so a contact lookup may still name it. */
    val hasUnnamed: Boolean
        get() = participants.any { contact -> contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name) }

    /** Slot labels by subscription id; empty on a one-SIM phone, where a label tells the reader nothing. Read once. */
    val simLabels: Map<Int, String> by lazy {
        val sims = source.activeSubscriptions()
        if (sims.size < 2) emptyMap() else sims.associate { it.subscriptionId to "%d".format(it.slotIndex + 1) }
    }

    fun recipients(): List<Recipient> = participants.map { it.toRecipient() }

    /**
     * Finds the people once: from the first loaded message, or for a thread with no messages yet from the
     * thread's addresses with the numbers [intentNumber] spells (a number, or a JSON list of numbers).
     * True when this call found them.
     */
    fun resolve(threadId: Long, messages: List<Message>, intentNumber: String?): Boolean {
        if (participants.isNotEmpty()) return false
        participants = if (messages.isEmpty()) {
            fixParticipantNumbers(source.threadParticipants(threadId), parseIntentNumbers(intentNumber))
        } else {
            messages.first().participants
        }
        return true
    }

    /** When nobody was found, the opening intent's [number] and [name] make the one person. False without a number. */
    fun fallBackTo(name: String, number: String?): Boolean {
        if (participants.isNotEmpty()) return true
        if (number == null) return false
        participants.add(newContact(rawId = 0, name = name, number = number))
        return true
    }

    fun loadPrivateContacts() {
        privateContacts = source.privateContacts()
    }

    /**
     * Names participants shown by their number from [privateContacts], and their messages' senders with them.
     * True when any name was replaced.
     */
    fun applyPrivateNames(messages: List<Message>): Boolean {
        if (privateContacts.isEmpty()) return false
        val senderNumbersToReplace = HashMap<String, String>()
        participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
            privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first().normalizedNumber) }
                ?.apply {
                    senderNumbersToReplace[participant.phoneNumbers.first().normalizedNumber] = name
                    participant.name = name
                    participant.photoUri = photoUri
                }
        }

        messages.forEach { message ->
            if (senderNumbersToReplace.keys.contains(message.senderName)) {
                message.senderName = senderNumbersToReplace[message.senderName]!!
            }
        }
        return senderNumbersToReplace.isNotEmpty()
    }

    /** Manage people: adds [recipient] unless their number is already in. True when added. */
    fun add(recipient: Recipient): Boolean {
        if (participants.any { it.toRecipient().number == recipient.number }) return false
        participants.add(newContact(rawId = recipient.number.hashCode(), name = recipient.name, number = recipient.number))
        return true
    }

    /** Manage people: removes [recipient]. The conversation's own person stays. True when removed. */
    fun remove(recipient: Recipient): Boolean {
        if (participants.firstOrNull()?.toRecipient() == recipient) return false
        participants = participants.filterNot { it.toRecipient() == recipient }.toCollection(ArrayList())
        return true
    }

    /** The send SIM was switched by hand: it is remembered for every participant's number. */
    fun chooseSim(subscriptionId: Int) {
        addresses.forEach { source.savePreferredSim(it, subscriptionId) }
    }

    /**
     * The send-SIM switcher's cards and the one to start on, or null on a one-SIM phone or before anyone is
     * found. The start is the SIM picked for the first number, else the one [lastMessage] came in on, else
     * the system default, else the first.
     */
    fun sendSims(lastMessage: Message?): Pair<List<SIMCard>, Int>? {
        val sims = source.activeSubscriptions()
        if (sims.size < 2) return null
        val cards = sims.mapIndexed { index, sim ->
            val label = if (sim.number.isNotEmpty()) "${sim.displayName} (${sim.number})" else sim.displayName
            // The physical slot, as the timeline's SIM labels use; the list position only orders the toggle.
            val slot = sim.slotIndex.takeIf { it >= 0 }?.plus(1) ?: (index + 1)
            SIMCard(slot, sim.subscriptionId, label)
        }
        val numbers = addresses
        if (numbers.isEmpty()) return null
        return cards to properSimIndex(sims, numbers.first(), lastMessage)
    }

    private fun properSimIndex(sims: List<SimSubscription>, firstNumber: String, lastMessage: Message?): Int {
        val userPreferredSimId = source.preferredSimId(firstNumber)
        val userPreferredSimIdx = sims.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            sims.indexOfFirstOrNull { it.subscriptionId == lastMessage.subscriptionId }
        } else {
            null
        }

        val defaultSmsSubscriptionId = source.defaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            sims.indexOfFirstOrNull { it.subscriptionId == defaultSmsSubscriptionId }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
    }

    /** Who a scheduled message goes to: the conversation's own title, else each person's name or number. */
    fun scheduleName(conversationTitle: String?): String =
        conversationTitle?.takeIf { it.isNotEmpty() }
            ?: participants.joinToString { it.name.ifEmpty { it.phoneNumbers.firstOrNull()?.value.orEmpty() } }

    companion object {
        fun from(context: Context): ParticipantResolver = ParticipantResolver(AndroidSource(context.applicationContext))
    }

    private class AndroidSource(private val context: Context) : Source {
        override fun threadParticipants(threadId: Long) = context.getThreadParticipants(threadId, null)

        override fun privateContacts(): ArrayList<SimpleContact> {
            val cursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            return MyContactsContentProvider.getSimpleContacts(context, cursor)
        }

        @SuppressLint("MissingPermission")
        override fun activeSubscriptions() = context.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty().map {
            SimSubscription(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty(), it.number.orEmpty())
        }

        override fun defaultSmsSubscriptionId() = SmsManager.getDefaultSmsSubscriptionId()

        override fun preferredSimId(number: String) = context.config.getUseSIMIdAtNumber(number)

        override fun savePreferredSim(number: String, subscriptionId: Int) = context.config.saveUseSIMIdAtNumber(number, subscriptionId)
    }
}

/** The person Manage people shows for this contact: its first number and its name. */
fun SimpleContact.toRecipient(): Recipient {
    val phone = phoneNumbers.firstOrNull()
    return Recipient(phone?.normalizedNumber?.ifEmpty { phone.value } ?: "", name)
}

/** The numbers an opening intent names: one number, or a JSON list of them. */
internal fun parseIntentNumbers(numberFromIntent: String?): ArrayList<String> {
    val numbers = ArrayList<String>()
    if (numberFromIntent != null) {
        if (numberFromIntent.startsWith('[') && numberFromIntent.endsWith(']')) {
            val type = object : TypeToken<List<String>>() {}.type
            numbers.addAll(Gson().fromJson<List<String>>(numberFromIntent, type))
        } else {
            numbers.add(numberFromIntent)
        }
    }
    return numbers
}

/** Gives [participants] the exact spelling of [properNumbers] (the provider drops the "+"). */
internal fun fixParticipantNumbers(
    participants: ArrayList<SimpleContact>,
    properNumbers: List<String>,
): ArrayList<SimpleContact> {
    for (number in properNumbers) {
        for (participant in participants) {
            participant.phoneNumbers = participant.phoneNumbers.mapTo(ArrayList()) {
                val numberWithoutPlus = number.replace("+", "")
                if (numberWithoutPlus == it.normalizedNumber.trim()) {
                    if (participant.name == it.normalizedNumber) {
                        participant.name = number
                    }
                    PhoneNumber(number, 0, "", number)
                } else {
                    PhoneNumber(it.normalizedNumber, 0, "", it.normalizedNumber)
                }
            }
        }
    }
    return participants
}

private fun newContact(rawId: Int, name: String, number: String) = SimpleContact(
    rawId = rawId,
    contactId = rawId,
    name = name,
    photoUri = "",
    phoneNumbers = arrayListOf(PhoneNumber(number, 0, "", number)),
    birthdays = ArrayList(),
    anniversaries = ArrayList(),
)

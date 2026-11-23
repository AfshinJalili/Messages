package com.afsh.messages.helpers

import com.afsh.commons.models.PhoneNumber
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.models.Message
import com.afsh.messages.models.SIMCard
import com.afsh.messages.ui.newmessage.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun contact(name: String, vararg numbers: String) = SimpleContact(
    rawId = 0, contactId = 0, name = name, photoUri = "",
    phoneNumbers = numbers.mapTo(ArrayList()) { PhoneNumber(it, 0, "", it) },
    birthdays = ArrayList(), anniversaries = ArrayList(),
)

private const val INBOX = 1
private const val SENT = 2

internal fun sentOrReceived(id: Long, type: Int, subscriptionId: Int, participants: ArrayList<SimpleContact> = ArrayList()) = Message(
    id = id, body = "m$id", type = type, status = -1, participants = participants, date = id.toInt(), read = true, threadId = 1,
    isMMS = false, attachment = null, senderPhoneNumber = "", senderName = "", senderPhotoUri = "", subscriptionId = subscriptionId,
)

internal class FakeParticipantSource : ParticipantResolver.Source {
    var threadParticipants = ArrayList<SimpleContact>()
    var subscriptions = listOf<SimSubscription>()
    var defaultSms = -1
    val preferred = HashMap<String, Int>()
    var subscriptionReads = 0

    override fun threadParticipants(threadId: Long) = threadParticipants
    override fun privateContacts() = ArrayList<SimpleContact>()
    override fun activeSubscriptions(): List<SimSubscription> {
        subscriptionReads++
        return subscriptions
    }
    override fun defaultSmsSubscriptionId() = defaultSms
    override fun preferredSimId(number: String) = preferred[number] ?: 0
    override fun savePreferredSim(number: String, subscriptionId: Int) {
        preferred[number] = subscriptionId
    }
}

class ParticipantResolverTest {
    private val source = FakeParticipantSource()
    private val people = ParticipantResolver(source, isShortCode = { address -> address.any { it.isLetter() } })

    private val twoSims = listOf(
        SimSubscription(subscriptionId = 11, slotIndex = 0, displayName = "Irancell", number = ""),
        SimSubscription(subscriptionId = 22, slotIndex = 1, displayName = "MCI", number = "0912"),
    )

    @Test
    fun `a new thread takes its people from the thread and the plus sign from the intent`() {
        source.threadParticipants = arrayListOf(contact("989121234567", "989121234567"))

        assertTrue(people.resolve(threadId = 5, messages = emptyList(), intentNumber = "+989121234567"))

        val only = people.participants.single()
        assertEquals("+989121234567", only.name)
        assertEquals(PhoneNumber("+989121234567", 0, "", "+989121234567"), only.phoneNumbers.single())
    }

    @Test
    fun `a group intent lists its numbers as JSON`() {
        source.threadParticipants = arrayListOf(contact("Mina", "111"), contact("222", "222"))

        people.resolve(threadId = 5, messages = emptyList(), intentNumber = "[\"+111\",\"+222\"]")

        assertEquals(listOf("+111", "+222"), people.addresses)
        assertEquals("Mina", people.participants[0].name)
        assertEquals("+222", people.participants[1].name)
    }

    @Test
    fun `loaded messages name the people, once`() {
        val mina = arrayListOf(contact("Mina", "111"))

        assertTrue(people.resolve(5, listOf(sentOrReceived(1, INBOX, 11, mina)), null))
        assertFalse(people.resolve(5, listOf(sentOrReceived(2, INBOX, 11, arrayListOf(contact("Other", "999")))), null))

        assertEquals(listOf("Mina"), people.participants.map { it.name })
    }

    @Test
    fun `with nobody found the intent's number and title make the person`() {
        assertFalse(people.fallBackTo(name = "Mina", number = null))
        assertTrue(people.fallBackTo(name = "Mina", number = "0912"))

        assertEquals(listOf(Recipient("0912", "Mina")), people.recipients())
    }

    @Test
    fun `a number already found stays as it is`() {
        people.resolve(5, listOf(sentOrReceived(1, INBOX, 11, arrayListOf(contact("Mina", "111")))), null)

        assertTrue(people.fallBackTo(name = "Other", number = "999"))
        assertEquals(listOf("Mina"), people.participants.map { it.name })
    }

    @Test
    fun `a number shown as its name has no name yet`() {
        people.resolve(5, listOf(sentOrReceived(1, INBOX, 11, arrayListOf(contact("111", "111")))), null)
        assertTrue(people.hasUnnamed)
    }

    @Test
    fun `people are added once, and the conversation's own person cannot be removed`() {
        people.fallBackTo("Mina", "111")

        assertTrue(people.add(Recipient("222", "Sara")))
        assertFalse(people.add(Recipient("222", "Sara again")))
        assertFalse(people.remove(Recipient("111", "Mina")))
        assertTrue(people.remove(Recipient("222", "Sara")))

        assertEquals(listOf(Recipient("111", "Mina")), people.recipients())
    }

    @Test
    fun `a letter short code is a special number`() {
        people.fallBackTo("Bank", "MyBank")
        assertTrue(people.isSpecialNumber)
    }

    @Test
    fun `a hand-picked SIM is remembered for every number`() {
        people.resolve(5, listOf(sentOrReceived(1, INBOX, 11, arrayListOf(contact("A", "111"), contact("B", "222")))), null)

        people.chooseSim(22)

        assertEquals(mapOf("111" to 22, "222" to 22), source.preferred)
    }

    @Test
    fun `the send SIMs are labelled by slot, and the switcher needs two`() {
        people.fallBackTo("Mina", "111")
        source.subscriptions = twoSims.take(1)
        assertNull(people.sendSims(lastMessage = null))

        source.subscriptions = twoSims
        val (cards, _) = people.sendSims(lastMessage = null)!!
        assertEquals(listOf(SIMCard(1, 11, "Irancell"), SIMCard(2, 22, "MCI (0912)")), cards)
    }

    @Test
    fun `a SIM without a slot is numbered by its place`() {
        people.fallBackTo("Mina", "111")
        source.subscriptions = listOf(twoSims[0].copy(slotIndex = -1), twoSims[1].copy(slotIndex = -1))

        assertEquals(listOf(1, 2), people.sendSims(null)!!.first.map { it.id })
    }

    @Test
    fun `no number means no switcher`() {
        source.subscriptions = twoSims
        assertNull(people.sendSims(null))
    }

    @Test
    fun `the send SIM is the remembered one, then the sender's, then the system default, then the first`() {
        people.fallBackTo("Mina", "111")
        source.subscriptions = twoSims
        val received = sentOrReceived(1, INBOX, subscriptionId = 22)
        val sent = sentOrReceived(2, SENT, subscriptionId = 22)

        assertEquals(0, people.sendSims(sent)!!.second)
        source.defaultSms = 22
        assertEquals(1, people.sendSims(sent)!!.second)
        source.defaultSms = 11
        assertEquals(1, people.sendSims(received)!!.second)
        source.preferred["111"] = 11
        assertEquals(0, people.sendSims(received)!!.second)
    }

    @Test
    fun `the timeline labels SIMs by slot only on a two-SIM phone, read once`() {
        source.subscriptions = twoSims.take(1)
        assertEquals(emptyMap<Int, String>(), people.simLabels)

        val twoSimPhone = ParticipantResolver(FakeParticipantSource().apply { subscriptions = twoSims })
        assertEquals(mapOf(11 to "1", 22 to "2"), twoSimPhone.simLabels)
        people.simLabels
        assertEquals(1, source.subscriptionReads)
    }

    @Test
    fun `a schedule is addressed to the conversation's title, else to names or numbers`() {
        people.fallBackTo("", "111")
        people.add(Recipient("222", "Sara"))

        assertEquals("Group", people.scheduleName(conversationTitle = "Group"))
        assertEquals("111, Sara", people.scheduleName(conversationTitle = ""))
        assertEquals("111, Sara", people.scheduleName(conversationTitle = null))
    }
}

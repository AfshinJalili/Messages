package com.afsh.messages.ui.thread

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.afsh.messages.models.Message
import com.afsh.messages.models.SIMCard
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.newmessage.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val INBOX = 1
private const val SENT = 2
private const val FAILED = 5

internal fun message(id: Long, type: Int = INBOX, read: Boolean = true, date: Int = id.toInt() * 60, body: String = "m$id", isMms: Boolean = false) = Message(
    id = id, body = body, type = type, status = -1, participants = ArrayList(), date = date, read = read, threadId = 1,
    isMMS = isMms, attachment = null, senderPhoneNumber = "", senderName = "", senderPhotoUri = "", subscriptionId = 1,
)

private class FakeHost : ThreadStateHolder.Host {
    val events = mutableListOf<Any>()
    var keyboardHides = 0
    var viewportChecks = 0
    var backEnabled = false
    var chosenSim: Int? = null
    var mms = false
    var counter: Pair<Int, Int>? = null

    override fun onEvent(event: ThreadEvent) { events += event }
    override fun onComposerEvent(event: ComposerEvent) { events += event }
    override fun onPeopleEvent(event: PeopleEvent) { events += event }
    override fun hideKeyboard() { keyboardHides++ }
    override fun viewportChanged() { viewportChecks++ }
    override fun backEnabledChanged(enabled: Boolean) { backEnabled = enabled }
    override fun simChosen(subscriptionId: Int) { chosenSim = subscriptionId }
    override fun isMms(text: String, hasAttachments: Boolean) = mms || hasAttachments
    override fun smsLength(text: String) = counter
}

class ThreadStateHolderTest {
    private val host = FakeHost()
    private val holder = ThreadStateHolder(host)

    private fun show(vararg messages: Message) =
        holder.showItems(messages.toList(), starred = emptySet(), spamReasons = emptyMap(), simLabels = emptyMap(), empty = false)

    // Selection

    @Test
    fun `toggling selects and unselects, and starting a selection hides the keyboard`() {
        val a = message(1)
        show(a, message(2))
        holder.onEvent(ThreadEvent.ToggleSelection(a))
        assertEquals(setOf(a.getStableId()), holder.ui.selected)
        assertEquals(1, host.keyboardHides)
        assertTrue(host.backEnabled)

        holder.onEvent(ThreadEvent.ToggleSelection(a))
        assertTrue(holder.ui.selected.isEmpty())
        assertFalse(host.backEnabled)
    }

    @Test
    fun `select all takes every message and stays out of the host`() {
        val a = message(1)
        val b = message(2)
        show(a, b)
        holder.onEvent(ThreadEvent.Act(MessageAction.SELECT_ALL, listOf(a)))
        assertEquals(setOf(a.getStableId(), b.getStableId()), holder.ui.selected)
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun `other message actions go to the host`() {
        val a = message(1)
        holder.onEvent(ThreadEvent.Act(MessageAction.COPY, listOf(a)))
        assertEquals(listOf<Any>(ThreadEvent.Act(MessageAction.COPY, listOf(a))), host.events)
    }

    @Test
    fun `new items drop the selection of messages that are gone`() {
        val a = message(1)
        val b = message(2)
        show(a, b)
        holder.onEvent(ThreadEvent.ToggleSelection(a))
        holder.onEvent(ThreadEvent.ToggleSelection(b))
        show(b)
        assertEquals(setOf(b.getStableId()), holder.ui.selected)
    }

    // Back

    @Test
    fun `back closes people, then the selection, then search`() {
        val a = message(1)
        show(a)
        holder.openSearch()
        holder.onEvent(ThreadEvent.ToggleSelection(a))
        holder.openPeople(listOf(Recipient("+1", "A")))

        assertTrue(holder.back())
        assertFalse(holder.people.open)
        assertTrue(holder.ui.selecting)

        assertTrue(holder.back())
        assertFalse(holder.ui.selecting)
        assertTrue(holder.ui.searching)

        assertTrue(holder.back())
        assertFalse(holder.ui.searching)
        assertFalse(host.backEnabled)
        assertFalse(holder.back())
    }

    // Search

    @Test
    fun `search steps to the next older match and wraps`() {
        val matches = listOf(message(3), message(2), message(1))
        assertEquals(3L, holder.stepSearch(matches)!!.id)
        assertEquals(2L, holder.stepSearch(matches)!!.id)
        assertEquals(1L, holder.stepSearch(matches)!!.id)
        assertEquals(3L, holder.stepSearch(matches)!!.id)
    }

    @Test
    fun `a changed query starts from the newest match again`() {
        val matches = listOf(message(3), message(2))
        holder.stepSearch(matches)
        holder.onEvent(ThreadEvent.SearchChanged)
        assertEquals(3L, holder.stepSearch(matches)!!.id)
        assertNull(holder.stepSearch(emptyList()))
    }

    // Landing, jumps and read state

    @Test
    fun `landing waits for the provider and runs once`() {
        var asked = 0
        holder.land(ready = false) { asked++; InitialScroll.FirstUnread }
        assertEquals(0, asked)
        holder.land(ready = true) { asked++; InitialScroll.FirstUnread }
        holder.land(ready = true) { asked++; null }
        assertEquals(1, asked)
        assertEquals(InitialScroll.FirstUnread, holder.ui.initialScroll)
        assertFalse(holder.readsAtBottom())

        holder.onEvent(ThreadEvent.InitialScrollSettled)
        assertNull(holder.ui.initialScroll)
        assertTrue(holder.readsAtBottom())
    }

    @Test
    fun `nothing to land on settles at once`() {
        holder.land(ready = true) { null }
        assertTrue(holder.readsAtBottom())
    }

    @Test
    fun `a jump cancels the landing and only its own scroll settles it`() {
        holder.startJump()
        holder.land(ready = true) { InitialScroll.FirstUnread }
        assertNull(holder.ui.initialScroll)
        assertTrue(holder.isJumping)

        holder.scrollToJump(7, isMms = false)
        val first = holder.ui.scrollRequest as ScrollRequest.ToMessage
        holder.scrollToJump(8, isMms = false)
        val second = holder.ui.scrollRequest as ScrollRequest.ToMessage

        holder.onEvent(ThreadEvent.JumpSettled(first.nonce))
        assertTrue(holder.isJumping)
        holder.onEvent(ThreadEvent.JumpSettled(second.nonce))
        assertFalse(holder.isJumping)
        assertTrue(holder.readsAtBottom())
    }

    @Test
    fun `older pages wait while a jump walks pages`() {
        holder.startJump()
        holder.onEvent(ThreadEvent.LoadOlder)
        assertTrue(host.events.isEmpty())
        holder.jumpFailed()
        holder.onEvent(ThreadEvent.LoadOlder)
        assertEquals(listOf<Any>(ThreadEvent.LoadOlder), host.events)
    }

    @Test
    fun `scrolling away or still scrolling is not reading`() {
        holder.land(ready = true) { null }
        holder.onEvent(ThreadEvent.Viewport(atBottom = true, scrolling = true))
        assertFalse(holder.readsAtBottom())
        assertTrue(holder.readsAtBottom(includeScrolling = true))
        holder.onEvent(ThreadEvent.Viewport(atBottom = false, scrolling = false))
        assertFalse(holder.readsAtBottom(includeScrolling = true))
        assertEquals(2, host.viewportChecks)
    }

    @Test
    fun `each scroll request is a new value`() {
        holder.scrollToBottom()
        val first = holder.ui.scrollRequest
        holder.scrollToBottom()
        assertTrue(first != holder.ui.scrollRequest)
    }

    @Test
    fun `a retry runs once until the message leaves the failed state`() {
        val failed = message(1, type = FAILED)
        assertTrue(holder.startRetry(failed))
        assertFalse(holder.startRetry(failed))
        show(failed)
        assertFalse(holder.startRetry(failed))
        show(failed.copy(type = SENT))
        assertTrue(holder.startRetry(failed))
    }

    // SIM

    @Test
    fun `the SIM switcher toggles round and remembers the choice`() {
        holder.showSims(listOf(SIMCard(1, 10, "A"), SIMCard(2, 20, "B")), index = 1)
        assertEquals(20, holder.sendSubscriptionId)
        assertEquals(2, holder.ui.sendSim)

        holder.onComposerEvent(ComposerEvent.NextSim)
        assertEquals(10, holder.sendSubscriptionId)
        assertEquals(0, holder.composer.simIndex)
        assertEquals(1, holder.ui.sendSim)
        assertEquals(10, host.chosenSim)
    }

    @Test
    fun `one SIM has nothing to switch`() {
        holder.onEvent(ThreadEvent.NextSim)
        assertNull(host.chosenSim)
        assertNull(holder.sendSubscriptionId)
    }

    // Composer

    @Test
    fun `the counter shows SMS parts, and nothing for MMS or empty text`() {
        host.counter = 2 to 40
        holder.setText("hello")
        assertEquals(2, holder.composer.smsParts)
        assertEquals(40, holder.composer.smsLeft)
        assertFalse(holder.composer.isMms)

        host.mms = true
        holder.setText("hello!")
        assertTrue(holder.composer.isMms)
        assertEquals(0, holder.composer.smsParts)

        holder.setText("")
        assertFalse(holder.composer.isMms)
    }

    @Test
    fun `moving the cursor alone does not remeasure`() {
        holder.setText("abc")
        host.counter = 1 to 1
        holder.onComposerTextChange(TextFieldValue("abc", TextRange(1)))
        assertEquals(0, holder.composer.smsParts)
    }

    @Test
    fun `dictation inserts at the cursor`() {
        holder.onComposerTextChange(TextFieldValue("ac", TextRange(1)))
        holder.insertAtCursor("b")
        assertEquals("abc", holder.text)
        assertEquals(TextRange(2), holder.composerText.selection)
    }

    @Test
    fun `cancelling the schedule clears it and stays out of the host`() {
        holder.setSchedule(1000, "Tomorrow")
        assertEquals("Tomorrow", holder.composer.scheduledAt)
        holder.onComposerEvent(ComposerEvent.CancelSchedule)
        assertNull(holder.scheduledMillis)
        assertNull(holder.composer.scheduledAt)
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun `sending goes to the host`() {
        holder.onComposerEvent(ComposerEvent.Send)
        assertEquals(listOf<Any>(ComposerEvent.Send), host.events)
    }

    // Header

    @Test
    fun `facts set the header, the greeting and the composer`() {
        holder.showFacts(ThreadFacts(title = "Mina Farahani", participantCount = 1, firstNumber = "+98912", firstName = "Mina Farahani", senderBlocked = true))
        assertEquals("Mina Farahani", holder.ui.header.title)
        assertEquals("+98912", holder.ui.header.subtitle)
        assertEquals("Mina", holder.ui.firstName)
        assertTrue(holder.ui.header.canDial)
        assertTrue(holder.composer.blocked)
        assertTrue(holder.composer.visible)
    }

    @Test
    fun `the opening title stays until participants have a title`() {
        holder.showTitle("From intent")
        holder.showFacts(ThreadFacts())
        assertEquals("From intent", holder.ui.header.title)
        assertTrue(holder.ui.header.actions.isEmpty())
    }

    @Test
    fun `the Recycle Bin and a pending delete hide the composer`() {
        holder.showFacts(ThreadFacts(participantCount = 1, isRecycleBin = true))
        assertFalse(holder.composer.visible)
        holder.showFacts(ThreadFacts(participantCount = 1, deletePending = true))
        assertFalse(holder.composer.visible)
    }

    @Test
    fun `a short code cannot be replied to or greeted`() {
        holder.showFacts(ThreadFacts(title = "BANK", participantCount = 1, firstNumber = "BANK", specialNumber = true, specialSubtitle = "Automated"))
        assertFalse(holder.composer.canReply)
        assertEquals("", holder.ui.firstName)
        assertEquals("Automated", holder.ui.header.subtitle)
        assertFalse(holder.ui.header.canDial)
    }
}

class ThreadHeaderRulesTest {
    private val single = ThreadFacts(title = "+1555", participantCount = 1, firstNumber = "+1555", firstName = "+1555", hasConversation = true, archived = false, hasItems = true)

    @Test
    fun `an unsaved number offers adding to contacts and blocking`() {
        val actions = threadMenuActions(single)
        assertTrue(ThreadMenuAction.ADD_TO_CONTACT in actions)
        assertTrue(ThreadMenuAction.BLOCK in actions)
        assertTrue(ThreadMenuAction.ARCHIVE in actions)
        assertFalse(ThreadMenuAction.ALLOW_SENDER in actions)
        assertFalse(ThreadMenuAction.RENAME in actions)
        assertEquals(ThreadMenuAction.entries.filter { it in actions }, actions)
    }

    @Test
    fun `a blocked sender is allowed rather than blocked`() {
        val actions = threadMenuActions(single.copy(senderBlocked = true))
        assertTrue(ThreadMenuAction.ALLOW_SENDER in actions)
        assertFalse(ThreadMenuAction.BLOCK in actions)
    }

    @Test
    fun `the Recycle Bin offers only restore and delete`() {
        assertEquals(listOf(ThreadMenuAction.RESTORE, ThreadMenuAction.DELETE), threadMenuActions(single.copy(isRecycleBin = true)))
    }

    @Test
    fun `groups are renamed and say how many people`() {
        val group = ThreadFacts(title = "Family", participantCount = 3, hasConversation = true, groupSubtitle = "3 people")
        assertTrue(ThreadMenuAction.RENAME in threadMenuActions(group))
        assertEquals("3 people", threadHeader(group, "").subtitle)
        assertTrue(threadHeader(group, "").isGroup)
        assertEquals("", greetingName(group, "Family"))
    }

    @Test
    fun `a number is not a name to greet`() {
        assertEquals("", greetingName(single, "+1555"))
    }

    @Test
    fun `muting toggles which of mute and unmute shows`() {
        assertTrue(ThreadMenuAction.MUTE in threadMenuActions(single))
        val muted = threadMenuActions(single.copy(muted = true))
        assertTrue(ThreadMenuAction.UNMUTE in muted)
        assertFalse(ThreadMenuAction.MUTE in muted)
    }

    @Test
    fun `landing goes to the first unread, or to spam when opened from Spam`() {
        val unread = message(2, read = false)
        val items = listOf(message(1), ThreadItem.ThreadUnreadSeparator, unread)
        assertEquals(InitialScroll.FirstUnread, landing(isRecycleBin = false, openSpam = false, items = items) { false })
        assertNull(landing(isRecycleBin = true, openSpam = false, items = items) { false })
        assertNull(landing(isRecycleBin = false, openSpam = false, items = listOf(message(1))) { false })

        val spamRead = message(3)
        val spamUnread = message(4, read = false)
        val spam = setOf(3L, 4L)
        assertEquals(InitialScroll.Message(4, false), landing(false, true, listOf(spamRead, spamUnread)) { it.id in spam })
        assertEquals(InitialScroll.Message(3, false), landing(false, true, listOf(spamRead)) { it.id in spam })
    }
}

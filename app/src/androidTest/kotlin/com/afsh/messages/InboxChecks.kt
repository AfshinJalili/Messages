package com.afsh.messages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.extensions.extractOtpCode
import com.afsh.messages.helpers.InboxFilter
import com.afsh.messages.helpers.SwipeAction
import com.afsh.messages.helpers.category
import com.afsh.messages.helpers.sortedForInbox
import com.afsh.messages.helpers.swipeAction
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ConversationRow
import com.afsh.messages.ui.components.SwipeGesture
import com.afsh.messages.ui.components.SwipeTone
import com.afsh.messages.ui.components.SwipeableRow
import com.afsh.messages.ui.components.TopDestination
import com.afsh.messages.ui.inbox.InboxAction
import com.afsh.messages.ui.inbox.InboxSection
import com.afsh.messages.ui.inbox.InboxUiState
import com.afsh.messages.ui.inbox.PRIMARY_ACTION_COUNT
import com.afsh.messages.ui.inbox.availableActions
import com.afsh.messages.ui.inbox.section
import com.afsh.messages.ui.inbox.splitForBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** In-memory rows, throwaway settings and a throwaway database only; the real inbox and settings are never touched. */
@RunWith(AndroidJUnit4::class)
class InboxChecks {
    // An activity rule so assertions resolve strings in the same locale the screen renders in (the
    // test phone runs Persian, which also localizes digits).
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun row(id: Long, title: String, read: Boolean = true, pinned: Boolean = false, muted: Boolean = false, isGroup: Boolean = false, phone: String = "+15551234567") =
        inboxRow(fixtureConversation(id, title, read = read, phone = phone, isGroup = isGroup), pinned = pinned, muted = muted)

    /** Selection swaps the search field for same-height controls; rows must not move under the finger. */
    @Test
    fun selectionKeepsRowsInPlace() {
        var state by mutableStateOf(InboxUiState(rows = listOf(row(1, "Mina"), row(2, "Dad", read = false))))
        compose.setContent {
            AfshTheme(dark = false) {
                TestInbox(state)
            }
        }
        val dadRow = hasContentDescription("Dad", substring = true)
        val before = compose.onNode(dadRow).getUnclippedBoundsInRoot().top
        state = state.copy(selected = setOf(1L))
        compose.onNodeWithText(compose.activity.quantity(R.plurals.inbox_selected, 1)).assertExists()
        check(compose.onNode(dadRow).getUnclippedBoundsInRoot().top == before)
    }

    /** uiautomator clips bounds at the system bar; measure the real Compose touch targets instead. */
    @Test
    fun navigationTargetsAreAtLeast48dp() {
        compose.setContent {
            AfshTheme(dark = false) {
                TestInbox(InboxUiState(rows = listOf(row(1, "Mina"))))
            }
        }
        TopDestination.entries.forEach { compose.onNode(isNavigationItem(text(it.label))).assertHeightIsAtLeast(48.dp) }
        listOf(InboxFilter.ALL.label, InboxFilter.UNREAD.label).forEach {
            compose.onNodeWithText(text(it)).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun selectionActions() {
        val person = row(1, "Mina", read = false)
        val group = row(2, "Book club", pinned = true, muted = true, isGroup = true)
        val business = row(3, "FILIMO", phone = "FILIMO")

        val single = availableActions(listOf(person), archiveAvailable = true)
        check(InboxAction.DIAL in single && InboxAction.COPY_NUMBER in single && InboxAction.RENAME !in single)
        check(InboxAction.MARK_READ in single && InboxAction.MARK_UNREAD !in single)
        check(InboxAction.RENAME in availableActions(listOf(group), archiveAvailable = true))
        check(InboxAction.DIAL !in availableActions(listOf(business), archiveAvailable = true)) { "Letter sender ids cannot be dialled" }

        val both = availableActions(listOf(person, group), archiveAvailable = false)
        check(InboxAction.ARCHIVE !in both && InboxAction.DETAILS !in both)
        check(InboxAction.MARK_READ in both && InboxAction.MARK_UNREAD in both) {
            "Mixed read state should allow normalizing the selection in either direction"
        }
        // mixed selection: pin/mute whichever is missing, never offer the inverse
        check(InboxAction.PIN in both && InboxAction.UNPIN !in both && InboxAction.MUTE in both && InboxAction.UNMUTE !in both)

        val (bar, overflow) = both.splitForBar()
        check(bar.last() == InboxAction.DELETE && bar.size <= PRIMARY_ACTION_COUNT + 1)
        check((bar + overflow).toSet() == both.toSet() && bar.none { it in overflow })
        check(availableActions(emptyList(), archiveAvailable = true).isEmpty())
    }

    @Test
    fun inboxSections() {
        val now = Calendar.getInstance()
        val seconds = (now.timeInMillis / 1000).toInt()
        fun at(daysAgo: Int, pinned: Boolean = false) =
            row(1, "x", pinned = pinned).let { it.copy(conversation = it.conversation.copy(date = seconds - daysAgo * 86_400)) }
        check(at(0).section(now) == InboxSection.TODAY)
        check(at(1).section(now) == InboxSection.YESTERDAY)
        check(at(9).section(now) == InboxSection.EARLIER)
        check(at(9, pinned = true).section(now) == InboxSection.PINNED)
        val futureScheduled = at(-2).let { row ->
            row.copy(conversation = row.conversation.copy(isScheduled = true))
        }
        check(futureScheduled.section(now) == InboxSection.TODAY) {
            "Future scheduled sends stay in date order without a separate section"
        }
    }

    @Test
    fun messageMetadataSeparatesSmsAndMms() = IsolatedPreferences("metadata-regression").use { prefs ->
        val config = prefs.config
        val sms = fixtureMessage(123, "test", threadId = 0, date = 0, read = false, address = "test", senderName = "test")
        val mms = sms.copy(isMMS = true)
        config.setMessageStarred(sms.id, sms.isMMS, true)
        check(config.isMessageStarred(sms.id, sms.isMMS))
        check(!config.isMessageStarred(mms.id, mms.isMMS)) { "Starring SMS also stars unrelated MMS with the same provider ID" }
        config.setMessageReaction(sms.id, sms.isMMS, "👍")
        check(config.getMessageReaction(sms.id, sms.isMMS) == "👍")
        check(config.getMessageReaction(mms.id, mms.isMMS) == null) { "SMS reaction leaks to MMS" }
        config.setMessageStarred(mms.id, mms.isMMS, true)
        config.setMessageStarred(sms.id, sms.isMMS, false)
        check(config.isMessageStarred(mms.id, mms.isMMS))
        check(!config.isMessageStarred(sms.id, sms.isMMS))
        config.setMessageReaction(mms.id, mms.isMMS, "❤️")
        config.setMessageReaction(sms.id, sms.isMMS, null)
        check(config.getMessageReaction(mms.id, mms.isMMS) == "❤️")
        check(config.getMessageReaction(sms.id, sms.isMMS) == null)
    }

    private fun conversation(
        title: String,
        phoneNumber: String,
        snippet: String = "hi",
        threadId: Long = 1,
        date: Int = 1,
        isGroup: Boolean = false,
    ) = fixtureConversation(threadId, title, snippet = snippet, date = date, phone = phoneNumber, isGroup = isGroup)

    @Test
    fun conversationStateDiff() {
        val original = conversation("Sender", "123", snippet = "Preview")
        check(!Conversation.areContentsTheSame(original, original.copy(isArchived = true)))
        check(!Conversation.areContentsTheSame(original, original.copy(isScheduled = true)))
    }

    @Test
    fun inboxCategories() {
        check(conversation("Mom", "+15551234567").category() == InboxFilter.PERSONAL)
        check(conversation("Book club", "+1555|+1666", isGroup = true).category() == InboxFilter.PERSONAL)
        check(conversation("+15551234567", "+15551234567").category() == InboxFilter.UNKNOWN)
        check(conversation("FILIMO", "FILIMO").category() == InboxFilter.BUSINESS)
        check(conversation("12345", "12345").category() == InboxFilter.BUSINESS)
        val otp = conversation("+15551234567", "+15551234567", snippet = "Your verification code is 123456")
        check(otp.category() == InboxFilter.BUSINESS)
        check(InboxFilter.UNREAD.matches(otp.copy(read = false)))
        check(!InboxFilter.UNREAD.matches(otp))
    }

    @Test
    fun pinnedConversationsKeepPinOrder() = IsolatedPreferences("inbox-pins").use { prefs ->
        val config = prefs.config
        config.pinnedConversations = linkedSetOf("3", "1")
        config.addPinnedConversationByThreadId(2)
        check(config.pinnedConversations.toList() == listOf("3", "1", "2"))

        val sorted = listOf(
            conversation("a", "1", threadId = 1, date = 50),
            conversation("b", "2", threadId = 2, date = 10),
            conversation("c", "3", threadId = 3, date = 99),
            conversation("d", "4", threadId = 4, date = 100),
        ).sortedForInbox(config.pinnedConversations)
        // newest pin first, then the rest by date; recency never reorders the pinned block
        check(sorted.map { it.threadId } == listOf(2L, 1L, 3L, 4L))
    }

    /** At 1.3× text the name wraps, the preview stays one line, and nothing leaves the row (R2-15, D15). */
    @Test
    fun conversationRowLayout() {
        var rtl by mutableStateOf(false)
        val row = row(1, "FILIMO subscription and account notices", read = false, pinned = true, muted = true, phone = "FILIMO")
            .let { it.copy(conversation = it.conversation.copy(snippet = "برای ورود کد تایید را وارد کنید. Your verification code is 123456.", unreadCount = 120)) }
        compose.setContent {
            TestTheme(rtl = rtl, fontScale = 1.3f) {
                Box(Modifier.width(360.dp)) {
                    ConversationRow(row, selected = false, selecting = false, onClick = {}, onLongClick = {})
                }
            }
        }
        for (direction in listOf(false, true)) {
            rtl = direction
            compose.waitForIdle()
            val rowBounds = compose.onNode(hasContentDescription("FILIMO", substring = true)).getUnclippedBoundsInRoot()
            val preview = compose.onNodeWithText("Your verification code", substring = true, useUnmergedTree = true)
            check(preview.textLayout().lineCount == 1) { "Preview wraps (rtl=$direction)" }
            val previewBounds = preview.getUnclippedBoundsInRoot()
            check(previewBounds inside rowBounds) { "Preview leaves the row (rtl=$direction): $previewBounds in $rowBounds" }
            compose.onNodeWithText(compose.activity.localizedNumber(120), useUnmergedTree = true).assertExists()
        }
    }

    /**
     * Swipe settings are physical: a leftward swipe reports left under RTL too, and a short fast
     * flick never commits Delete or Archive.
     */
    @Test
    fun swipeRowDirectionsAndFlicks() {
        var rtl by mutableStateOf(false)
        val swipes = mutableListOf<Boolean>()
        val gesture = SwipeGesture(R.string.archive, AfshIcon.Archive, SwipeTone.PRIMARY)
        compose.setContent {
            TestTheme(rtl = rtl) {
                Box(Modifier.width(360.dp)) {
                    SwipeableRow(left = gesture, right = gesture, enabled = true, onSwipe = { swipes += it }) {
                        ConversationRow(row(1, "Mina"), selected = false, selecting = false, onClick = {}, onLongClick = {})
                    }
                }
            }
        }
        val mina = compose.onNode(hasContentDescription("Mina", substring = true))
        for (direction in listOf(false, true)) {
            rtl = direction
            swipes.clear()
            compose.waitForIdle()
            mina.performTouchInput { swipeLeft(startX = centerX, endX = centerX - width * 0.25f, durationMillis = 40) }
            compose.waitForIdle()
            check(swipes.isEmpty()) { "A short flick committed (rtl=$direction): $swipes" }
            mina.performTouchInput { swipeLeft() }
            compose.waitForIdle()
            mina.performTouchInput { swipeRight() }
            compose.waitForIdle()
            check(swipes == listOf(false, true)) { "Swipes were not physical (rtl=$direction): $swipes" }
        }
    }

    @Test
    fun threadText() {
        // Keyword-gated: a bare number must not produce a Copy chip.
        check("Your verification code is 123456".extractOtpCode() == "123456")
        check("کد تایید شما 45678 است".extractOtpCode() == "45678")
        check("OTP: 9012, do not share".extractOtpCode() == "9012")
        check("See you at 2030".extractOtpCode() == null)
        check("Your order 12345678901 has shipped".extractOtpCode() == null)
        check("Balance is 4500".extractOtpCode() == null)
        // Digit runs longer than 8 are account/order numbers, not codes.
        check("code 123456789012".extractOtpCode() == null)
        check("pin 123".extractOtpCode() == null)
        check("انتقال به 123456*7890 مبلغ 900,000 رمز 6543210".extractOtpCode() == "6543210")
        check("رمز 6543210 حساب 123456*7890".extractOtpCode() == "6543210")
        check("Amount 4500 verification code 007890".extractOtpCode() == "007890")
        check("OTP: 007890, do not share".extractOtpCode() == "007890")
        check("کد تایید شما ۰۰۷۸۹۰ است".extractOtpCode() == "۰۰۷۸۹۰")
        check("OTP: ٠٠٧٨٩٠".extractOtpCode() == "٠٠٧٨٩٠")
        check("رمز صادر نشد حساب 123456*7890".extractOtpCode() == null)
        check("Your pincode address is 123456".extractOtpCode() == null)
        check("OTP 123456 or OTP 654321".extractOtpCode() == null)
        check("OTP 123456 or 654321".extractOtpCode() == null)
        check("رمز 123456 یا 654321".extractOtpCode() == null)
        check("code 1234,567".extractOtpCode() == null)
        check("code 1234, 5678".extractOtpCode() == null)
        check("code 2026-09-30".extractOtpCode() == null)
        check("رمز 123456*7890".extractOtpCode() == null)
        check("code 1234abcd".extractOtpCode() == null)
        check("code 1234۵۶۷۸۹".extractOtpCode() == null)

        // Unsaved long-number senders still use OTP detection for Business classification.
        val sender = conversation("+15551234567", "+15551234567", snippet = "کد تایید شما ۰۰۷۸۹۰ است")
        check(sender.category() == InboxFilter.BUSINESS)
        check(sender.copy(snippet = "رمز صادر نشد حساب 123456*7890").category() == InboxFilter.UNKNOWN)
    }

    /** Swipe settings are physical directions; archive goes dead where the provider cannot archive. */
    @Test
    fun swipeActions() = IsolatedPreferences("inbox-swipes").use { prefs ->
        val config = prefs.config
        config.isArchiveAvailable = true
        config.swipeLeftAction = SwipeAction.ARCHIVE
        config.swipeRightAction = SwipeAction.DELETE
        check(config.swipeAction(towardsRight = false) == SwipeAction.ARCHIVE)
        check(config.swipeAction(towardsRight = true) == SwipeAction.DELETE)

        config.isArchiveAvailable = false
        check(config.swipeAction(towardsRight = false) == SwipeAction.NONE)
        check(config.swipeAction(towardsRight = true) == SwipeAction.DELETE)
    }

    @Test
    fun migration11to12() = migratedDatabase(
        name = "inbox-migration-check.db",
        version = 11,
        migrations = listOf("MIGRATION_11_12", "MIGRATION_12_13", "MIGRATION_13_14", "MIGRATION_14_15"),
        seed = { db ->
            db.execSQL("INSERT INTO drafts (thread_id, body, date) VALUES (1, 'keep draft', 123)")
            db.execSQL("INSERT INTO blocked_messages (address, body, date, reason) VALUES ('test', 'keep spam', 123, 1)")
        },
    ) { room ->
        val db = room.openHelper.writableDatabase
        check(room.DraftsDao().getDraftById(1)?.body == "keep draft")
        db.query("SELECT body FROM blocked_messages").use { check(it.moveToFirst() && it.getString(0) == "keep spam") }
        db.query("PRAGMA index_info(index_messages_thread_id_date)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) columns.add(cursor.getString(2))
            check(columns == listOf("thread_id", "date"))
        }
        db.query("PRAGMA table_info(conversations)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns.add(cursor.getString(1))
            check("preview_snippet" in columns && "preview_date" in columns && "all_spam" in columns)
        }
    }

    @Test
    fun migration14to15() = migratedDatabase(
        name = "inbox-preview-migration-check.db",
        version = 14,
        migrations = listOf("MIGRATION_14_15"),
        seed = { db ->
            db.execSQL(
                "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count, message_count) " +
                    "VALUES (3, 'provider', 1, 1, 'Preview', '', 0, '5550200', 0, 0, 0, 0, 2)"
            )
            db.execSQL(
                "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_phone_number, sender_name, sender_photo_uri, subscription_id, status, is_scheduled) " +
                    "VALUES (10, 'visible-body', 1, '[]', 5, 1, 3, 0, '', '', '', -1, -1, 0)"
            )
            db.execSQL(
                "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_phone_number, sender_name, sender_photo_uri, subscription_id, status, is_scheduled) " +
                    "VALUES (11, 'spam-body', 1, '[]', 9, 1, 3, 0, '', '', '', -1, -1, 0)"
            )
            db.execSQL("INSERT INTO spam_messages (id, thread_id, reason, marked_ts) VALUES (11, 3, 1, 1)")
        },
    ) { room ->
        val row = room.ConversationsDao().getNonArchived().single()
        check(row.threadId == 3L)
        check(row.snippet == "visible-body") { "Migration must backfill the visible preview as the inbox snippet" }
        check(row.date == 5) { "Spam threads must take their date from the latest visible message" }
        check(!row.allSpam)
    }
}

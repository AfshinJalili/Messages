package org.fossify.messages

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.extractOtpCode
import org.fossify.messages.helpers.Config
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.helpers.category
import org.fossify.messages.helpers.sortedForInbox
import org.fossify.messages.models.Conversation
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.fossify.messages.helpers.swipeAction
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.inbox.InboxAction
import org.fossify.messages.ui.inbox.InboxRow
import org.fossify.messages.ui.inbox.InboxScreen
import org.fossify.messages.ui.inbox.InboxSection
import org.fossify.messages.ui.inbox.InboxUiState
import org.fossify.messages.ui.inbox.PRIMARY_ACTION_COUNT
import org.fossify.messages.ui.inbox.availableActions
import org.fossify.messages.ui.inbox.section
import org.fossify.messages.ui.inbox.splitForBar
import java.util.Calendar
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Database fixtures are disposable. Activity checks open the inbox and a synthetic thread; no messages are sent. */
@RunWith(AndroidJUnit4::class)
class InboxChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext

    // An activity rule so assertions resolve strings in the same locale the screen renders in (the
    // test phone runs Persian, which also localizes digits).
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun row(id: Long, title: String, read: Boolean = true, pinned: Boolean = false, muted: Boolean = false, isGroup: Boolean = false, phone: String = "+15551234567") =
        InboxRow(Conversation(id, "hi", 1, read, title, "", isGroup, phone), draft = null, pinned = pinned, muted = muted)

    /** Selection swaps the search field for same-height controls; rows must not move under the finger. */
    @Test
    fun selectionKeepsRowsInPlace() {
        var state by mutableStateOf(InboxUiState(rows = listOf(row(1, "Mina"), row(2, "Dad", read = false))))
        compose.setContent {
            OpenLineTheme(dark = false) {
                InboxScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
                    onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
                )
            }
        }
        val before = compose.onNodeWithText("Dad").getUnclippedBoundsInRoot().top
        state = state.copy(selected = setOf(1L))
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.inbox_selected, 1, 1)).assertExists()
        check(compose.onNodeWithText("Dad").getUnclippedBoundsInRoot().top == before)
    }

    /** uiautomator clips bounds at the system bar; measure the real Compose touch targets instead. */
    @Test
    fun navigationTargetsAreAtLeast48dp() {
        compose.setContent {
            OpenLineTheme(dark = false) {
                InboxScreen(
                    state = InboxUiState(rows = listOf(row(1, "Mina"))),
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpen = {}, onToggleSelection = {}, onSelectAll = {}, onClearSelection = {}, onAction = {},
                    onSwipe = { _, _ -> }, onFilter = {}, onSearch = {}, onNewMessage = {}, onLibrary = {}, onSettings = {},
                )
            }
        }
        listOf(
            R.string.inbox_nav_inbox, R.string.inbox_nav_library, org.fossify.commons.R.string.settings,
            InboxFilter.ALL.label, InboxFilter.UNREAD.label,
        ).forEach {
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
        check(at(-2).section(now) == InboxSection.UPCOMING) { "Scheduled sends must not fall into Earlier" }
    }

    @Test
    fun messageMetadataSeparatesSmsAndMms() {
        val preferenceName = "metadata-regression"
        val context = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String?, mode: Int) =
                targetContext.getSharedPreferences(preferenceName, mode)
        }
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val config = Config(context)
            val sms = org.fossify.messages.models.Message(
                123, "test", android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX, -1, ArrayList(), 0, false, 0L, false, null, "test", "test", "", -1
            )
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
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun conversation(
        title: String,
        phoneNumber: String,
        snippet: String = "hi",
        threadId: Long = 1,
        date: Int = 1,
        isGroup: Boolean = false,
    ) = Conversation(threadId, snippet, date, true, title, "", isGroup, phoneNumber)

    @Test
    fun conversationStateDiff() {
        val original = Conversation(1, "Preview", 1, true, "Sender", "", false, "123")
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
    fun pinnedConversationsKeepPinOrder() {
        val config = targetContext.config
        val original = config.pinnedConversations
        try {
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
        } finally {
            config.pinnedConversations = original
        }
    }

    @Test
    fun conversationRowLayout() = instrumentation.runOnMainSync {
        for (rtl in listOf(false, true)) {
            val configuration = Configuration(targetContext.resources.configuration).apply {
                fontScale = 1.3f
                setLayoutDirection(Locale(if (rtl) "fa" else "en"))
            }
            val themed = ContextThemeWrapper(targetContext.createConfigurationContext(configuration), R.style.AppTheme)
            val binding = ItemConversationBinding.inflate(LayoutInflater.from(themed))
            binding.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            binding.conversationAddress.text = "FILIMO"
            binding.conversationBodyShort.text = "برای ورود کد تایید را وارد کنید. Your verification code is 123456."
            binding.conversationDate.text = "Sep 9"
            binding.draftIndicator.visibility = View.GONE
            binding.pinIndicator.visibility = View.VISIBLE
            binding.mutedIndicator.visibility = View.VISIBLE
            binding.unreadCountBadge.apply {
                visibility = View.VISIBLE
                text = "99+"
            }
            val width = (360 * themed.resources.displayMetrics.density).toInt()
            binding.root.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            binding.root.layout(0, 0, width, binding.root.measuredHeight)
            check(binding.conversationBodyShort.width > width / 3)
            check(binding.conversationBodyShort.lineCount == 2)
            check(binding.unreadCountBadge.width >= binding.unreadCountBadge.paint.measureText("99+"))
            check(binding.conversationBodyShort.bottom <= binding.root.height)
            val image = Bitmap.createBitmap(width, binding.root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            canvas.drawColor(Color.DKGRAY)
            binding.root.draw(canvas)
            File(targetContext.cacheDir, "inbox-row-${if (rtl) "rtl" else "ltr"}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
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


    }

    /** Swipe settings are physical directions; archive goes dead where the provider cannot archive. */
    @Test
    fun swipeActions() {
        val config = targetContext.config
        val originalLeft = config.swipeLeftAction
        val originalRight = config.swipeRightAction
        val originalArchiveAvailable = config.isArchiveAvailable
        try {
            config.isArchiveAvailable = true
            config.swipeLeftAction = SwipeAction.ARCHIVE
            config.swipeRightAction = SwipeAction.DELETE
            check(config.swipeAction(towardsRight = false) == SwipeAction.ARCHIVE)
            check(config.swipeAction(towardsRight = true) == SwipeAction.DELETE)

            config.isArchiveAvailable = false
            check(config.swipeAction(towardsRight = false) == SwipeAction.NONE)
            check(config.swipeAction(towardsRight = true) == SwipeAction.DELETE)
        } finally {
            config.swipeLeftAction = originalLeft
            config.swipeRightAction = originalRight
            config.isArchiveAvailable = originalArchiveAvailable
        }
    }

    @Test
    fun migration11to12() {
        val name = "inbox-migration-check.db"
        targetContext.deleteDatabase(name)
        try {
            val schema = instrumentation.context.assets.open("org.fossify.messages.databases.MessagesDatabase/11.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            targetContext.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
                db.execSQL("INSERT INTO drafts (thread_id, body, date) VALUES (1, 'keep draft', 123)")
                db.execSQL("INSERT INTO blocked_messages (address, body, date, reason) VALUES ('test', 'keep spam', 123, 1)")
                db.version = 11
            }
            val migrations = listOf("MIGRATION_11_12", "MIGRATION_12_13", "MIGRATION_13_14").map {
                MessagesDatabase::class.java.getDeclaredField(it).apply { isAccessible = true }.get(null) as Migration
            }
            val room = Room.databaseBuilder(targetContext, MessagesDatabase::class.java, name)
                .addMigrations(*migrations.toTypedArray()).build()
            try {
                val db = room.openHelper.writableDatabase // Opens and validates against the actual Room entity schema.
                check(room.DraftsDao().getDraftById(1)?.body == "keep draft")
                db.query("SELECT body FROM blocked_messages").use { check(it.moveToFirst() && it.getString(0) == "keep spam") }
                db.query("PRAGMA index_info(index_messages_thread_id_date)").use { cursor ->
                    val columns = mutableListOf<String>()
                    while (cursor.moveToNext()) columns.add(cursor.getString(2))
                    check(columns == listOf("thread_id", "date"))
                }
            } finally {
                room.close()
            }
        } finally {
            targetContext.deleteDatabase(name)
        }
    }
}

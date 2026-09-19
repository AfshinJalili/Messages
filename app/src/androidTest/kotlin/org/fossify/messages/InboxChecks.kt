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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.extractOtpCode
import org.fossify.messages.helpers.ConversationSwipeCallback
import org.fossify.messages.helpers.Config
import org.fossify.messages.helpers.InboxFilter
import org.fossify.messages.helpers.SwipeAction
import org.fossify.messages.helpers.category
import org.fossify.messages.helpers.sortedForInbox
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.BlockedMessage
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Database fixtures are disposable. Activity checks open the inbox and a synthetic thread; no messages are sent. */
@RunWith(AndroidJUnit4::class)
class InboxChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext

    @Test
    fun selectionHidesHeaderWithoutMovingRows() {
        ActivityScenario.launch<MainActivity>(Intent(targetContext, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val header = activity.findViewById<View>(R.id.inbox_header)
                activity.onInboxSelectionChanged(true)
                check(header.visibility == View.INVISIBLE) { "Selection must hide the header and preserve its layout space" }
                activity.onInboxSelectionChanged(false)
                check(header.visibility == View.VISIBLE)
            }
            if (InstrumentationRegistry.getArguments().getString("capture_inbox") == "true") {
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(500)
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(image))
                        File(targetContext.cacheDir, "inbox-screen.png").outputStream().use {
                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally {
                        image.recycle()
                    }
                }
            }
        }
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
            val sms = BlockedMessage(id = 123, address = "test", body = "test", date = 0, reason = 0)
                .toMessage(org.fossify.commons.models.SimpleContact(0, 0, "test", "", arrayListOf(), arrayListOf(), arrayListOf()))
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

    /** Swipe directions are absolute, so the underlay must follow the finger, not the layout direction. */
    @Test
    fun swipeActions() = instrumentation.runOnMainSync {
        val config = targetContext.config
        val originalLeft = config.swipeLeftAction
        val originalRight = config.swipeRightAction
        val originalArchiveAvailable = config.isArchiveAvailable
        try {
            val themed = ContextThemeWrapper(targetContext, R.style.AppTheme)
            val callback = ConversationSwipeCallback(context = themed, isSwipeEnabled = { true }, onSwipe = { _, _ -> })
            val list = RecyclerView(themed)
            val binding = ItemConversationBinding.inflate(LayoutInflater.from(themed))
            val width = (360 * themed.resources.displayMetrics.density).toInt()
            binding.conversationAddress.text = "FILIMO"
            binding.conversationBodyShort.text = "Your verification code is 123456."
            binding.root.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            binding.root.layout(0, 0, width, binding.root.measuredHeight)
            val holder = object : RecyclerView.ViewHolder(binding.root) {}

            fun flagsFor(vararg directions: Int) =
                ItemTouchHelper.Callback.makeMovementFlags(0, directions.fold(0) { acc, it -> acc or it })

            config.isArchiveAvailable = true
            config.swipeLeftAction = SwipeAction.ARCHIVE
            config.swipeRightAction = SwipeAction.DELETE
            check(callback.getMovementFlags(list, holder) == flagsFor(ItemTouchHelper.LEFT, ItemTouchHelper.RIGHT))

            config.swipeLeftAction = SwipeAction.NONE
            check(callback.getMovementFlags(list, holder) == flagsFor(ItemTouchHelper.RIGHT))

            // Archive is unavailable on some telephony providers; that direction must go dead, not crash.
            config.swipeLeftAction = SwipeAction.ARCHIVE
            config.swipeRightAction = SwipeAction.ARCHIVE
            config.isArchiveAvailable = false
            check(callback.getMovementFlags(list, holder) == flagsFor())
            config.isArchiveAvailable = true

            val archive = ContextCompat.getColor(themed, R.color.swipe_archive_background)
            val mute = ContextCompat.getColor(themed, R.color.swipe_mute_background)
            val untouched = Color.WHITE
            config.swipeRightAction = SwipeAction.ARCHIVE
            config.swipeLeftAction = SwipeAction.MUTE

            fun underlayAt(dX: Float, x: Int): Int {
                val image = Bitmap.createBitmap(width, binding.root.height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(image)
                    canvas.drawColor(untouched)
                    callback.onChildDraw(canvas, list, holder, dX, 0f, ItemTouchHelper.ACTION_STATE_SWIPE, true)
                    return image.getPixel(x, binding.root.height / 2)
                } finally {
                    image.recycle()
                }
            }

            val half = width / 2f
            check(underlayAt(half, width / 4) == archive)
            check(underlayAt(half, width * 3 / 4) == untouched)
            check(underlayAt(-half, width * 3 / 4) == mute)
            check(underlayAt(-half, width / 4) == untouched)
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
            val field = MessagesDatabase::class.java.getDeclaredField("MIGRATION_11_12").apply { isAccessible = true }
            val migration = field.get(null) as Migration
            val room = Room.databaseBuilder(targetContext, MessagesDatabase::class.java, name)
                .addMigrations(migration).build()
            try {
                val db = room.openHelper.writableDatabase // Opens and validates against the actual Room entity schema.
                check(room.DraftsDao().getDraftById(1)?.body == "keep draft")
                check(room.BlockedMessagesDao().getAll().single().body == "keep spam")
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

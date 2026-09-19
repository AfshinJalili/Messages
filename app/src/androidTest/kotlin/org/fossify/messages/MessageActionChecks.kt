package org.fossify.messages

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.models.Message
import org.fossify.messages.views.MessageBodyView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses in-memory bubbles only. Does not insert, send, or delete provider messages. */
@RunWith(AndroidJUnit4::class)
class MessageActionChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun exactTextTapsAndLongPress() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val body = MessageBodyView(activity)
                activity.addContentView(body, ViewGroup.LayoutParams(900, 600))
                var clicks = 0
                var holds = 0
                body.setOnClickListener { clicks++ }
                body.setOnLongClickListener { holds++; true }
                body.setMessageBody("Use 123456 here, call +98 912 345 6789 or visit https://example.com/123?q=45 and کد ۱۲۳۴۵۶.")
                body.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
                body.layout(0, 0, 900, 600)
                val spans = body.text as Spanned
                val links = spans.getSpans(0, spans.length, URLSpan::class.java)
                assertEquals(1, links.size)
                val numbers = spans.getSpans(0, spans.length, ClickableSpan::class.java).filterNot { it is URLSpan }
                assertEquals(listOf("123456", "+98 912 345 6789", "۱۲۳۴۵۶"), numbers.map { spans.subSequence(spans.getSpanStart(it), spans.getSpanEnd(it)).toString() })
                fun down(offset: Int) {
                    val line = body.layout.getLineForOffset(offset)
                    val x = (body.layout.getPrimaryHorizontal(offset) + body.layout.getPrimaryHorizontal(offset + 1)) / 2 + body.totalPaddingLeft
                    val y = (body.layout.getLineTop(line) + body.layout.getLineBottom(line)) / 2f + body.totalPaddingTop
                    val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
                    body.dispatchTouchEvent(event)
                    event.recycle()
                }
                down(5)
                body.performClick()
                assertEquals(0, clicks)
                assertEquals("123456", (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString())
                down(1)
                body.performClick()
                assertEquals(1, clicks)
                down(5)
                body.performLongClick()
                assertEquals(1, holds)
                body.specialClicksEnabled = { false }
                down(5)
                body.performClick()
                assertEquals(2, clicks)
                body.setMessageBody("A plain recycled message")
                assertEquals(0, (body.text as Spanned).getSpans(0, body.text.length, ClickableSpan::class.java).size)
            }
        }
    }

    @Test fun nativeTapReleaseAndHold() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var body: MessageBodyView
            var clicks = 0
            var holds = 0
            scenario.onActivity { activity ->
                body = MessageBodyView(activity).apply {
                    setMessageBody("Code 654321 is ready")
                    setOnClickListener { clicks++ }
                    setOnLongClickListener { holds++; true }
                }
                activity.addContentView(body, ViewGroup.LayoutParams(900, 400))
            }
            instrumentation.waitForIdleSync()
            fun touch(offset: Int, action: Int) {
                scenario.onActivity {
                    val line = body.layout.getLineForOffset(offset)
                    val x = (body.layout.getPrimaryHorizontal(offset) + body.layout.getPrimaryHorizontal(offset + 1)) / 2 + body.totalPaddingLeft
                    val y = (body.layout.getLineTop(line) + body.layout.getLineBottom(line)) / 2f + body.totalPaddingTop
                    val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
                    body.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
            touch(6, MotionEvent.ACTION_DOWN)
            touch(6, MotionEvent.ACTION_UP)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0, clicks)
                assertEquals("654321", (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString())
            }
            touch(1, MotionEvent.ACTION_DOWN)
            touch(1, MotionEvent.ACTION_UP)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(1, clicks) }
            touch(6, MotionEvent.ACTION_DOWN)
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 200)
            touch(6, MotionEvent.ACTION_UP)
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1, holds)
                assertEquals("Releasing a hold must not open the menu", 1, clicks)
            }
        }
    }

    @Test fun bottomMessageMenuStaysReachable() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var recycler: MyRecyclerView
            lateinit var adapter: ThreadAdapter
            scenario.onActivity { activity ->
                recycler = MyRecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity).apply { stackFromEnd = true }
                }
                activity.addContentView(recycler, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                adapter = ThreadAdapter(activity, recycler, {}, false) { _, _, _ -> error("No deletion expected") }
                // A large-font menu must remain scrollable even when taller than the available window.
                ThreadAdapter::class.java.getDeclaredField("fontSize").apply { isAccessible = true }.setFloat(adapter, 180f)
                recycler.adapter = adapter
                adapter.updateMessages(arrayListOf(Message(987654322, "Bottom message", 2, 0, arrayListOf(), 1700000000, true, 987654322, false, null, "", "Fixture", "", -1)))
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            scenario.onActivity {
                recycler.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<MessageBodyView>(R.id.thread_message_body).performClick()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
            scenario.onActivity { activity ->
                val popup = ThreadAdapter::class.java.getDeclaredField("messageMenu").apply { isAccessible = true }.get(adapter) as PopupWindow
                val view = popup.contentView
                val frame = android.graphics.Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val location = IntArray(2).also { view.getLocationOnScreen(it) }
                assertTrue("Menu top ${location[1]} must stay below ${frame.top}", location[1] >= frame.top)
                assertTrue("Menu bottom ${location[1] + view.height} must stay above ${frame.bottom}", location[1] + view.height <= frame.bottom)
                val scroll = view as? android.widget.ScrollView
                if (scroll != null) {
                    scroll.isSmoothScrollingEnabled = false
                    scroll.fullScroll(View.FOCUS_DOWN)
                }
                else {
                    val content = view as ViewGroup
                    val last = content.getChildAt(content.childCount - 1)
                    val visible = android.graphics.Rect()
                    assertTrue("Last action is clipped and cannot be reached", last.getGlobalVisibleRect(visible) && visible.height() >= last.height)
                }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val popup = ThreadAdapter::class.java.getDeclaredField("messageMenu").apply { isAccessible = true }.get(adapter) as PopupWindow
                val scroll = popup.contentView as android.widget.ScrollView
                val content = scroll.getChildAt(0) as ViewGroup
                val share = content.getChildAt(content.childCount - 1)
                val visible = android.graphics.Rect()
                assertTrue("Last action must be reachable: scroll=${scroll.scrollY}, viewport=${scroll.height}, content=${content.height}, last=${share.top}..${share.bottom}", share.getGlobalVisibleRect(visible))
                assertTrue("Last action must be fully visible", visible.height() >= share.height)
                popup.dismiss()
            }
        }
    }

    @Test fun bubbleMenuAndSelectionToolbar() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var recycler: MyRecyclerView
            lateinit var adapter: ThreadAdapter
            scenario.onActivity { activity ->
                recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
                activity.addContentView(recycler, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                adapter = ThreadAdapter(activity, recycler, {}, false) { _, _, _ -> error("No deletion expected") }
                recycler.adapter = adapter
                adapter.updateMessages(arrayListOf(Message(987654321, "Your code is 123456. Visit example.com for details.", 2, 0, arrayListOf(), 1700000000, true, 987654321, false, null, "", "Fixture", "", -1)))
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            scenario.onActivity { activity ->
                val holder = checkNotNull(recycler.findViewHolderForAdapterPosition(0))
                val body = holder.itemView.findViewById<MessageBodyView>(R.id.thread_message_body)
                body.performClick()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
            scenario.onActivity { activity ->
                val popup = ThreadAdapter::class.java.getDeclaredField("messageMenu").apply { isAccessible = true }.get(adapter) as PopupWindow
                assertTrue(popup.isShowing)
                val content = (popup.contentView as android.widget.ScrollView).getChildAt(0) as ViewGroup
                val labels = (0 until content.childCount).map { (content.getChildAt(it) as TextView).text.toString() }
                assertFalse(labels.any { it.contains("2023") })
                assertEquals(activity.getString(org.fossify.commons.R.string.copy), labels.first())
                assertTrue(labels.contains(activity.getString(org.fossify.commons.R.string.delete)))
                assertTrue(labels.contains(activity.getString(R.string.star_message)))
                assertTrue(labels.contains(activity.getString(R.string.forward_message)))
                assertTrue(labels.contains(activity.getString(org.fossify.commons.R.string.share)))
                val forward = (0 until content.childCount).map { content.getChildAt(it) as TextView }.first { it.text == activity.getString(R.string.forward_message) }
                assertTrue(forward.isEnabled)
                assertTrue(forward.hasOnClickListeners())
                content.measure(View.MeasureSpec.makeMeasureSpec(popup.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                content.layout(0, 0, content.measuredWidth, content.measuredHeight)
                val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
                content.draw(android.graphics.Canvas(bitmap))
                java.io.File(activity.cacheDir, "message-actions.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                var forwarded: Intent? = null
                val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                    override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                        forwarded = Intent(intent)
                        return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                    }
                }
                instrumentation.addMonitor(monitor)
                try {
                    forward.performClick()
                    assertEquals(org.fossify.messages.activities.NewConversationActivity::class.java.name, forwarded?.component?.className)
                    assertEquals(Intent.ACTION_SEND, forwarded?.action)
                    assertEquals("Your code is 123456. Visit example.com for details.", forwarded?.getStringExtra(Intent.EXTRA_TEXT))
                } finally { instrumentation.removeMonitor(monitor) }
                (0 until content.childCount).map { content.getChildAt(it) as TextView }.first { it.text == activity.getString(org.fossify.commons.R.string.select_text) }.performClick()
                assertFalse(popup.isShowing)
                val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
                activity.menuInflater.inflate(R.menu.cab_thread, menu)
                adapter.prepareActionMode(menu)
                assertFalse("Select text must not select the message", menu.findItem(R.id.cab_copy_to_clipboard).isVisible)
                recycler.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<MessageBodyView>(R.id.thread_message_body).performLongClick()
                adapter.prepareActionMode(menu)
                assertTrue(menu.findItem(R.id.cab_copy_to_clipboard).isVisible)
                assertTrue(menu.findItem(R.id.cab_delete).isVisible)
                val quick = (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible && (it as androidx.appcompat.view.menu.MenuItemImpl).requiresActionButton() }
                assertEquals(listOf(R.id.cab_copy_to_clipboard, R.id.cab_delete), quick.map { it.itemId })
                adapter.finishActMode()
            }
        }
    }
}

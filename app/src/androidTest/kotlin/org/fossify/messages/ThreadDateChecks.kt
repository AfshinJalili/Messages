package org.fossify.messages

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.helpers.*
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadDateTime
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** In-memory messages only; never sends or inserts provider messages. */
@RunWith(AndroidJUnit4::class)
class ThreadDateChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun message(id: Int, date: DateTime, sim: Int = 1) = Message(
        id.toLong(), "Message $id. A longer message to check wrapping and the separate time footer.",
        2, 0, arrayListOf(), (date.millis / 1000).toInt(), true, Long.MAX_VALUE - 12,
        false, null, "", "Date fixture", "", sim)

    @Test fun calendarDividersFooterAndStickyDate() {
        val context = instrumentation.targetContext
        val intent = Intent(context, ThreadActivity::class.java)
            .putExtra(THREAD_ID, Long.MAX_VALUE - 12).putExtra(THREAD_NUMBER, "5550100988")
            .putExtra(THREAD_TITLE, "Date fixture")
        ActivityScenario.launch<ThreadActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            val readyDeadline = SystemClock.elapsedRealtime() + 5000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < readyDeadline) {
                scenario.onActivity { activity ->
                    ready = listOf("providerMessagesReady", "initialPositionSettled").all { field ->
                        ThreadActivity::class.java.getDeclaredField(field).apply { isAccessible = true }.getBoolean(activity)
                    }
                }
                if (!ready) SystemClock.sleep(30)
            }
            assertTrue("Initial provider load completes before installing in-memory fixtures", ready)
            val firstDay = DateTime.now().withMonthOfYear(9).withDayOfMonth(11).withTimeAtStartOfDay()
            val fixture = arrayListOf(message(1, firstDay.plusMinutes(1)), message(2, firstDay.plusHours(15), 2),
                message(3, firstDay.plusDays(1).minusSeconds(1)), message(4, firstDay.plusDays(1)))
            repeat(24) { fixture.add(message(it + 5, firstDay.plusDays(1).plusMinutes(it + 1))) }
            fixture[7] = fixture[7].copy(type = 1, body = "Received message. پیام دریافتی", participants = arrayListOf(
                org.fossify.commons.models.SimpleContact(0, 0, "Date fixture", "", arrayListOf(), arrayListOf(), arrayListOf())))
            fixture[8] = fixture[8].copy(body = "", isMMS = true, attachment = org.fossify.messages.models.MessageAttachment(9, "", arrayListOf(
                org.fossify.messages.models.Attachment(null, 9, "content://fixture/document", "application/pdf", 0, 0, "Document.pdf"))))
            fixture[9] = fixture[9].copy(body = "سلام", subscriptionId = 2)
            lateinit var list: RecyclerView
            lateinit var adapter: ThreadAdapter
            lateinit var items: ArrayList<ThreadItem>
            scenario.onActivity { activity ->
                ThreadActivity::class.java.getDeclaredField("messages").apply { isAccessible = true }.set(activity, fixture)
                @Suppress("UNCHECKED_CAST")
                items = ThreadActivity::class.java.getDeclaredMethod("getThreadItems").apply { isAccessible = true }.invoke(activity) as ArrayList<ThreadItem>
                val dividers = items.filterIsInstance<ThreadDateTime>()
                assertEquals("Gaps and SIM switches must not split a day", 2, dividers.size)
                assertEquals(firstDay.millis / 1000, dividers[0].date.toLong())
                assertEquals(firstDay.plusDays(1).millis / 1000, dividers[1].date.toLong())
                list = activity.findViewById(R.id.thread_messages_list)
                adapter = list.adapter as ThreadAdapter
                ThreadAdapter::class.java.getDeclaredField("simLabels").apply { isAccessible = true }
                    .set(adapter, mapOf(1 to "1", 2 to "2"))
                adapter.updateMessages(items)
            }
            instrumentation.waitForIdleSync()
            val commitDeadline = SystemClock.elapsedRealtime() + 3000
            var committed = false
            while (!committed && SystemClock.elapsedRealtime() < commitDeadline) {
                scenario.onActivity { committed = adapter.currentList == items }
                if (!committed) SystemClock.sleep(30)
            }
            assertTrue("Fixture diff is committed before scrolling", committed)
            scenario.onActivity {
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, 0)
            }
            val deadline = SystemClock.elapsedRealtime() + 3000
            var positioned = false
            while (!positioned && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity {
                    positioned = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 8
                }
                if (!positioned) SystemClock.sleep(30)
            }
            var positionDetails = ""
            scenario.onActivity {
                positionDetails = "first=${(list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()}, last=${(list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()}, rows=${adapter.itemCount}, height=${list.height}"
            }
            assertTrue("Wait for the requested RecyclerView layout: $positionDetails", positioned)
            scenario.onActivity { activity ->
                ThreadActivity::class.java.getDeclaredMethod("updateStickyDate").apply { isAccessible = true }.invoke(activity)
                val sticky = activity.findViewById<TextView>(R.id.sticky_thread_date)
                assertEquals("first=${(list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()}, rows=${adapter.currentList.size}, children=${(0 until list.childCount).map { list.getChildAdapterPosition(list.getChildAt(it)) to list.getChildAt(it).top }}", View.VISIBLE, sticky.visibility)
                assertEquals(ThreadDates.label((firstDay.plusDays(1).millis / 1000).toInt()), sticky.text.toString())
                val row = list.findViewHolderForAdapterPosition(8)!!.itemView
                val footer = row.findViewById<TextView>(R.id.thread_message_time)
                val bubble = row.findViewById<View>(R.id.thread_message_bubble)
                val body = row.findViewById<View>(R.id.thread_message_body)
                assertEquals(View.VISIBLE, footer.visibility)
                assertFalse(footer.text.contains("SIM"))
                assertTrue(footer.contentDescription.contains("SIM 1"))
                assertEquals("1", row.findViewById<TextView>(R.id.thread_message_sim).text.toString())
                assertEquals(activity.getColor(R.color.message_sim_one), row.findViewById<View>(R.id.thread_message_sim).backgroundTintList!!.defaultColor)
                assertTrue(footer.textSize < row.findViewById<TextView>(R.id.thread_message_body).textSize * 0.7f)
                val metadata = footer.parent as View
                val textBody = body as TextView
                val lastLine = textBody.layout.lineCount - 1
                assertTrue("Metadata does not overlap the final text line", metadata.top >= body.bottom ||
                    metadata.left >= body.left + textBody.totalPaddingLeft + textBody.layout.getLineRight(lastLine))
                assertTrue((footer.parent as View).bottom <= bubble.height)
                assertNotNull(footer.compoundDrawablesRelative[2])
                for (position in 9..11) {
                    val extra = list.findViewHolderForAdapterPosition(position)!!.itemView
                    val extraFooter = extra.findViewById<TextView>(R.id.thread_message_time)
                    val extraBubble = extra.findViewById<View>(R.id.thread_message_bubble)
                    assertTrue((extraFooter.parent as View).bottom <= extraBubble.height)
                    assertTrue(extraFooter.right <= extraBubble.width)
                    if (position == 11) {
                        val sim = extra.findViewById<TextView>(R.id.thread_message_sim)
                        assertEquals("2", sim.text.toString())
                        assertEquals(activity.getColor(R.color.message_sim_two), sim.backgroundTintList!!.defaultColor)
                    }
                    if (position == 9) assertNull(extraFooter.compoundDrawablesRelative[2])
                    if (position == 10) {
                        assertEquals(View.GONE, extra.findViewById<View>(R.id.thread_message_body).visibility)
                        assertEquals("Document text uses the bubble foreground", extraFooter.currentTextColor,
                            extra.findViewById<TextView>(R.id.filename).currentTextColor)
                        assertTrue((extraFooter.parent as View).top >= (extra.findViewById<View>(R.id.thread_message_attachments_holder).parent as View).bottom)
                    }
                }
                val field = ThreadAdapter::class.java.getDeclaredField("fontSize").apply { isAccessible = true }
                val originalSize = field.getFloat(adapter)
                field.setFloat(adapter, originalSize * 1.5f)
                try {
                    for (position in listOf(8, 9, 10, 11)) {
                        val holder = adapter.onCreateViewHolder(list, adapter.getItemViewType(position))
                        adapter.onBindViewHolder(holder, position)
                        holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                        val width = (360 * activity.resources.displayMetrics.density).toInt()
                        holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        holder.itemView.layout(0, 0, width, holder.itemView.measuredHeight)
                        val footer = holder.itemView.findViewById<TextView>(R.id.thread_message_time)
                        val bubble = holder.itemView.findViewById<View>(R.id.thread_message_bubble)
                        val metadata = footer.parent as View
                        assertTrue("Large RTL footer fits", metadata.bottom <= bubble.height - bubble.paddingBottom && metadata.left >= bubble.paddingLeft && metadata.right <= bubble.width - bubble.paddingRight)
                        val body = holder.itemView.findViewById<TextView>(R.id.thread_message_body)
                        if (position == 11) {
                            assertTrue("Short messages use inline metadata", metadata.top < body.bottom)
                            assertTrue("Inline text and metadata stay separate in RTL", metadata.left >= body.right)
                        }
                        adapter.onViewRecycled(holder)
                    }
                } finally { field.setFloat(adapter, originalSize) }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
            scenario.onActivity { activity ->
                val sticky = activity.findViewById<TextView>(R.id.sticky_thread_date)
                assertTrue("Sticky date text has been laid out", sticky.width > sticky.paddingLeft + sticky.paddingRight)
                val capture = activity.findViewById<View>(R.id.thread_holder)
                val bitmap = android.graphics.Bitmap.createBitmap(capture.width, capture.height, android.graphics.Bitmap.Config.ARGB_8888)
                capture.draw(android.graphics.Canvas(bitmap))
                java.io.File(activity.cacheDir, "thread-dates.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            SystemClock.sleep(1100)
            scenario.onActivity { activity ->
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.sticky_thread_date).visibility)
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                ThreadActivity::class.java.getDeclaredMethod("updateStickyDate").apply { isAccessible = true }.invoke(activity)
                assertEquals("Inline date must suppress its sticky duplicate", View.INVISIBLE,
                    activity.findViewById<View>(R.id.sticky_thread_date).visibility)
            }
        }
    }

    @Test fun labelsAndFooterRefresh() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val date = DateTime(2026, 9, 11, 0, 0)
            assertEquals("11 September", ThreadDates.label((date.millis / 1000).toInt(), date))
            assertEquals("11 September 2026", ThreadDates.label((date.millis / 1000).toInt(), date.plusYears(1)))
            val message = message(1, date)
            assertFalse(Message.areContentsTheSame(message, message.copy(status = 64)))
            assertFalse(Message.areContentsTheSame(message, message.copy(subscriptionId = 2)))
            assertFalse(Message.areContentsTheSame(message, message.copy(type = 5)))
        } finally { Locale.setDefault(previous) }
    }
}

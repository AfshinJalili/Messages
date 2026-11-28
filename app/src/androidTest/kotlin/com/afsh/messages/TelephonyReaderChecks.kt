package com.afsh.messages

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.helpers.TelephonyReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inserts only disposable local provider rows; never sends SMS/MMS. Requires the default SMS role. */
@RunWith(AndroidJUnit4::class)
class TelephonyReaderChecks {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun insertMms(threadId: Long, dateSeconds: Long, box: Int): Uri =
        checkNotNull(context.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateSeconds)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_TYPE, 132)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
        }))

    /** Two MMS downloads can finish together: each must announce its own row, not the newest one. */
    @Test fun downloadedMmsIsReadByItsIdNotAsTheNewest() {
        val threadId = context.getThreadId("555${System.currentTimeMillis() % 100000000}21")
        val now = System.currentTimeMillis() / 1000
        val downloaded = insertMms(threadId, now, Telephony.Mms.MESSAGE_BOX_INBOX)
        val newer = insertMms(threadId, now + 60, Telephony.Mms.MESSAGE_BOX_SENT)
        try {
            val mms = checkNotNull(TelephonyReader(context).getMmsById(ContentUris.parseId(downloaded)))
            assertEquals(ContentUris.parseId(downloaded), mms.id)
            assertEquals(threadId, mms.threadId)
            assertEquals(Telephony.Mms.MESSAGE_BOX_INBOX, mms.type)
            assertTrue(mms.isMMS)
            assertNull(TelephonyReader(context).getMmsById(-1))
        } finally {
            context.contentResolver.delete(downloaded, null, null)
            context.contentResolver.delete(newer, null, null)
        }
    }
}

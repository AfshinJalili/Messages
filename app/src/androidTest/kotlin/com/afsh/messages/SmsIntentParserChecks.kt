package com.afsh.messages

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afsh.messages.helpers.SmsIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Recipients of the sms/mms URIs that compose intents and "respond via message" carry. Needs android.net.Uri. */
@RunWith(AndroidJUnit4::class)
class SmsIntentParserChecks {
    private fun recipients(uri: Uri) = SmsIntentParser.parseRecipientsFromUri(uri)?.toList()

    @Test fun everySmsAndMmsSchemeGivesTheBareNumber() {
        listOf("sms", "smsto", "mms", "mmsto").forEach { scheme ->
            // Telecom builds the quick-reply uri this way, which encodes the plus sign.
            assertEquals(scheme, listOf("+989121234567"), recipients(Uri.fromParts(scheme, "+989121234567", null)))
            assertEquals(scheme, listOf("09121234567"), recipients(Uri.parse("$scheme:09121234567")))
        }
    }

    @Test fun separatorsSplitRecipientsAndTheQueryIsNotPartOfThem() {
        assertEquals(listOf("123", "456"), recipients(Uri.parse("smsto:123;456")))
        assertEquals(listOf("123", "456"), recipients(Uri.parse("mmsto:123,456")))
        assertEquals(listOf("123"), recipients(Uri.parse("sms:123?body=hi")))
    }

    @Test fun anotherSchemeHasNoRecipients() {
        assertNull(recipients(Uri.parse("tel:123")))
    }
}

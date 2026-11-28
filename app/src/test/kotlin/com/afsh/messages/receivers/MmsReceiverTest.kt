package com.afsh.messages.receivers

import com.afsh.commons.extensions.baseConfig
import com.afsh.messages.extensions.config
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The MMS library deletes a downloaded MMS for good when a block hook says yes. The user's spam
 * rules must not reach those hooks: the classifier decides after the MMS is stored, as for SMS.
 */
class MmsReceiverTest {

    @Test
    fun `a blocked phrase never makes the MMS library drop the message`() {
        val context = PreferencesContext()
        context.config.blockedKeywords = setOf("لغو11")
        assertFalse(MmsReceiver().isContentBlocked(context, "کد ورود شما 7788\nلغو11"))
    }

    @Test
    fun `blocking unknown numbers never makes the MMS library drop the message`() {
        val context = PreferencesContext()
        context.baseConfig.blockUnknownNumbers = true
        // With no contacts provider on the JVM, an empty From is the one sender the lookup reports as unknown.
        assertFalse(MmsReceiver().isAddressBlocked(context, ""))
    }
}

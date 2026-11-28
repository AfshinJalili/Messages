package com.afsh.messages.helpers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertNull
import org.junit.Test

/** The receivers classify a message after Telephony stored it, so a classifier error must not hide it. */
class IncomingSpamClassifierTest {

    private val brokenSettings = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            throw IllegalStateException("settings unavailable")
    }

    @Test
    fun `a classifier failure leaves the message unsilenced`() {
        assertNull(IncomingSpamClassifier.silenceReason(brokenSettings, "+989121234567", "کد ورود شما 7788\nلغو11"))
    }
}

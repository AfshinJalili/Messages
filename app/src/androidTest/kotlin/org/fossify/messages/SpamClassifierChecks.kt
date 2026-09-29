package org.fossify.messages

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.BLOCK_REASON_KEYWORD
import org.fossify.messages.helpers.BLOCK_REASON_NUMBER
import org.fossify.messages.helpers.IncomingSpamClassifier
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against throwaway preferences; the app's real allowlist and keywords are untouched. */
@RunWith(AndroidJUnit4::class)
class SpamClassifierChecks {
    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "spam-classifier-checks"
    private val context = object : ContextWrapper(targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int) = targetContext.getSharedPreferences(prefsName, mode)
    }

    @After
    fun clear() {
        // Config writes with apply(); commit first so a pending write cannot recreate the file.
        targetContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        targetContext.deleteSharedPreferences(prefsName)
    }

    private fun reason(address: String, body: String = "hello") = IncomingSpamClassifier.silenceReason(context, address, body)

    @Test
    fun allowedSenderBeatsKeyword() {
        context.config.blockedKeywords = hashSetOf("prize")
        check(reason("+15550100777", "win a prize") == BLOCK_REASON_KEYWORD)
        context.config.addAllowedNumber("5550100777")
        check(reason("+15550100777", "win a prize") == null) { "Never spam must win over a keyword" }
    }

    @Test
    fun blockedSenderGoesToSpamAndAllowingUnblocks() {
        context.config.addSpamNumber("+15550100778")
        check(reason("5550100778") == BLOCK_REASON_NUMBER) { "A blocked number must match in any format" }
        check(reason("5550100779") == null) { "Other numbers must not be blocked" }

        context.config.addAllowedNumber("5550100778")
        check(reason("+15550100778") == null) { "Allowing a sender must unblock it" }
        check(context.config.spamNumbers.isEmpty())

        context.config.addSpamNumber("5550100778")
        check(reason("+15550100778") == BLOCK_REASON_NUMBER) { "Blocking again must win over the earlier allow" }
        check(context.config.allowedNumbers.isEmpty())
    }

    @Test
    fun senderNamesMatchExactly() {
        context.config.addSpamNumber("HAMRAH_AVAL")
        check(reason("HAMRAH_AVAL") == BLOCK_REASON_NUMBER)
        check(reason("MCI Simcard") == null) { "A blocked sender name must not match other names" }
    }
}

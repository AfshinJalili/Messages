package com.afsh.messages

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.BLOCK_REASON_KEYWORD
import com.afsh.messages.helpers.BLOCK_REASON_NUMBER
import com.afsh.messages.helpers.BLOCK_REASON_RULE
import com.afsh.messages.helpers.FilterRule
import com.afsh.messages.helpers.FilterVerdict
import com.afsh.messages.helpers.RuleBasedFilter
import com.afsh.messages.helpers.SpamPatternGroup
import com.afsh.messages.helpers.SpamPatterns
import com.afsh.messages.models.SpamWhy
import com.afsh.messages.models.spamWhy
import com.afsh.messages.helpers.IncomingSpamClassifier
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

    @Test
    fun oneTimeCodesAreNeverRuleSpam() {
        context.config.ruleFilterEnabled = true
        listOf(
            "کد تایید شما: 12345\nدیجی‌کالا\nلغو11",
            "رمز یک‌بار مصرف: ۴۵۶۷۸۹ بانک ملت",
            "Your verification code is 482913. https://example.com/help",
            "کد فعال‌سازی 5521\n@snapp.ir #5521",
        ).forEach { body ->
            val result = RuleBasedFilter.explain(context, "SERVICE", body)
            check(result.rule == FilterRule.ONE_TIME_CODE) { "Not a code: $body -> $result" }
            check(reason("SERVICE", body) == null) { "A code went to Spam: $body" }
        }
    }

    @Test
    fun codesBeatBlockedPhrasesButNotBlockedSenders() {
        context.config.blockedKeywords = hashSetOf("لغو11")
        val code = "کد ورود شما 7788\nلغو11"
        check(reason("+15550100780", code) == null) { "A blocked phrase caught a code" }
        check(reason("+15550100780", "جشنواره\nلغو11") == BLOCK_REASON_KEYWORD)
        context.config.addSpamNumber("+15550100780")
        check(reason("+15550100780", code) == BLOCK_REASON_NUMBER) { "A blocked sender must still win" }
    }

    @Test
    fun marketingStillGoesToSpamAndSaysWhy() {
        context.config.ruleFilterEnabled = true
        val body = "جشنواره تخفیف 1403 لغو11"
        check(reason("SHOP", body) == BLOCK_REASON_RULE)
        val why: SpamWhy = context.spamWhy(BLOCK_REASON_RULE, "SHOP", body)
        check(why.label == R.string.spam_why_unsubscribe) { "$why" }
        check(why.matchedWords(body) == listOf("لغو")) { "${why.matchedWords(body)}" }
        check(!why.stale)
    }

    @Test
    fun editedPatternsPersistAndDriveTheVerdict() {
        context.config.ruleFilterEnabled = true
        val body = "Special offer, reply STOP"
        check(RuleBasedFilter.explain(context, "SHOP", body).verdict == FilterVerdict.NO_VERDICT)
        context.config.spamPatterns = context.config.spamPatterns.with(SpamPatternGroup.UNSUBSCRIBE, listOf("reply stop"))
        check(context.config.spamPatterns[SpamPatternGroup.UNSUBSCRIBE] == listOf("reply stop"))
        check(context.config.spamPatterns[SpamPatternGroup.MARKETING] == SpamPatterns.DEFAULT[SpamPatternGroup.MARKETING])
        check(RuleBasedFilter.explain(context, "SHOP", body).verdict == FilterVerdict.BLOCK)
        check(context.spamWhy(BLOCK_REASON_RULE, "SHOP", "hello").stale) { "No match now must read as stale" }
        context.config.spamPatterns = SpamPatterns.DEFAULT
        check(context.config.spamPatterns == SpamPatterns.DEFAULT)
    }

    @Test
    fun invalidPatternsAreReported() {
        check(SpamPatterns.error("(unclosed") != null)
        check(SpamPatterns.error("کد\\s*تایید") == null)
    }
}

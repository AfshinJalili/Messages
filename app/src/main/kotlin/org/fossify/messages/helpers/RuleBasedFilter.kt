package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.extensions.config

enum class FilterVerdict { ALLOW, BLOCK, NO_VERDICT }

object RuleBasedFilter {

    private val URL = Regex("""https?://|www\.|[\w-]+\.(ir|io|com|net|me|ly|app)/""", RegexOption.IGNORE_CASE)
    private val UNSUBSCRIBE = Regex("""لغو|unsubscribe|opt[- ]?out""", RegexOption.IGNORE_CASE)
    private val OTP = Regex("""رمز یکبار|رمز پویا|رمز ورود|رمز دوم|کد تایید|کد ورود|کد فعال|کد امنیتی|verification code|one[- ]?time|\bOTP\b""", RegexOption.IGNORE_CASE)
    private val MARKETING = Regex("""تخفیف|هدیه|جشنواره|جایزه|قرعه|کلیک|رایگان|بسته|فروش|کمپین|ثبت ?نام|عضویت""", RegexOption.IGNORE_CASE)

    fun evaluate(context: Context, sender: String, body: String): FilterVerdict {
        if (!context.config.ruleFilterEnabled || body.isBlank()) return FilterVerdict.NO_VERDICT
        val isAlphaSender = sender.any { it.isLetter() } || sender.trimStart().firstOrNull().let { it == '*' || it == '#' }
        val hasUrl = URL.containsMatchIn(body)
        val hasUnsubscribe = UNSUBSCRIBE.containsMatchIn(body)

        // ordered, high-precision first
        return when {
            OTP.containsMatchIn(body) && !hasUrl && !hasUnsubscribe -> FilterVerdict.ALLOW
            hasUnsubscribe -> FilterVerdict.BLOCK
            MARKETING.containsMatchIn(body) && (hasUrl || isAlphaSender) -> FilterVerdict.BLOCK
            else -> FilterVerdict.NO_VERDICT
        }
    }
}

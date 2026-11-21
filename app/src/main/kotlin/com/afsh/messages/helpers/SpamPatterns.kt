package com.afsh.messages.helpers

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The pattern groups the local rule filter reads. Each group is a list of regular expressions,
 * matched case-insensitively; a group matches when any of its patterns does.
 */
enum class SpamPatternGroup(val key: String) {
    /** Words that say a message carries a one-time code. */
    CODE_WORDS("code_words"),

    /** The code itself. A message is a one-time code only with both a code word and a code. */
    CODE("code"),

    /** Bank, receipt and payment language that must stay in the inbox. */
    TRANSACTIONAL("transactional"),

    LINKS("links"),
    MARKETING("marketing"),

    /** Strong promo emojis: each distinct match is one marketing signal. */
    PROMO_EMOJI("promo_emoji"),

    /** Weak smileys and hearts: never a signal on their own. */
    PROMO_EMOJI_WEAK("promo_emoji_weak"),
}

data class SpamPatterns(val groups: Map<SpamPatternGroup, List<String>>) {

    operator fun get(group: SpamPatternGroup): List<String> = groups[group].orEmpty()

    fun with(group: SpamPatternGroup, patterns: List<String>) = SpamPatterns(groups + (group to patterns))

    /** Only the groups that differ from [DEFAULT], so later default changes still reach the rest. */
    fun toJson(): String {
        val json = JSONObject()
        SpamPatternGroup.entries.filter { get(it) != DEFAULT[it] }.forEach { json.put(it.key, JSONArray(get(it))) }
        return json.toString()
    }

    companion object {
        private const val DIGIT = "[0-9۰-۹٠-٩]"
        private const val ZWNJ = "[\\u200c ]?"
        private const val VS16 = "\\uFE0F?"

        val DEFAULT = SpamPatterns(
            mapOf(
                SpamPatternGroup.CODE_WORDS to listOf(
                    "(کد|رمز)$ZWNJ\\s*(تای?ید|تأیید|ورود|فعال|امنیتی|احراز|بازیابی|شناسایی|عبور|یک\\s*$ZWNJ\\s*بار|پویا|دوم)",
                    "کد\\s*شما",
                    "verification code",
                    "security code",
                    "confirmation code",
                    "login code",
                    "your code",
                    "code is",
                    "passcode",
                    "one[- ]?time",
                    "\\bOTP\\b",
                    "\\b2FA\\b",
                    // Web OTP: the last line is "@site #code".
                    "@[\\w.-]+\\s+#$DIGIT{4,8}",
                ),
                SpamPatternGroup.CODE to listOf(
                    "(?<!$DIGIT)$DIGIT{4,8}(?!$DIGIT)",
                ),
                SpamPatternGroup.TRANSACTIONAL to listOf(
                    "برداشت",
                    "واریز",
                    "موجودی",
                    "تراکنش",
                    "رسید\\s*پرداخت",
                    "پرداخت\\s*موفق",
                ),
                SpamPatternGroup.LINKS to listOf(
                    "https?://",
                    "www\\.",
                    "[\\w-]+\\.(ir|io|com|net|me|ly|app)/",
                ),
                SpamPatternGroup.MARKETING to listOf(
                    "مجانی",
                    "رایگان",
                    "جوایز",
                    "جایزه\\s*ویژه",
                    "جایزه",
                    "قرعه(?:$ZWNJ\\s*کشی)?",
                    "هدیه\\s*رایگان",
                    "هدیه\\s*ویژه",
                    "هدیه",
                    "کد\\s*تخفیف",
                    "درصد\\s*تخفیف",
                    "تخفیف\\s*ویژه",
                    "تخفیف",
                    "جشنواره",
                    "کمپین",
                    "حراجی?",
                    "فروش\\s*ویژه",
                    "فرصت\\s*محدود",
                    "فرصت\\s*طلایی",
                    "فقط\\s*امروز",
                    "آخرین\\s*مهلت",
                    "برنده\\s*شو",
                    "برنده\\s*شد",
                    "شما\\s*برنده",
                    "شانس\\s*برنده",
                    "بسته\\s*ویژه",
                    "بسته\\s*رایگان",
                    "بسته\\s*پیشنهادی",
                    "خرید\\s*بسته",
                    "کلیک\\s*کنید",
                    "ثبت$ZWNJ\\s*نام\\s*کنید",
                    "همین\\s*حالا\\s*بخر",
                    "پیش$ZWNJ\\s*فروش",
                    "پیشنهاد\\s*شگفت",
                ),
                SpamPatternGroup.PROMO_EMOJI to listOf(
                    // Celebrate / gift
                    "🎉", "🎊", "🎁", "🏆", "🥇", "🎂",
                    // Urgency / money / shop
                    "🔥", "💥", "💰", "💸", "💵", "🛒", "📣", "📢", "⏰$VS16", "⏱$VS16",
                    // Food promo
                    "🍕", "🍔", "🍟", "🥤", "🍝", "🍰",
                ),
                SpamPatternGroup.PROMO_EMOJI_WEAK to listOf(
                    "😊", "😃", "😁", "😍", "😌", "☺$VS16", "❤$VS16", "💕", "🌸", "🌹",
                ),
            ),
        )

        /** Groups missing from [json] keep their defaults; unreadable JSON means all defaults. */
        fun fromJson(json: String?): SpamPatterns {
            if (json.isNullOrBlank()) return DEFAULT
            return try {
                val obj = JSONObject(json)
                SpamPatterns(
                    SpamPatternGroup.entries.associateWith { group ->
                        val array = obj.optJSONArray(group.key) ?: return@associateWith DEFAULT[group]
                        (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }
                    },
                )
            } catch (_: JSONException) {
                DEFAULT
            }
        }

        /** Null when [pattern] compiles; otherwise the reason it does not. */
        fun error(pattern: String): String? = try {
            Regex(pattern)
            null
        } catch (e: IllegalArgumentException) {
            e.message?.lineSequence()?.firstOrNull() ?: "invalid"
        }
    }
}

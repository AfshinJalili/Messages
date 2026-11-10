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
    UNSUBSCRIBE("unsubscribe"),
    LINKS("links"),
    MARKETING("marketing"),
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
                SpamPatternGroup.UNSUBSCRIBE to listOf(
                    "لغو",
                    "unsubscribe",
                    "opt[- ]?out",
                ),
                SpamPatternGroup.LINKS to listOf(
                    "https?://",
                    "www\\.",
                    "[\\w-]+\\.(ir|io|com|net|me|ly|app)/",
                ),
                SpamPatternGroup.MARKETING to listOf(
                    "تخفیف",
                    "هدیه",
                    "جشنواره",
                    "جایزه",
                    "قرعه",
                    "کلیک",
                    "رایگان",
                    "بسته",
                    "فروش",
                    "کمپین",
                    "ثبت ?نام",
                    "عضویت",
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

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

/** Why a pattern can't be saved. */
sealed interface PatternProblem {
    /** It doesn't compile; [reason] is the regex engine's first line. */
    data class Invalid(val reason: String) : PatternProblem

    /** It also matches empty text, almost always a stray `|` or an all-optional pattern. */
    data object MatchesEmpty : PatternProblem
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
        /** Start of a Persian word. `\b` sees Persian letters on Android but not on JDK 19+, where tests run. */
        private const val WORD_START = "(?<![\\p{L}\\p{N}_])"
        private const val ZWNJ = "[\\u200c ]?"
        private const val VS16 = "\\uFE0F?"
        /** Common short-link and web TLDs; bare host with optional path, no scheme required. */
        private const val TLD = "ir|io|com|net|me|ly|app|org|co|info|xyz|link|click|site|online|tv|cc|to"

        val DEFAULT = SpamPatterns(
            mapOf(
                SpamPatternGroup.CODE_WORDS to listOf(
                    "$WORD_START(کد|رمز)$ZWNJ\\s*(تای?ید|تأیید|ورود|فعال|امنیتی|احراز|بازیابی|شناسایی|عبور|یک\\s*$ZWNJ\\s*بار|پویا|دوم)",
                    "${WORD_START}کد\\s*شما",
                    "\\bverification code\\b",
                    "\\bsecurity code\\b",
                    "\\bconfirmation code\\b",
                    "\\blogin code\\b",
                    "\\byour code\\b",
                    "\\bcode is\\b",
                    "\\bpasscode\\b",
                    "\\bone[- ]?time\\b",
                    "\\bOTP\\b",
                    "\\b2FA\\b",
                    // Web OTP: the last line is "@site #code".
                    "@[\\w.-]+\\s+#$DIGIT{4,8}",
                ),
                SpamPatternGroup.CODE to listOf(
                    // Not part of a longer number, a date (1403/12/29), a time or an amount (1,500,000).
                    "(?<!$DIGIT[/.,:]?)$DIGIT{4,8}(?![/.,:]?$DIGIT)",
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
                    "(?<![\\w.@])[\\w-]+\\.(?:$TLD)(?:/[\\w./-]*)?(?![\\w.])",
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
                    "کنسرت",
                    "اجرا",
                    "تئاتر",
                    "همایش",
                    "بلیط",
                    "بلیت",
                    "خرید\\s*بلیط",
                    "خرید\\s*بلیت",
                    "خرید\\s*👇",
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
                    // Bulk-layout marks normal people rarely use in texts
                    "✅", "✔$VS16", "☑$VS16", "📍",
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

        /**
         * [pattern] as the filter matches it: folded like the body ([foldSpamText]), so a pattern
         * typed with ZWNJ, Persian digits or Arabic Yeh matches the text it was copied from.
         */
        fun compile(pattern: String): Regex = Regex(foldSpamText(pattern).folded, RegexOption.IGNORE_CASE)

        /** Why [pattern] can't be saved, or null when it can. */
        fun problem(pattern: String): PatternProblem? {
            val regex = try {
                compile(pattern)
            } catch (e: IllegalArgumentException) {
                return PatternProblem.Invalid(e.message?.lineSequence()?.firstOrNull() ?: "invalid")
            }
            return PatternProblem.MatchesEmpty.takeIf { regex.containsMatchIn("") }
        }
    }
}

/** Text as patterns and phrases see it, and where each of its characters came from. */
class SpamText(val folded: String, private val origins: IntArray) {
    /** The characters of the original text that a match in [folded] covers. */
    fun originalRange(range: IntRange): IntRange = origins[range.first]..origins[range.last]
}

/**
 * Folds [text] for matching: Arabic/Persian digits to ASCII, Yeh/Kaf variants to Persian, ZWNJ to a
 * space, and tatweel, direction marks, ZWJ and Arabic diacritics dropped, so «تـخـفـیـف» and «جَشنواره»
 * read as the plain words.
 */
fun foldSpamText(text: String): SpamText {
    val folded = StringBuilder(text.length)
    val origins = IntArray(text.length)
    text.forEachIndexed { index, c ->
        if (c.isIgnoredForMatching()) return@forEachIndexed
        origins[folded.length] = index
        folded.append(
            when (c) {
                '\u064A', '\u0649' -> '\u06CC' // Arabic Yeh / Alef Maqsura → Persian Yeh
                '\u0643' -> '\u06A9' // Arabic Kaf → Persian Kaf
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0') // Persian digits
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660') // Arabic-Indic digits
                '\u200c' -> ' ' // ZWNJ → space
                else -> c
            },
        )
    }
    return SpamText(folded.toString(), origins)
}

private fun Char.isIgnoredForMatching() = when (this) {
    '\u0640', // tatweel
    '\u200D', // ZWJ
    '\u200E', '\u200F', '\u061C', // LRM, RLM, Arabic letter mark
    in '\u064B'..'\u065F', '\u0670', // Arabic diacritics and superscript alef
    -> true
    else -> false
}

/**
 * Turns a stored pattern into ordinary text for the "What the filter looks for" page: no regex
 * syntax, just the Persian word or emoji people would recognize in a message.
 */
fun plainFilterLabels(patterns: List<String>): List<String> = patterns.map { pattern ->
    pattern
        .replace("\\uFE0F?", "")
        .replace("[\\u200c ]?", "\u200c")
        .replace("(?:", "")
        .replace(")?", "")
        .replace("\\s*", "")
        .replace("\\s+", " ")
        .replace("\\\\", "")
        .replace("\\", "")
        .trim()
}.filter { it.isNotEmpty() }.distinct()

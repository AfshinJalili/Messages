package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config

enum class FilterVerdict { ALLOW, BLOCK, NO_VERDICT }

/** Which rule decided, and where in the body its patterns matched. */
enum class FilterRule { ONE_TIME_CODE, TRANSACTIONAL, MARKETING }

data class FilterMatch(val group: SpamPatternGroup, val range: IntRange)

data class FilterResult(
    val verdict: FilterVerdict,
    val rule: FilterRule? = null,
    val matches: List<FilterMatch> = emptyList(),
) {
    companion object {
        val NONE = FilterResult(FilterVerdict.NO_VERDICT)
    }
}

object RuleBasedFilter {

    /**
     * Signal weights used by [explain]: a marketing / event / CTA word is [WORD_WEIGHT], and a
     * strong promo or layout emoji (🎉, ✅, 📍, …) is the same weight. Links and named senders are
     * companions. One strong signal alone from a numeric sender stays in the inbox; one plus a
     * companion, or two signals, goes to Spam. Opt-out lines such as «لغو11» are never a signal.
     */
    const val WORD_WEIGHT = 2
    const val EMOJI_WEIGHT = 2
    /** Block without a companion when total weight reaches this (e.g. one word + one emoji). */
    const val BLOCK_WEIGHT = 3
    /** With a link or named sender, this weight is enough (one marketing word or one strong emoji). */
    const val BLOCK_WEIGHT_WITH_COMPANION = 2

    /**
     * A code is a one-time code only this many characters or fewer from a code word, in the same
     * sentence: «کد تایید شما: 482913», but not a year at the end of "Your code is LUCKY. Win… 2025".
     */
    const val CODE_WINDOW = 30
    private val SENTENCE_END = Regex("[.!?؟…](?:\\s|$)")

    fun evaluate(context: Context, sender: String, body: String): FilterVerdict = explain(context, sender, body).verdict

    fun explain(context: Context, sender: String, body: String): FilterResult {
        if (!context.config.ruleFilterEnabled) return FilterResult.NONE
        return explain(context.config.spamPatterns, sender, body)
    }

    /**
     * Ordered, high-precision first. A one-time code (a code word and a code) and transactional
     * bank/receipt language are always allowed: service SMS carry links and the mandatory "لغو11"
     * line, which must not send them to Spam. Marketing scores word and strong-emoji weights
     * equally, with a link or named sender as a companion — never an unsubscribe shortcut.
     */
    fun explain(patterns: SpamPatterns, sender: String, body: String): FilterResult {
        if (body.isBlank()) return FilterResult.NONE
        val text = foldSpamText(body)
        val compiled = CompiledPatterns.of(patterns)
        fun find(group: SpamPatternGroup) = compiled.find(group, text.folded)
        // Matches are found in the folded text; the result points into the body as received.
        fun result(verdict: FilterVerdict, rule: FilterRule, matches: List<FilterMatch>) =
            FilterResult(verdict, rule, matches.map { FilterMatch(it.group, text.originalRange(it.range)) })

        val oneTimeCode = codeNearWord(text.folded, find(SpamPatternGroup.CODE_WORDS), find(SpamPatternGroup.CODE))
        if (oneTimeCode != null) {
            return result(FilterVerdict.ALLOW, FilterRule.ONE_TIME_CODE, oneTimeCode)
        }

        val transactional = find(SpamPatternGroup.TRANSACTIONAL)
        if (transactional.isNotEmpty()) {
            return result(FilterVerdict.ALLOW, FilterRule.TRANSACTIONAL, transactional)
        }

        val signals = compiled.signalMatches(listOf(SpamPatternGroup.MARKETING, SpamPatternGroup.PROMO_EMOJI), text.folded)
        if (signals.isEmpty()) return FilterResult.NONE
        val marketing = signals.filter { it.group == SpamPatternGroup.MARKETING }
        val strongEmoji = signals.filter { it.group == SpamPatternGroup.PROMO_EMOJI }

        val weight = marketing.size * WORD_WEIGHT + strongEmoji.size * EMOJI_WEIGHT
        val links = find(SpamPatternGroup.LINKS)
        val namedSender = isAlphaSender(sender)
        val companion = links.isNotEmpty() || namedSender
        val block = weight >= BLOCK_WEIGHT || (weight >= BLOCK_WEIGHT_WITH_COMPANION && companion)
        if (block) {
            return result(FilterVerdict.BLOCK, FilterRule.MARKETING, marketing + strongEmoji + links)
        }
        return FilterResult.NONE
    }

    /** The first code word and code within [CODE_WINDOW] of each other in one sentence, or null. */
    private fun codeNearWord(text: String, words: List<FilterMatch>, codes: List<FilterMatch>): List<FilterMatch>? {
        for (word in words) {
            for (code in codes) {
                val gap = minOf(word.range.last, code.range.last) + 1 until maxOf(word.range.first, code.range.first)
                if (gap.isEmpty()) return listOf(word, code)
                if (gap.last - gap.first + 1 > CODE_WINDOW) continue
                if (SENTENCE_END.containsMatchIn(text.substring(gap))) continue
                return listOf(word, code)
            }
        }
        return null
    }

    private fun isAlphaSender(sender: String) =
        sender.any { it.isLetter() } || sender.trimStart().firstOrNull().let { it == '*' || it == '#' }

    /** Compiled once per pattern set; patterns that do not compile are skipped. */
    private class CompiledPatterns(private val regexes: Map<SpamPatternGroup, List<Regex>>) {
        fun find(group: SpamPatternGroup, body: String): List<FilterMatch> =
            regexes[group].orEmpty().flatMap { regex ->
                regex.findAll(body).filter { !it.range.isEmpty() }.map { FilterMatch(group, it.range) }.toList()
            }

        /**
         * One match per pattern that hits, so each distinct pattern is one signal. A match inside
         * one already counted is the same words again («تخفیف» inside «کد تخفیف»), so it isn't
         * counted; longer matches count first. Empty matches are skipped, so `حراج|` still fires
         * on «حراج».
         */
        fun signalMatches(groups: List<SpamPatternGroup>, body: String): List<FilterMatch> {
            val patterns = groups.flatMap { group -> regexes[group].orEmpty().map { group to it } }
            val candidates = patterns.withIndex().flatMap { (pattern, entry) ->
                val (group, regex) = entry
                regex.findAll(body).filter { !it.range.isEmpty() }.map { Triple(pattern, group, it.range) }.toList()
            }.sortedByDescending { (_, _, range) -> range.last - range.first }
            val counted = ArrayList<FilterMatch>()
            val countedPatterns = HashSet<Int>()
            for ((pattern, group, range) in candidates) {
                if (pattern in countedPatterns) continue
                if (counted.any { range.first >= it.range.first && range.last <= it.range.last }) continue
                countedPatterns += pattern
                counted += FilterMatch(group, range)
            }
            return counted
        }

        companion object {
            @Volatile
            private var cached: Pair<SpamPatterns, CompiledPatterns>? = null

            fun of(patterns: SpamPatterns): CompiledPatterns {
                cached?.let { (key, value) -> if (key == patterns) return value }
                val compiled = CompiledPatterns(
                    patterns.groups.mapValues { (_, list) ->
                        list.mapNotNull { runCatching { SpamPatterns.compile(it) }.getOrNull() }
                    },
                )
                cached = patterns to compiled
                return compiled
            }
        }
    }
}

/**
 * Where each blocked phrase occurs in [body], ignoring case and folded like the filter's patterns
 * ([foldSpamText]), so «لغو11» also catches «لغو۱۱». Ranges point into [body] as received.
 */
fun keywordMatches(keywords: Collection<String>, body: String): List<IntRange> {
    val text = foldSpamText(body)
    return keywords.map { foldSpamText(it).folded }.filter { it.isNotBlank() }.flatMap { keyword ->
        generateSequence(text.folded.indexOf(keyword, ignoreCase = true).takeIf { it >= 0 }) { from ->
            text.folded.indexOf(keyword, from + keyword.length, ignoreCase = true).takeIf { it >= 0 }
        }.map { text.originalRange(it until it + keyword.length) }.toList()
    }
}

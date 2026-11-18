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
     * Signal weights used by [explain]: a marketing / event / CTA word is [WORD_WEIGHT], a strong
     * promo or layout emoji is [EMOJI_WEIGHT]. Opt-out lines and links are companions, not scored
     * signals — emoji alone (even with a link) must not block, so a delivery SMS with 📍 and a
     * tracking URL stays in the inbox, while an event word plus a bare short link does not.
     */
    const val WORD_WEIGHT = 2
    const val EMOJI_WEIGHT = 1
    /** Block without a companion when total weight reaches this (e.g. one word + one emoji). */
    const val BLOCK_WEIGHT = 3
    /** With a link, named sender, or opt-out companion, this weight is enough (one marketing word). */
    const val BLOCK_WEIGHT_WITH_COMPANION = 2

    fun evaluate(context: Context, sender: String, body: String): FilterVerdict = explain(context, sender, body).verdict

    fun explain(context: Context, sender: String, body: String): FilterResult {
        if (!context.config.ruleFilterEnabled) return FilterResult.NONE
        return explain(context.config.spamPatterns, sender, body)
    }

    /**
     * Ordered, high-precision first. A one-time code (a code word and a code) and transactional
     * bank/receipt language are always allowed: service SMS carry links and the mandatory "لغو11"
     * line, which must not send them to Spam. Marketing scores word and emoji weights; a link,
     * named sender, or opt-out line is a companion for word signals — never an emoji-only shortcut.
     */
    fun explain(patterns: SpamPatterns, sender: String, body: String): FilterResult {
        if (body.isBlank()) return FilterResult.NONE
        val normalized = normalizeSpamText(body)
        val compiled = CompiledPatterns.of(patterns)
        fun find(group: SpamPatternGroup) = compiled.find(group, normalized)
        fun signals(group: SpamPatternGroup) = compiled.signalMatches(group, normalized)

        val codeWords = find(SpamPatternGroup.CODE_WORDS)
        val codes = find(SpamPatternGroup.CODE)
        if (codeWords.isNotEmpty() && codes.isNotEmpty()) {
            return FilterResult(FilterVerdict.ALLOW, FilterRule.ONE_TIME_CODE, codeWords + codes)
        }

        val transactional = find(SpamPatternGroup.TRANSACTIONAL)
        if (transactional.isNotEmpty()) {
            return FilterResult(FilterVerdict.ALLOW, FilterRule.TRANSACTIONAL, transactional)
        }

        val marketing = signals(SpamPatternGroup.MARKETING)
        val strongEmoji = signals(SpamPatternGroup.PROMO_EMOJI)
        if (marketing.isEmpty() && strongEmoji.isEmpty()) return FilterResult.NONE

        val weight = marketing.size * WORD_WEIGHT + strongEmoji.size * EMOJI_WEIGHT
        val links = find(SpamPatternGroup.LINKS)
        val optOut = find(SpamPatternGroup.OPT_OUT)
        val namedSender = isAlphaSender(sender)
        // Opt-out is a companion like a link. It is not returned in matches so Why never highlights «لغو».
        val companion = links.isNotEmpty() || namedSender || optOut.isNotEmpty()
        val block = weight >= BLOCK_WEIGHT || (weight >= BLOCK_WEIGHT_WITH_COMPANION && companion)
        if (block) {
            return FilterResult(
                FilterVerdict.BLOCK,
                FilterRule.MARKETING,
                marketing + strongEmoji + links,
            )
        }
        return FilterResult.NONE
    }

    private fun isAlphaSender(sender: String) =
        sender.any { it.isLetter() } || sender.trimStart().firstOrNull().let { it == '*' || it == '#' }

    /** Compiled once per pattern set; patterns that do not compile are skipped. */
    private class CompiledPatterns(private val regexes: Map<SpamPatternGroup, List<Regex>>) {
        fun find(group: SpamPatternGroup, body: String): List<FilterMatch> =
            regexes[group].orEmpty().flatMap { regex ->
                regex.findAll(body).filter { !it.range.isEmpty() }.map { FilterMatch(group, it.range) }.toList()
            }

        /** One match per pattern that hits, so each distinct pattern is one signal. */
        fun signalMatches(group: SpamPatternGroup, body: String): List<FilterMatch> =
            regexes[group].orEmpty().mapNotNull { regex ->
                regex.find(body)?.takeIf { !it.range.isEmpty() }?.let { FilterMatch(group, it.range) }
            }

        companion object {
            @Volatile
            private var cached: Pair<SpamPatterns, CompiledPatterns>? = null

            fun of(patterns: SpamPatterns): CompiledPatterns {
                cached?.let { (key, value) -> if (key == patterns) return value }
                val compiled = CompiledPatterns(
                    patterns.groups.mapValues { (_, list) ->
                        list.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
                    },
                )
                cached = patterns to compiled
                return compiled
            }
        }
    }
}

/** Where each blocked phrase occurs in [body], for highlighting why a keyword rule matched. */
fun keywordMatches(keywords: Collection<String>, body: String): List<IntRange> = keywords.filter { it.isNotEmpty() }.flatMap { keyword ->
    generateSequence(body.indexOf(keyword, ignoreCase = true).takeIf { it >= 0 }) { from ->
        body.indexOf(keyword, from + keyword.length, ignoreCase = true).takeIf { it >= 0 }
    }.map { it until it + keyword.length }.toList()
}

package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config

enum class FilterVerdict { ALLOW, BLOCK, NO_VERDICT }

/** Which rule decided, and where in the body its patterns matched. */
enum class FilterRule { ONE_TIME_CODE, UNSUBSCRIBE, MARKETING }

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

    fun evaluate(context: Context, sender: String, body: String): FilterVerdict = explain(context, sender, body).verdict

    fun explain(context: Context, sender: String, body: String): FilterResult {
        if (!context.config.ruleFilterEnabled) return FilterResult.NONE
        return explain(context.config.spamPatterns, sender, body)
    }

    /**
     * Ordered, high-precision first. A one-time code (a code word and a code) is always allowed:
     * service SMS carry links and the mandatory "لغو11" line, which must not send codes to Spam.
     */
    fun explain(patterns: SpamPatterns, sender: String, body: String): FilterResult {
        if (body.isBlank()) return FilterResult.NONE
        val compiled = CompiledPatterns.of(patterns)
        fun find(group: SpamPatternGroup) = compiled.find(group, body)

        val codeWords = find(SpamPatternGroup.CODE_WORDS)
        val codes = find(SpamPatternGroup.CODE)
        if (codeWords.isNotEmpty() && codes.isNotEmpty()) {
            return FilterResult(FilterVerdict.ALLOW, FilterRule.ONE_TIME_CODE, codeWords + codes)
        }
        val unsubscribe = find(SpamPatternGroup.UNSUBSCRIBE)
        if (unsubscribe.isNotEmpty()) {
            return FilterResult(FilterVerdict.BLOCK, FilterRule.UNSUBSCRIBE, unsubscribe)
        }
        val marketing = find(SpamPatternGroup.MARKETING)
        if (marketing.isNotEmpty()) {
            val links = find(SpamPatternGroup.LINKS)
            if (links.isNotEmpty() || isAlphaSender(sender)) {
                return FilterResult(FilterVerdict.BLOCK, FilterRule.MARKETING, marketing + links)
            }
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

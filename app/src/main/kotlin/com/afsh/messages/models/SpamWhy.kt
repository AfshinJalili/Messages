package com.afsh.messages.models

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.BLOCK_REASON_KEYWORD
import com.afsh.messages.helpers.BLOCK_REASON_RULE
import com.afsh.messages.helpers.FilterRule
import com.afsh.messages.helpers.FilterVerdict
import com.afsh.messages.helpers.RuleBasedFilter
import com.afsh.messages.helpers.keywordMatches

/**
 * Why a message is in Spam: the rule as words, and where in the body it matched. [ranges] are
 * end-inclusive, sorted and never overlap. [stale] means the current rules no longer match it.
 */
@Immutable
data class SpamWhy(
    @StringRes val label: Int,
    val ranges: List<IntRange> = emptyList(),
    val stale: Boolean = false,
) {
    fun matchedWords(body: String): List<String> = ranges.map { body.substring(it) }.distinct()
}

/** Explains with today's phrases and patterns, so it can differ from what matched on arrival. */
fun Context.spamWhy(reason: Int, address: String, body: String): SpamWhy = when (reason) {
    BLOCK_REASON_KEYWORD -> {
        val ranges = keywordMatches(config.blockedKeywords, body)
        SpamWhy(R.string.spam_why_phrase, merge(ranges), stale = ranges.isEmpty())
    }

    BLOCK_REASON_RULE -> {
        val result = RuleBasedFilter.explain(config.spamPatterns, address, body)
        val label = when (result.rule) {
            FilterRule.UNSUBSCRIBE -> R.string.spam_why_unsubscribe
            FilterRule.MARKETING -> R.string.spam_why_marketing
            else -> R.string.block_reason_rule
        }
        val blocked = result.verdict == FilterVerdict.BLOCK
        SpamWhy(label, if (blocked) merge(result.matches.map { it.range }) else emptyList(), stale = !blocked)
    }

    else -> SpamWhy(spamReasonLabel(reason))
}

/** Joins overlapping or touching ranges so each character is highlighted once. */
internal fun merge(ranges: List<IntRange>): List<IntRange> {
    val merged = ArrayList<IntRange>()
    ranges.sortedBy { it.first }.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

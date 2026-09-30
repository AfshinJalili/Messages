package org.fossify.messages.ui.search

import org.fossify.messages.helpers.searchRanges
import androidx.annotation.StringRes
import org.fossify.messages.R
import org.fossify.messages.models.SearchMatch

const val SEARCH_DEBOUNCE_MS = 200L
const val SEARCH_TRANSITION_MS = 250

enum class SearchFilter(@StringRes val label: Int) {
    ALL(R.string.inbox_all),
    MESSAGES(R.string.messages),
    PEOPLE(R.string.search_people),
    MEDIA(R.string.search_media),
}

data class SearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val matches: List<SearchMatch> = emptyList(),
    val recent: List<String> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    val entry get() = query.isBlank()
    val showingHistory get() = entry && filter == SearchFilter.ALL
    val visibleMatches get() = matches.filter {
        when (filter) {
            SearchFilter.ALL -> true
            SearchFilter.MESSAGES -> !it.person
            SearchFilter.PEOPLE -> it.person
            SearchFilter.MEDIA -> if (entry) it.photo else it.media
        }
    }
}

/** Keep enough context before the first literal match to make a long message recognisable. */
fun searchExcerpt(text: String, query: String): String {
    val match = searchRanges(text, query).firstOrNull()?.first ?: -1
    val start = (match - EXCERPT_CONTEXT_CHARS).coerceAtLeast(0)
    return if (start == 0) text else "…${text.substring(start)}"
}

private const val EXCERPT_CONTEXT_CHARS = 40

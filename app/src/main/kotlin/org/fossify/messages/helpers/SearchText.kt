package org.fossify.messages.helpers

/** Arabic and Persian forms share positions, so highlighting never rewrites the displayed text. */
fun String.normalizeSearchText() = replace('ي', 'ی').replace('ك', 'ک')

fun literalSearchPattern(text: String): String = "%" + text.normalizeSearchText()
    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

fun searchRanges(text: String, query: String): List<IntRange> {
    val term = query.trim().normalizeSearchText()
    if (term.isEmpty()) return emptyList()
    val normalized = text.normalizeSearchText()
    return buildList {
        var start = normalized.indexOf(term, ignoreCase = true)
        while (start >= 0) {
            add(start until start + term.length)
            start = normalized.indexOf(term, start + term.length, ignoreCase = true)
        }
    }
}

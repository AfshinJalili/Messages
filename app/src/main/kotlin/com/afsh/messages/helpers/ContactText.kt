package com.afsh.messages.helpers

import org.fossify.commons.models.PhoneNumber

/**
 * A shared contact as plain SMS text: the name, then each phone number on its own line. Numbers that
 * normalize the same are listed once. Returns null when the contact has no number to share.
 */
fun contactAsText(name: String, numbers: List<PhoneNumber>): String? {
    val lines = numbers
        .filter { it.value.isNotBlank() }
        .distinctBy { it.normalizedNumber.ifBlank { it.value.filter(Char::isDigit) } }
        .map { it.value.trim() }
    if (lines.isEmpty()) return null
    return (listOf(name.trim()).filter { it.isNotEmpty() } + lines).joinToString("\n")
}

/** Inserts [block] at the cursor, on lines of its own; returns the new text and the cursor after it. */
fun insertBlock(text: String, at: Int, block: String): Pair<String, Int> {
    val before = if (at > 0 && text[at - 1] != '\n') "\n" else ""
    val after = if (at < text.length && text[at] != '\n') "\n" else ""
    val inserted = before + block + after
    return text.replaceRange(at, at, inserted) to at + before.length + block.length
}

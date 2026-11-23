package com.afsh.messages.helpers

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * The filter rules one device moves to another (51): app-level always-spam numbers, never-spam
 * allowlist, and phrases.
 */
data class BlockRules(
    val numbers: Set<String>,
    val phrases: Set<String>,
    val allowed: Set<String> = emptySet(),
)

/**
 * Reads and writes filter rules as
 * `{"version":1,"numbers":[…],"phrases":[…],"allowed":[…]}`. Older files without `allowed` still
 * import (empty allowlist). Files from the old keyword export (keywords joined by
 * [BLOCKED_KEYWORDS_EXPORT_DELIMITER]) still import as phrases.
 */
object BlockRulesFile {
    private const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun write(rules: BlockRules, output: OutputStream) {
        val payload = BlockRulesJson(
            version = VERSION,
            numbers = rules.numbers.sorted(),
            phrases = rules.phrases.sorted(),
            allowed = rules.allowed.sorted(),
        )
        output.bufferedWriter().use { it.write(json.encodeToString(BlockRulesJson.serializer(), payload)) }
    }

    fun read(input: InputStream): BlockRules {
        val text = input.bufferedReader().use { it.readText() }.trim()
        return try {
            val parsed = json.decodeFromString(BlockRulesJson.serializer(), text)
            BlockRules(parsed.numbers.toSet(), parsed.phrases.toSet(), parsed.allowed.toSet())
        } catch (_: SerializationException) {
            val phrases = text.split(BLOCKED_KEYWORDS_EXPORT_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
            BlockRules(emptySet(), phrases.toSet())
        }
    }

    @Serializable
    private data class BlockRulesJson(
        val version: Int = VERSION,
        val numbers: List<String> = emptyList(),
        val phrases: List<String> = emptyList(),
        val allowed: List<String> = emptyList(),
    )
}

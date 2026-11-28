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
 * [BLOCKED_KEYWORDS_EXPORT_DELIMITER]) still import as phrases; that export was never JSON.
 */
object BlockRulesFile {
    private const val VERSION = 1
    private const val BYTE_ORDER_MARK = "\uFEFF"

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

    /**
     * Entries come back trimmed, without blanks, and with each phrase once ignoring case.
     *
     * @throws SerializationException when the text is JSON but not a rules object, such as a
     * message backup or a cut-off file, so nothing from it is imported.
     */
    fun read(input: InputStream): BlockRules {
        val text = input.bufferedReader().use { it.readText() }.removePrefix(BYTE_ORDER_MARK).trim()
        if (!text.startsWith('{') && !text.startsWith('[')) {
            return BlockRules(emptySet(), phrases(text.split(BLOCKED_KEYWORDS_EXPORT_DELIMITER)))
        }
        val parsed = json.decodeFromString(BlockRulesJson.serializer(), text)
        return BlockRules(entries(parsed.numbers), phrases(parsed.phrases), entries(parsed.allowed))
    }

    private fun entries(values: List<String>): Set<String> = values.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun phrases(values: List<String>): Set<String> = entries(values).distinctBy { it.lowercase() }.toSet()

    @Serializable
    private data class BlockRulesJson(
        val version: Int = VERSION,
        val numbers: List<String> = emptyList(),
        val phrases: List<String> = emptyList(),
        val allowed: List<String> = emptyList(),
    )
}

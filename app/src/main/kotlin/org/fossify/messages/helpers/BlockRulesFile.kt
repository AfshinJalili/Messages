package org.fossify.messages.helpers

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** The block rules one device moves to another (51): app-level blocked numbers and phrases. */
data class BlockRules(val numbers: Set<String>, val phrases: Set<String>)

/**
 * Reads and writes block rules as `{"version":1,"numbers":[…],"phrases":[…]}`. Files from the
 * old keyword export (keywords joined by [BLOCKED_KEYWORDS_EXPORT_DELIMITER]) still import as
 * phrases.
 */
object BlockRulesFile {
    private const val VERSION = 1

    fun write(rules: BlockRules, output: OutputStream) {
        val json = JSONObject()
            .put("version", VERSION)
            .put("numbers", JSONArray(rules.numbers.sorted()))
            .put("phrases", JSONArray(rules.phrases.sorted()))
        output.bufferedWriter().use { it.write(json.toString(2)) }
    }

    fun read(input: InputStream): BlockRules {
        val text = input.bufferedReader().use { it.readText() }.trim()
        return try {
            val json = JSONObject(text)
            BlockRules(json.strings("numbers"), json.strings("phrases"))
        } catch (_: JSONException) {
            val phrases = text.split(BLOCKED_KEYWORDS_EXPORT_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
            BlockRules(emptySet(), phrases.toSet())
        }
    }

    private fun JSONObject.strings(name: String): Set<String> {
        val array = optJSONArray(name) ?: return emptySet()
        return (0 until array.length()).map { array.getString(it).trim() }.filter { it.isNotEmpty() }.toSet()
    }
}

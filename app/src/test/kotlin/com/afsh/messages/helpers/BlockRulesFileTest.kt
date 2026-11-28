package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BlockRulesFileTest {

    @Test
    fun `write then read keeps numbers phrases and allowed`() {
        val original = BlockRules(
            numbers = setOf("5550100", "+989121111111"),
            phrases = setOf("free prize", "winner"),
            allowed = setOf("09121234567", "BANK"),
        )
        val bytes = ByteArrayOutputStream().also { BlockRulesFile.write(original, it) }.toByteArray()
        val restored = BlockRulesFile.read(ByteArrayInputStream(bytes))
        assertEquals(original.numbers, restored.numbers)
        assertEquals(original.phrases, restored.phrases)
        assertEquals(original.allowed, restored.allowed)
    }

    @Test
    fun `older JSON without allowed imports an empty allowlist`() {
        val json = """{"version":1,"numbers":["555"],"phrases":["prize"]}"""
        val rules = BlockRulesFile.read(ByteArrayInputStream(json.toByteArray()))
        assertEquals(setOf("555"), rules.numbers)
        assertEquals(setOf("prize"), rules.phrases)
        assertTrue(rules.allowed.isEmpty())
    }

    @Test
    fun `legacy CSV still imports as phrases`() {
        val rules = BlockRulesFile.read(ByteArrayInputStream("winner,free prize".toByteArray()))
        assertTrue(rules.numbers.isEmpty())
        assertEquals(setOf("winner", "free prize"), rules.phrases)
        assertTrue(rules.allowed.isEmpty())
    }

    private fun read(text: String) = BlockRulesFile.read(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `a rules file saved with a byte order mark still imports`() {
        val rules = read("\uFEFF{\"version\":1,\"numbers\":[\"555\"],\"phrases\":[\"prize\"]}")
        assertEquals(setOf("555"), rules.numbers)
        assertEquals(setOf("prize"), rules.phrases)
    }

    @Test
    fun `a message backup is not a rules file and imports nothing`() {
        val backup = """[{"address":"+989121234567","body":"Hi, ok, see you"}]"""
        assertThrows(IllegalArgumentException::class.java) { read(backup) }
    }

    @Test
    fun `a truncated rules file fails instead of importing its text as phrases`() {
        assertThrows(IllegalArgumentException::class.java) { read("""{"version":1,"phrases":["prize","""") }
    }

    @Test
    fun `entries are trimmed, blanks dropped and phrases kept once ignoring case`() {
        val rules = read(
            """{"numbers":[" 555 ",""],"phrases":[""," ","  Prize ","prize","PRIZE","win"],"allowed":["  ","BANK "]}""",
        )
        assertEquals(setOf("555"), rules.numbers)
        assertEquals(setOf("Prize", "win"), rules.phrases)
        assertEquals(setOf("BANK"), rules.allowed)
    }
}

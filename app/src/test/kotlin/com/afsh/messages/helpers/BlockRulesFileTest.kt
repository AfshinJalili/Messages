package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
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
}

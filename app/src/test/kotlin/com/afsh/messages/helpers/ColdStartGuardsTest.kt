package com.afsh.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source guards for cold-start behaviour that must not regress: launch uses a dirty-check
 * reconcile, and the inbox DAO must not reintroduce correlated message subqueries.
 */
class ColdStartGuardsTest {

    private val root = File("src/main/kotlin/com/afsh/messages")

    @Test
    fun `main activity launches with dirty-check reconcile not a forced full pass`() {
        val text = File(root, "activities/MainActivity.kt").readText()
        assertTrue(text.contains("scheduleLaunchReconcile"))
        assertFalse(
            Regex("""scheduleProviderReconcile\s*\(\s*this\s*,\s*immediate\s*=\s*true\s*\)""")
                .containsMatchIn(text)
        )
        assertTrue(text.contains("roomSnapshotReceived"))
        assertTrue(text.contains("messagesImported"))
        assertFalse(text.contains("providerReconcileActive && inboxConversations.isEmpty()"))
    }

    @Test
    fun `inbox repository exposes dirty-check launch path`() {
        val text = File(root, "helpers/InboxRepository.kt").readText()
        assertTrue(text.contains("fun scheduleLaunchReconcile"))
        assertTrue(text.contains("needsFullReconcile"))
        assertTrue(text.contains("TelephonyReader(context).threadDigest()"))
        assertTrue(text.contains("force"))
    }

    @Test
    fun `inbox list queries read denormalized preview columns without message subqueries`() {
        val text = File(root, "interfaces/ConversationsDao.kt").readText()
        assertTrue(text.contains("preview_snippet as new_snippet"))
        assertTrue(text.contains("all_spam = 0"))
        val inboxSql = Regex(
            """@Query\("(SELECT .*?)"\)\s+fun (?:getNonArchivedWithLatestSnippet|observeNonArchivedWithLatestSnippet)"""
        ).findAll(text).map { it.groupValues[1] }.toList()
        assertTrue(inboxSql.size >= 2)
        inboxSql.forEach { sql ->
            val expanded = sql.replace("\$PREVIEW_COLUMNS", "preview_snippet as new_snippet, preview_date as new_date, *")
            assertFalse(expanded.contains("FROM messages", ignoreCase = true))
            assertFalse(expanded.contains("spam_messages", ignoreCase = true))
            assertFalse(expanded.contains("recycle_bin_messages", ignoreCase = true))
        }
    }

    @Test
    fun `splash activity is gone`() {
        assertFalse(File(root, "activities/SplashActivity.kt").exists())
    }
}

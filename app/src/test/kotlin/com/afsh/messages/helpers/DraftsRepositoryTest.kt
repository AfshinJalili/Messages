package com.afsh.messages.helpers

import com.afsh.messages.interfaces.DraftsDao
import com.afsh.messages.models.Draft
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftsRepositoryTest {

    private class FakeDraftsDao : DraftsDao {
        val rows = LinkedHashMap<Long, Draft>()
        override fun insertOrUpdate(draft: Draft): Long {
            rows[draft.threadId] = draft
            return draft.threadId
        }
        override fun getAll(): List<Draft> = rows.values.toList()
        override fun getDraftById(threadId: Long): Draft? = rows[threadId]
        override fun delete(threadId: Long): Int = if (rows.remove(threadId) != null) 1 else 0
    }

    private val dao = FakeDraftsDao()
    private val errors = ArrayList<Exception>()
    private val repository = DraftsRepository(dao, clock = { 42L }, onError = { errors += it })

    @Test
    fun `a saved draft is read back for its thread`() {
        repository.save(threadId = 7, body = "hello")

        assertEquals("hello", repository.get(7))
        assertEquals(Draft(threadId = 7, body = "hello", date = 42L), dao.rows[7])
    }

    @Test
    fun `storing empty text removes the draft`() {
        repository.save(threadId = 7, body = "hello")

        repository.store(threadId = 7, text = "", hasAttachments = false)

        assertEquals("", repository.get(7))
        assertEquals(emptyMap<Long, String>(), repository.getAll())
    }

    @Test
    fun `storing text with attachments removes the draft because attachments are not kept`() {
        repository.save(threadId = 7, body = "old")

        repository.store(threadId = 7, text = "caption", hasAttachments = true)

        assertEquals("", repository.get(7))
    }

    @Test
    fun `storing plain text saves it`() {
        repository.store(threadId = 7, text = "hi", hasAttachments = false)

        assertEquals("hi", repository.get(7))
    }

    @Test
    fun `all drafts are keyed by thread`() {
        repository.save(threadId = 1, body = "a")
        repository.save(threadId = 2, body = "b")

        assertEquals(mapOf(1L to "a", 2L to "b"), repository.getAll())
    }

    @Test
    fun `a failing database reads as no drafts and reports write failures`() {
        val broken = object : DraftsDao {
            override fun insertOrUpdate(draft: Draft): Long = throw IllegalStateException("write")
            override fun getAll(): List<Draft> = throw IllegalStateException("read")
            override fun getDraftById(threadId: Long): Draft? = throw IllegalStateException("read")
            override fun delete(threadId: Long): Int = throw IllegalStateException("delete")
        }
        val failing = DraftsRepository(broken, clock = { 0L }, onError = { errors += it })

        assertEquals("", failing.get(7))
        assertEquals(emptyMap<Long, String>(), failing.getAll())
        failing.save(threadId = 7, body = "x")
        failing.delete(threadId = 7)

        assertEquals(listOf("write", "delete"), errors.map { it.message })
    }
}

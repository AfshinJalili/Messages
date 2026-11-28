package com.afsh.messages.helpers

import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.models.Message
import com.afsh.messages.models.RecycleBinMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class RecycleBinRepositoryTest {

    /**
     * Room's recycle-bin tables in memory, keyed like Room by id and transport (SMS and MMS ids
     * overlap); any DAO call the repository should not make throws.
     */
    private class FakeMessagesDao : MessagesDao by unsupported() {
        val messages = LinkedHashMap<Pair<Long, Boolean>, Message>()
        val bin = LinkedHashMap<Pair<Long, Boolean>, Long>()

        override fun getThreadIdForMessage(id: Long, isMms: Boolean): Long? = messages[id to isMms]?.threadId
        override fun insertRecycleBinEntry(recycleBinMessage: RecycleBinMessage) {
            bin[recycleBinMessage.id to recycleBinMessage.isMMS] = recycleBinMessage.deletedTS
        }
        override fun deleteFromRecycleBin(id: Long, isMms: Boolean) {
            bin.remove(id to isMms)
        }
        override fun deleteThreadMessagesFromRecycleBin(threadId: Long) {
            bin.keys.removeAll { messages[it]?.threadId == threadId }
        }
        override fun getThreadMessagesFromRecycleBin(threadId: Long): List<Message> =
            bin.keys.mapNotNull(messages::get).filter { it.threadId == threadId }
        override fun getAllRecycleBinMessages(): List<Message> = bin.keys.mapNotNull(messages::get)
        override fun getOldRecycleBinMessages(timestamp: Long): List<Message> =
            bin.filterValues { it < timestamp }.keys.mapNotNull(messages::get)
    }

    private val dao = FakeMessagesDao()
    private val refreshed = ArrayList<Long>()
    private val deleted = ArrayList<Pair<Long, Boolean>>()
    private val errors = ArrayList<Exception>()
    private var now = 1_000_000L
    private val repository = RecycleBinRepository(
        dao = dao,
        deleteMessage = { id, isMms -> deleted += id to isMms },
        refreshPreview = { refreshed += it },
        clock = { now },
        onError = { errors += it },
    )

    private fun message(id: Long, threadId: Long, isMms: Boolean = false) = Message(
        id = id, body = "", type = 1, status = 0, participants = ArrayList(), date = 0, read = true,
        threadId = threadId, isMMS = isMms, attachment = null, senderPhoneNumber = "", senderName = "",
        senderPhotoUri = "", subscriptionId = 0,
    ).also { dao.messages[id to isMms] = it }

    @Test
    fun `moving a message stamps the deletion time and refreshes its conversation`() {
        message(id = 5, threadId = 9)

        repository.moveToRecycleBin(5, isMms = false)

        assertEquals(mapOf((5L to false) to now), dao.bin)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `restoring a message takes it out of the bin and refreshes its conversation`() {
        message(id = 5, threadId = 9)
        repository.moveToRecycleBin(5, isMms = false)
        refreshed.clear()

        repository.restore(5, isMms = false)

        assertEquals(emptyMap<Pair<Long, Boolean>, Long>(), dao.bin)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `a message whose thread is unknown is still binned without a refresh`() {
        repository.moveToRecycleBin(77, isMms = false)

        assertEquals(mapOf((77L to false) to now), dao.bin)
        assertEquals(emptyList<Long>(), refreshed)
    }

    @Test
    fun `restoring a conversation restores only that conversation`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 9)
        message(id = 3, threadId = 4)
        listOf(1L, 2L, 3L).forEach { repository.moveToRecycleBin(it, isMms = false) }
        refreshed.clear()

        repository.restoreConversation(9)

        assertEquals(setOf(3L to false), dao.bin.keys)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `emptying a conversation erases only its binned messages`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 9, isMms = true)
        message(id = 3, threadId = 9)
        message(id = 4, threadId = 4)
        repository.moveToRecycleBin(1, isMms = false)
        repository.moveToRecycleBin(2, isMms = true)
        repository.moveToRecycleBin(4, isMms = false)

        repository.emptyConversation(9)

        assertEquals(listOf(1L to false, 2L to true), deleted)
    }

    @Test
    fun `emptying the bin erases every binned message`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 4, isMms = true)
        message(id = 3, threadId = 4)
        repository.moveToRecycleBin(1, isMms = false)
        repository.moveToRecycleBin(2, isMms = true)

        repository.emptyAll()

        assertEquals(listOf(1L to false, 2L to true), deleted)
    }

    @Test
    fun `purging erases messages binned more than thirty days ago and keeps newer ones`() {
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 9)
        message(id = 3, threadId = 9)
        now = 0L
        repository.moveToRecycleBin(1, isMms = false)
        now = 1L
        repository.moveToRecycleBin(2, isMms = false)
        repository.moveToRecycleBin(3, isMms = false)
        now = thirtyDays + 1L

        repository.purgeExpired()

        assertEquals(listOf(1L to false), deleted)
    }

    @Test
    fun `an SMS and an MMS sharing an id are binned and restored on their own`() {
        message(id = 42, threadId = 9)
        message(id = 42, threadId = 4, isMms = true)

        repository.moveToRecycleBin(42, isMms = false)
        repository.moveToRecycleBin(42, isMms = true)
        repository.restore(42, isMms = true)

        assertEquals(setOf(42L to false), dao.bin.keys)
        assertEquals(listOf(9L, 4L, 4L), refreshed)
        repository.emptyAll()
        assertEquals(listOf(42L to false), deleted)
    }

    @Test
    fun `a failing database is reported, not thrown`() {
        val failing = RecycleBinRepository(
            dao = unsupported(),
            deleteMessage = { _, _ -> },
            refreshPreview = {},
            onError = { errors += it },
        )

        failing.moveToRecycleBin(1, isMms = false)
        failing.restore(1, isMms = false)

        assertEquals(2, errors.size)
    }

    private companion object {
        inline fun <reified T> unsupported(): T = Proxy.newProxyInstance(
            T::class.java.classLoader, arrayOf(T::class.java)
        ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
    }
}

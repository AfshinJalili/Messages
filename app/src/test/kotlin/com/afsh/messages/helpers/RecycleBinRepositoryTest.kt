package com.afsh.messages.helpers

import com.afsh.messages.interfaces.MessagesDao
import com.afsh.messages.models.Message
import com.afsh.messages.models.RecycleBinMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class RecycleBinRepositoryTest {

    /** Room's recycle-bin tables in memory; any DAO call the repository should not make throws. */
    private class FakeMessagesDao : MessagesDao by unsupported() {
        val messages = LinkedHashMap<Long, Message>()
        val bin = LinkedHashMap<Long, Long>()

        override fun getThreadIdForMessage(id: Long): Long? = messages[id]?.threadId
        override fun insertRecycleBinEntry(recycleBinMessage: RecycleBinMessage) {
            bin[recycleBinMessage.id] = recycleBinMessage.deletedTS
        }
        override fun deleteFromRecycleBin(id: Long) {
            bin.remove(id)
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
    ).also { dao.messages[id] = it }

    @Test
    fun `moving a message stamps the deletion time and refreshes its conversation`() {
        message(id = 5, threadId = 9)

        repository.moveToRecycleBin(5)

        assertEquals(mapOf(5L to now), dao.bin)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `restoring a message takes it out of the bin and refreshes its conversation`() {
        message(id = 5, threadId = 9)
        repository.moveToRecycleBin(5)
        refreshed.clear()

        repository.restore(5)

        assertEquals(emptyMap<Long, Long>(), dao.bin)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `a message whose thread is unknown is still binned without a refresh`() {
        repository.moveToRecycleBin(77)

        assertEquals(mapOf(77L to now), dao.bin)
        assertEquals(emptyList<Long>(), refreshed)
    }

    @Test
    fun `restoring a conversation restores only that conversation`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 9)
        message(id = 3, threadId = 4)
        listOf(1L, 2L, 3L).forEach(repository::moveToRecycleBin)
        refreshed.clear()

        repository.restoreConversation(9)

        assertEquals(setOf(3L), dao.bin.keys)
        assertEquals(listOf(9L), refreshed)
    }

    @Test
    fun `emptying a conversation erases only its binned messages`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 9, isMms = true)
        message(id = 3, threadId = 9)
        message(id = 4, threadId = 4)
        listOf(1L, 2L, 4L).forEach(repository::moveToRecycleBin)

        repository.emptyConversation(9)

        assertEquals(listOf(1L to false, 2L to true), deleted)
    }

    @Test
    fun `emptying the bin erases every binned message`() {
        message(id = 1, threadId = 9)
        message(id = 2, threadId = 4, isMms = true)
        message(id = 3, threadId = 4)
        listOf(1L, 2L).forEach(repository::moveToRecycleBin)

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
        repository.moveToRecycleBin(1)
        now = 1L
        repository.moveToRecycleBin(2)
        repository.moveToRecycleBin(3)
        now = thirtyDays + 1L

        repository.purgeExpired()

        assertEquals(listOf(1L to false), deleted)
    }

    @Test
    fun `a failing database is reported, not thrown`() {
        val failing = RecycleBinRepository(
            dao = unsupported(),
            deleteMessage = { _, _ -> },
            refreshPreview = {},
            onError = { errors += it },
        )

        failing.moveToRecycleBin(1)
        failing.restore(1)

        assertEquals(2, errors.size)
    }

    private companion object {
        inline fun <reified T> unsupported(): T = Proxy.newProxyInstance(
            T::class.java.classLoader, arrayOf(T::class.java)
        ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
    }
}

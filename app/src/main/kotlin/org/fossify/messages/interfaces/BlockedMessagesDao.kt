package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.BlockedMessage

@Dao
interface BlockedMessagesDao {
    @Query("SELECT * FROM blocked_messages ORDER BY date DESC")
    fun getAll(): List<BlockedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(blockedMessage: BlockedMessage): Long

    @Query("DELETE FROM blocked_messages WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM blocked_messages")
    fun deleteAll()
}

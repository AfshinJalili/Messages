package com.afsh.messages.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.afsh.messages.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}

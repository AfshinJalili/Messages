package com.afsh.messages.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "message_attachments")
data class MessageAttachment(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "text") var text: String,
    @ColumnInfo(name = "attachments") var attachments: ArrayList<Attachment>
)

package com.afsh.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity

/** A message in the recycle bin. SMS and MMS ids overlap, so an entry names its message by id and transport. */
@Entity(tableName = "recycle_bin_messages", primaryKeys = ["id", "is_mms"])
data class RecycleBinMessage(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "is_mms") val isMMS: Boolean,
    @ColumnInfo(name = "deleted_ts") var deletedTS: Long
)

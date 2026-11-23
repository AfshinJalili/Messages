package com.afsh.messages

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.databases.MessagesDatabase
import org.json.JSONObject

/**
 * A throwaway database file built from the exported Room schema of [version], seeded by [seed],
 * then opened through [migrations] (field names on [MessagesDatabase]). Opening validates the
 * result against the current entities. The file is deleted afterwards.
 */
fun <T> migratedDatabase(
    name: String,
    version: Int,
    migrations: List<String>,
    seed: (SQLiteDatabase) -> Unit,
    block: (MessagesDatabase) -> T,
): T {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    context.deleteDatabase(name)
    return try {
        val schema = instrumentation.context.assets.open("com.afsh.messages.databases.MessagesDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            seed(db)
            db.version = version
        }
        val steps = migrations.map {
            MessagesDatabase::class.java.getDeclaredField(it).apply { isAccessible = true }.get(null) as Migration
        }
        val room = Room.databaseBuilder(context, MessagesDatabase::class.java, name).addMigrations(*steps.toTypedArray()).build()
        try {
            room.openHelper.writableDatabase
            block(room)
        } finally {
            room.close()
        }
    } finally {
        context.deleteDatabase(name)
    }
}

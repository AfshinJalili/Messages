package com.afsh.messages.extensions

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.afsh.messages.helpers.FILE_SIZE_NONE
import java.io.FileNotFoundException

/**
 * The size of the file at [uri] in bytes, or [FILE_SIZE_NONE] when neither its descriptor nor its
 * `OpenableColumns.SIZE` row says.
 */
fun ContentResolver.fileSize(uri: Uri): Long {
    val assetFileDescriptor = try {
        openAssetFileDescriptor(uri, "r")
    } catch (_: FileNotFoundException) {
        null
    }

    // uses ParcelFileDescriptor#getStatSize underneath if failed
    val length = assetFileDescriptor?.use { it.length } ?: FILE_SIZE_NONE
    if (length != -1L) {
        return length
    }

    // if "content://" uri scheme, try contentResolver table
    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
        return query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // maybe shouldn't trust ContentResolver for size:
                // https://stackoverflow.com/questions/48302972/content-resolver-returns-wrong-size
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1) {
                    return@use FILE_SIZE_NONE
                }
                cursor.moveToFirst()
                return try {
                    cursor.getLong(sizeIndex)
                } catch (_: Throwable) {
                    FILE_SIZE_NONE
                }
            } ?: FILE_SIZE_NONE
    } else {
        return FILE_SIZE_NONE
    }
}

/**
 * Runs [callback] on each row of the query. Unlike `queryCursor`, a failed query throws: a missing
 * cursor or a provider error must not look like an empty table.
 */
internal fun ContentResolver.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit,
) {
    val cursor = query(uri, projection, selection, selectionArgs, sortOrder)
        ?: throw IllegalStateException("Conversation provider returned no cursor")
    cursor.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

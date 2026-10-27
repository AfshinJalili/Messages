package org.fossify.messages.helpers

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.fossify.commons.helpers.isUpsideDownCakePlus
import org.fossify.messages.models.BackupType
import org.fossify.messages.models.ImportResult
import org.fossify.messages.models.MessagesBackup
import org.fossify.messages.models.MmsBackup
import org.fossify.messages.models.SmsBackup
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** A backup file, opened but not yet restored (31 · Import / review backup). */
sealed interface BackupFile {
    /** The app's own JSON export. */
    data class Messages(val messages: List<MessagesBackup>) : BackupFile {
        val smsCount get() = messages.count { it.backupType == BackupType.SMS }
        val mmsCount get() = messages.count { it.backupType == BackupType.MMS }

        /** Distinct senders and groups, the closest a backup gets to a conversation count. */
        val conversationCount
            get() = messages.mapTo(HashSet()) { message ->
                when (message) {
                    is SmsBackup -> message.address
                    is MmsBackup -> message.addresses.map { it.address }.sorted().joinToString(",")
                }
            }.size
    }

    /** An SMS Backup & Restore XML file: SMS only, read while restoring. */
    data object SmsXml : BackupFile
}

/** Why a file could not be opened as a backup. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Opens and restores message backups. Call from a background thread. */
class MessagesImporter(private val context: Context) {
    private val writer = MessagesWriter(context)

    /** @throws InvalidBackupException when the file is neither the app's JSON nor SMS XML, or is empty. */
    fun open(uri: Uri): BackupFile {
        val type = context.contentResolver.getType(uri).orEmpty()
        val xml = isXmlMimeType(type) || (uri.path?.endsWith("txt") == true && startsWithXml(uri))
        return if (xml) BackupFile.SmsXml else BackupFile.Messages(readJson(uri))
    }

    /** Restores the chosen kinds; existing messages stay as they are. */
    fun restore(backup: BackupFile.Messages, sms: Boolean, mms: Boolean): ImportResult {
        val tally = Tally()
        backup.messages.forEach { message ->
            tally.attempt {
                when {
                    message is SmsBackup && sms -> writer.writeSmsMessage(message)
                    message is MmsBackup && mms -> writer.writeMmsMessage(message)
                    else -> return@attempt false
                }
                true
            }
        }
        writer.fixConversationDates()
        refreshConversations()
        return tally.result()
    }

    fun restoreSmsXml(uri: Uri): ImportResult {
        val tally = Tally()
        val input = context.contentResolver.openInputStream(uri) ?: throw InvalidBackupException("Cannot open $uri")
        input.use { it.readSmsXml(tally) }
        refreshConversations()
        return tally.result()
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun readJson(uri: Uri): List<MessagesBackup> {
        val messages = try {
            context.contentResolver.openInputStream(uri)!!.buffered().use { Json.decodeFromStream<List<MessagesBackup>>(it) }
        } catch (e: SerializationException) {
            throw InvalidBackupException("Not a message backup", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a message backup", e)
        }
        if (messages.isEmpty()) throw InvalidBackupException("The backup is empty")
        return if (isUpsideDownCakePlus()) {
            // Android 14 drops messages whose subscriptionId this device does not have (#191).
            messages.map {
                when (it) {
                    is SmsBackup -> it.copy(subscriptionId = -1)
                    is MmsBackup -> it.copy(subscriptionId = -1)
                }
            }
        } else {
            messages
        }
    }

    private fun InputStream.readSmsXml(tally: Tally) {
        bufferedReader().use { reader ->
            val parser = Xml.newPullParser().apply { setInput(reader) }
            try {
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "smses")
            } catch (e: Exception) {
                throw InvalidBackupException("Not an SMS backup", e)
            }
            var depth = 1
            while (depth != 0) {
                when (parser.next()) {
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.START_TAG -> depth++
                }
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name == "sms") {
                    tally.attempt {
                        writer.writeSmsMessage(parser.readSms())
                        true
                    }
                } else {
                    parser.skip()
                }
            }
        }
    }

    private fun XmlPullParser.readSms(): SmsBackup {
        require(XmlPullParser.START_TAG, null, "sms")
        return SmsBackup(
            subscriptionId = 0,
            address = getAttributeValue(null, "address"),
            body = getAttributeValue(null, "body"),
            date = getAttributeValue(null, "date").toLong(),
            dateSent = getAttributeValue(null, "date").toLong(),
            locked = getAttributeValue(null, "locked").toInt(),
            protocol = getAttributeValue(null, "protocol"),
            read = getAttributeValue(null, "read").toInt(),
            status = getAttributeValue(null, "status").toInt(),
            type = getAttributeValue(null, "type").toInt(),
            serviceCenter = getAttributeValue(null, "service_center"),
        )
    }

    private fun XmlPullParser.skip() {
        check(eventType == XmlPullParser.START_TAG)
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun startsWithXml(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLine()?.startsWith("<?xml") == true }
    }.getOrNull() == true

    private fun isXmlMimeType(mimeType: String) =
        mimeType.equals("application/xml", ignoreCase = true) || mimeType.equals("text/xml", ignoreCase = true)

    /** Counts what was written and what failed, and turns that into an [ImportResult]. */
    private class Tally {
        private var imported = 0
        private var failed = 0

        /** [write] returns false for a message it chose to skip. */
        fun attempt(write: () -> Boolean) {
            try {
                if (write()) imported++
            } catch (_: Exception) {
                failed++
            }
        }

        fun result() = when {
            imported == 0 && failed == 0 -> ImportResult.IMPORT_NOTHING_NEW
            failed > 0 && imported > 0 -> ImportResult.IMPORT_PARTIAL
            failed > 0 -> ImportResult.IMPORT_FAIL
            else -> ImportResult.IMPORT_OK
        }
    }
}

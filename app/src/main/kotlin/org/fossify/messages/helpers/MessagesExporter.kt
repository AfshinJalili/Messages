package org.fossify.messages.helpers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

enum class ExportOutcome { EXPORTED, NOTHING_TO_EXPORT, FAILED }

/** Writes the chosen kinds of messages to a JSON backup (30 · Export / options). Call off the main thread. */
class MessagesExporter(private val context: Context) {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun export(uri: Uri, sms: Boolean, mms: Boolean): ExportOutcome {
        var outcome = ExportOutcome.FAILED
        try {
            MessagesReader(context).getMessagesToExport(getSms = sms, getMms = mms) { messages ->
                if (messages.isEmpty()) {
                    outcome = ExportOutcome.NOTHING_TO_EXPORT
                    return@getMessagesToExport
                }
                context.contentResolver.openOutputStream(uri)!!.buffered().use {
                    Json { encodeDefaults = true }.encodeToStream(messages, it)
                }
                outcome = ExportOutcome.EXPORTED
            }
        } catch (_: Throwable) { // OutOfMemoryError included: a huge history must not crash the app.
            outcome = ExportOutcome.FAILED
        }
        if (outcome != ExportOutcome.EXPORTED) {
            // An empty or partial file is worse than none.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
        return outcome
    }
}

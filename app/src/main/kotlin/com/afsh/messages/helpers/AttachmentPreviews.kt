package com.afsh.messages.helpers

import com.afsh.messages.extensions.*
import com.afsh.messages.ui.components.AfshIcon

/** The icon a file attachment shows for its type. */
internal fun attachmentIcon(mimeType: String) = when {
    mimeType.isAudioMimeType() -> AfshIcon.AudioFile
    mimeType.isCalendarMimeType() -> AfshIcon.Calendar
    mimeType.isPdfMimeType() -> AfshIcon.Pdf
    mimeType.isZipMimeType() -> AfshIcon.ZipFile
    else -> AfshIcon.Document
}

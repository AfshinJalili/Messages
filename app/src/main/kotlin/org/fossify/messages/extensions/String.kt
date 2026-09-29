package org.fossify.messages.extensions

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}

private val OTP_CODE = Regex(
    """(?<![\p{L}\p{N}_])(?:otp|code|pin|passcode|password|verification|verify|auth|token|رمز|کد|تایید|تأیید)(?![\p{L}\p{N}_])""" +
        """[\s:=\-‌]*(?:(?:is|your|code|تایید|تأیید|شما|عبارت|است|از|یکبار|مصرف)[\s:=\-‌]+)*""" +
        """(\p{Nd}{4,8})(?![\p{L}\p{N}_*]|[.,/：:\-٬٫]\s*\p{N}|\s+(?:or|یا)\s+\p{Nd})""",
    RegexOption.IGNORE_CASE
)

/**
 * ponytail: only explicit label-before-code phrases; add reported phrasing here rather than
 * guessing from unrelated numbers. Multiple candidates omit the automatic Copy action.
 */
fun String.extractOtpCode(): String? = OTP_CODE.findAll(this).singleOrNull()?.groupValues?.get(1)

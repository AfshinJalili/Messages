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

private val OTP_KEYWORDS = Regex(
    "otp|code|pin|passcode|password|verification|verify|auth|token|رمز|کد|تایید",
    RegexOption.IGNORE_CASE
)
private val OTP_CODE = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

/**
 * ponytail: keyword-gated so "see you at 2030" never grows a Copy chip. Widen [OTP_KEYWORDS] if
 * users report misses; a missing chip is cheap, a wrong one trains people to ignore it.
 */
fun String.extractOtpCode(): String? {
    if (!OTP_KEYWORDS.containsMatchIn(this)) {
        return null
    }
    return OTP_CODE.find(this)?.groupValues?.get(1)
}

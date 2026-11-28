package com.afsh.messages.messaging

import android.content.Context
import android.telephony.SmsMessage
import android.util.Patterns
import android.widget.Toast.LENGTH_LONG
import com.klinker.android.send_message.Settings
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getThreadId
import com.afsh.messages.extensions.messagingUtils
import com.afsh.messages.extensions.shortcutHelper
import com.afsh.messages.messaging.SmsException.Companion.EMPTY_DESTINATION_ADDRESS
import com.afsh.messages.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import com.afsh.messages.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.Message

@Deprecated("TODO: Move/rewrite messaging config code into the app.")
fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.sendLongAsMms = config.sendLongMessageMMS
    settings.sendLongAsMmsAfter = 1
    settings.group = config.sendGroupMessageMMS
    return settings
}

fun Context.isLongMmsMessage(text: String, settings: Settings = getSendMessageSettings()): Boolean {
    val data = SmsMessage.calculateLength(text, false)
    val numPages = data.first()
    return numPages > settings.sendLongAsMmsAfter && settings.sendLongAsMms
}

/**
 * Sends the message using the in-app SmsManager API wrappers if it's an SMS or using android-smsmms for MMS.
 * A failure shows as a toast (see [sendErrorText]).
 */
fun Context.sendMessageCompat(
    text: String,
    addresses: List<String>,
    subId: Int?,
    attachments: List<Attachment>,
    messageToResend: Message? = null,
    forceMms: Boolean = false,
) {
    try {
        sendMessage(text, addresses, subId, attachments, messageToResend, forceMms)
    } catch (e: Exception) {
        sendErrorText(e)?.let { toast(it, LENGTH_LONG) }
    }
    ensureBackgroundThread {
        val threadId = getThreadId(addresses.toSet())
        shortcutHelper.reportSendMessageUsage(threadId)
    }
}

/**
 * Hands the message to the SMS transport, or to android-smsmms for MMS. Throws when the SMS could not be
 * stored or sent, with a [SmsException] for the transport's own failures.
 */
fun Context.sendMessage(
    text: String,
    addresses: List<String>,
    subId: Int?,
    attachments: List<Attachment>,
    messageToResend: Message? = null,
    forceMms: Boolean = false,
) {
    // SMS and MMS ids are separate sequences, so a resend may only reuse the failed row within its own
    // table. Otherwise an SMS retried as MMS would delete whichever unrelated MMS shares its id.
    val smsIdToResend = messageToResend?.takeIf { !it.isMMS }?.id
    val mmsIdToResend = messageToResend?.takeIf { it.isMMS }?.id
    val settings = getSendMessageSettings()
    if (subId != null) {
        settings.subscriptionId = subId
    }

    val messagingUtils = messagingUtils
    val isMms = forceMms || attachments.isNotEmpty() || isLongMmsMessage(text, settings)
            || addresses.size > 1 && settings.group
    if (isMms) {
        // we send all MMS attachments separately to reduces the chances of hitting provider MMS limit.
        if (attachments.isNotEmpty()) {
            val lastIndex = attachments.lastIndex
            if (attachments.size > 1) {
                for (i in 0 until lastIndex) {
                    val attachment = attachments[i]
                    messagingUtils.sendMmsMessage("", addresses, attachment, settings, mmsIdToResend)
                }
            }

            val lastAttachment = attachments[lastIndex]
            messagingUtils.sendMmsMessage(text, addresses, lastAttachment, settings, mmsIdToResend)
        } else {
            messagingUtils.sendMmsMessage(text, addresses, null, settings, mmsIdToResend)
        }
    } else {
        messagingUtils.sendSmsMessage(
            text = text,
            addresses = addresses.toSet(),
            subId = settings.subscriptionId,
            requireDeliveryReport = settings.deliveryReports,
            messageId = smsIdToResend
        )
    }
}

/** What to tell the person when a send threw [error]; null when there is nothing to tell. */
fun Context.sendErrorText(error: Exception): String? = when {
    error !is SmsException -> String.format(getString(com.afsh.commons.R.string.error), error.toString())
    error.errorCode == EMPTY_DESTINATION_ADDRESS -> getString(R.string.empty_destination_address)
    error.errorCode == ERROR_PERSISTING_MESSAGE -> getString(R.string.unable_to_save_message)
    error.errorCode == ERROR_SENDING_MESSAGE -> getString(R.string.unknown_error_occurred_sending_message, error.errorCode)
    else -> null
}

/**
 * Check if a given "address" is a short code.
 * There's not much info available on these special numbers, even the wikipedia page (https://en.wikipedia.org/wiki/Short_code)
 * contains outdated information regarding max number of digits. The exact parameters for short codes can vary by country and by carrier.
 */
fun isShortCodeWithLetters(address: String): Boolean {
    if (Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
        // An email address is not a short code.
        return false
    }

    return address.any { it.isLetter() }
}

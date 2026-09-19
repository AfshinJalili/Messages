package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.ReceiverUtils.isMessageFilteredOut

/**
 * Classifies inbound SMS/MMS after Telephony persistence. A non-null reason means the message
 * should be silenced (no notification) and indexed in the local Spam folder.
 */
object IncomingSpamClassifier {

    fun silenceReason(context: Context, address: String, body: String): Int? {
        if (isMessageFilteredOut(context, body)) {
            return BLOCK_REASON_KEYWORD
        }
        if (context.isNumberBlocked(address)) {
            return BLOCK_REASON_NUMBER
        }

        val isKnownContact = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use { cursor ->
            SimpleContactsHelper(context).existsSync(address, cursor) != ContactLookupResult.NotFound
        }
        if (isKnownContact || context.config.allowedNumbers.contains(address)) {
            return null
        }

        if (context.baseConfig.blockUnknownNumbers) {
            return BLOCK_REASON_NUMBER
        }

        return when (RuleBasedFilter.evaluate(context, address, body)) {
            FilterVerdict.ALLOW -> null
            FilterVerdict.BLOCK -> BLOCK_REASON_RULE
            FilterVerdict.NO_VERDICT -> null
        }
    }
}

package com.afsh.messages.helpers

import android.content.Context
import android.util.Log
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.getMyContactsCursor
import com.afsh.commons.extensions.isNumberBlocked
import com.afsh.commons.helpers.ContactLookupResult
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.messages.extensions.config

/**
 * Classifies inbound SMS/MMS after Telephony persistence. A non-null reason means the message
 * should be silenced (no notification) and indexed in the local Spam folder.
 */
object IncomingSpamClassifier {
    private const val TAG = "IncomingSpamClassifier"

    /** A failure while classifying gives null: an error never hides a stored message. */
    fun silenceReason(context: Context, address: String, body: String): Int? = try {
        reason(context, address, body)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't classify an incoming message; it is not silenced", e)
        null
    }

    private fun reason(context: Context, address: String, body: String): Int? {
        // The user's own decision about a sender beats every rule below.
        if (context.config.allowedNumbers.containsNumber(address)) {
            return null
        }
        if (context.config.spamNumbers.containsNumber(address)) {
            return BLOCK_REASON_NUMBER
        }
        if (context.isNumberBlocked(address)) {
            return BLOCK_REASON_NUMBER
        }
        // A one-time code is never caught by a blocked phrase, an unknown sender or the rules:
        // missing a code costs more than letting one through. Only a blocked sender stops it.
        if (isOneTimeCode(context, address, body)) {
            return null
        }
        if (keywordMatches(context.config.blockedKeywords, body).isNotEmpty()) {
            return BLOCK_REASON_KEYWORD
        }

        val isKnownContact = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use { cursor ->
            SimpleContactsHelper(context).existsSync(address, cursor) != ContactLookupResult.NotFound
        }
        if (isKnownContact) {
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

    /** Uses the patterns even when the rule filter is off: that switch only turns blocking on. */
    fun isOneTimeCode(context: Context, address: String, body: String): Boolean =
        RuleBasedFilter.explain(context.config.spamPatterns, address, body).rule == FilterRule.ONE_TIME_CODE
}

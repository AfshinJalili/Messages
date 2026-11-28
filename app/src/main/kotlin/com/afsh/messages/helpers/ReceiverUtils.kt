package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            // A blank phrase is in every message.
            if (blockedKeyword.isNotBlank() && body.contains(blockedKeyword, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}

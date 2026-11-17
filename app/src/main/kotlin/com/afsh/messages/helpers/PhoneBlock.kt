package com.afsh.messages.helpers

import android.content.Context
import android.provider.BlockedNumberContract
import com.afsh.messages.R
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.toast

/**
 * Writes numbers to Android's [BlockedNumberContract] block list ("Block on this phone").
 * App-level always-spam ([Config.addSpamNumber]) is separate: spam still saves messages;
 * phone block drops them before they arrive.
 *
 * Always gives visible feedback on failure: toast when [BlockedNumberContract.canCurrentUserBlockNumbers]
 * is false; [addBlockedNumber] already toasts on [SecurityException] and other insert failures.
 */
fun Context.blockNumbersOnPhone(numbers: Collection<String>): Boolean {
    val toBlock = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (toBlock.isEmpty()) return true
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(this)) {
        toast(R.string.block_on_phone_unavailable)
        return false
    }
    return toBlock.map(::addBlockedNumber).all { it }
}

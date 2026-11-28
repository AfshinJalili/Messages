package com.afsh.messages.helpers

import android.content.Context
import android.provider.BlockedNumberContract
import com.afsh.messages.R
import com.afsh.commons.extensions.addBlockedNumber
import com.afsh.commons.extensions.getBlockedNumbers
import com.afsh.commons.extensions.isNumberBlocked
import com.afsh.commons.extensions.toast

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

/** Takes [numbers] off Android's block list, whichever stored form matches. False when the phone refuses. */
fun Context.unblockNumbersOnPhone(numbers: Collection<String>): Boolean {
    val toUnblock = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (toUnblock.isEmpty()) return true
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(this)) {
        toast(R.string.block_on_phone_unavailable)
        return false
    }
    return try {
        toUnblock.forEach { BlockedNumberContract.unblock(this, it) }
        true
    } catch (e: SecurityException) {
        toast(R.string.block_on_phone_unavailable)
        false
    }
}

/** Blocking. True when Android blocks every one of [numbers]; false for none, or when the list cannot be read. */
fun Context.isBlockedOnPhone(numbers: Collection<String>): Boolean {
    if (numbers.isEmpty()) return false
    val blocked = try {
        getBlockedNumbers()
    } catch (e: SecurityException) {
        return false
    }
    return numbers.all { isNumberBlocked(it, blocked) }
}

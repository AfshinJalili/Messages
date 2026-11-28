package com.afsh.messages.activities

import android.app.Activity
import android.content.Intent
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.helpers.SenderFilter
import com.afsh.messages.helpers.blockNumbersOnPhone
import com.afsh.messages.helpers.senderStanding
import com.afsh.messages.helpers.setSenderFilter
import com.afsh.messages.helpers.unblockNumbersOnPhone
import com.afsh.messages.ui.components.confirm
import com.afsh.messages.ui.components.showAfshDialog
import com.afsh.messages.ui.isolate
import com.afsh.messages.ui.spam.SpamAndBlockingSheet

/**
 * Who the Spam and blocking sheet acts on. [threadIds] are their conversations, whose spam Never spam brings
 * back. [name] is the sender's name, or null for several. [canBlock] is false when a group is among them.
 */
data class SpamSenders(val numbers: List<String>, val threadIds: List<Long>, val name: String?, val canBlock: Boolean)

/**
 * Opens the Spam and blocking sheet for [senders] and applies the choice at once, with Undo: no confirmation,
 * except before Android blocks a number, which reaches past this app. [offerUndo] shows the host's Undo;
 * [onChanged] runs on the main thread after every change and every undo.
 */
fun Activity.openSpamAndBlocking(
    senders: SpamSenders,
    offerUndo: (message: String, undo: () -> Unit) -> Unit,
    onChanged: () -> Unit,
) {
    val numbers = senders.numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (numbers.isEmpty()) return
    val who = senders.name?.takeIf { numbers.size == 1 }
        ?: resources.getQuantityString(R.plurals.sender_count, numbers.size, numbers.size)
    ensureBackgroundThread {
        val standing = senderStanding(numbers)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            showAfshDialog { dismiss ->
                SpamAndBlockingSheet(
                    who = who,
                    standing = standing,
                    canBlock = senders.canBlock,
                    onFilter = { filter ->
                        if (filter != standing.filter) setFilter(numbers, senders.threadIds, filter, offerUndo, onChanged)
                    },
                    onBlock = { blocked ->
                        if (blocked) {
                            confirmBlock(numbers, senders.name, offerUndo, onChanged)
                        } else {
                            unblock(numbers, offerUndo, onChanged)
                        }
                    },
                    onOpenRules = { startActivity(Intent(this, BlockRulesActivity::class.java)) },
                    onDismiss = dismiss,
                )
            }
        }
    }
}

/** Unblock from the blocked conversation's composer: the switch in the sheet, without the sheet. */
fun Activity.unblockOnPhone(numbers: List<String>, offerUndo: (message: String, undo: () -> Unit) -> Unit, onChanged: () -> Unit) =
    unblock(numbers, offerUndo, onChanged)

private fun Activity.setFilter(
    numbers: List<String>,
    threadIds: List<Long>,
    filter: SenderFilter,
    offerUndo: (String, () -> Unit) -> Unit,
    onChanged: () -> Unit,
) = ensureBackgroundThread {
    val undo = setSenderFilter(numbers, threadIds, filter)
    runOnUiThread {
        onChanged()
        val message = getString(
            when (filter) {
                SenderFilter.AUTOMATIC -> R.string.sender_filter_set_automatic
                SenderFilter.ALWAYS_SPAM -> R.string.sender_filter_set_always
                SenderFilter.NEVER_SPAM -> R.string.sender_filter_set_never
            },
        )
        offerUndo(message) {
            ensureBackgroundThread {
                undo()
                runOnUiThread(onChanged)
            }
        }
    }
}

private fun Activity.confirmBlock(numbers: List<String>, name: String?, offerUndo: (String, () -> Unit) -> Unit, onChanged: () -> Unit) {
    val count = numbers.size
    confirm(
        title = if (count == 1 && name != null) {
            resources.getQuantityString(R.plurals.block_on_phone_title, 1, isolate(name))
        } else {
            resources.getQuantityString(R.plurals.block_on_phone_title, count, count)
        },
        explanation = resources.getQuantityString(R.plurals.block_on_phone_text, count),
        confirmLabel = resources.getQuantityString(R.plurals.block_on_phone, count),
        destructive = true,
    ) {
        if (blockNumbersOnPhone(numbers)) {
            onChanged()
            offerUndo(getString(R.string.sender_blocked_on_phone)) {
                if (unblockNumbersOnPhone(numbers)) onChanged()
            }
        }
    }
}

private fun Activity.unblock(numbers: List<String>, offerUndo: (String, () -> Unit) -> Unit, onChanged: () -> Unit) {
    if (!unblockNumbersOnPhone(numbers)) return
    onChanged()
    offerUndo(getString(R.string.sender_unblocked_on_phone)) {
        if (blockNumbersOnPhone(numbers)) onChanged()
    }
}

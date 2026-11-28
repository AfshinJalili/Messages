package com.afsh.messages.activities

import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getThreadTitle
import com.afsh.messages.helpers.SWIPE_UNDO_DURATION_MS
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.models.Message

/**
 * Spam in the conversation. Messages: Not spam, at once with Undo. The sender: the Spam and blocking sheet, and
 * Unblock on the blocked composer.
 */
internal class ThreadSpamActions(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val people get() = visit.people

    fun openSheet() {
        val participants = people.participants
        activity.openSpamAndBlocking(
            SpamSenders(
                numbers = people.addresses,
                threadIds = listOf(visit.threadId),
                name = visit.conversation?.title?.takeIf { it.isNotEmpty() } ?: participants.getThreadTitle(),
                canBlock = participants.size == 1,
            ),
            offerUndo = ::offerUndo,
            onChanged = ::senderChanged,
        )
    }

    fun unblock() = activity.unblockOnPhone(people.addresses, ::offerUndo, ::senderChanged)

    /**
     * [selected] go back to the conversation. A sender on the always-spam list leaves it, or their next message
     * would land in Spam again. Undo puts back both.
     */
    fun notSpam(selected: List<Message>) {
        ensureBackgroundThread {
            val config = activity.config
            val unlisted = selected.map { it.senderPhoneNumber }
                .filter { it.isNotEmpty() && config.spamNumbers.containsNumber(it) }
                .distinct()
            unlisted.forEach(config::removeSpamNumber)
            val forgotten = visit.repository.forgetSpam(selected)
            refreshTimeline()
            activity.runOnUiThread {
                offerUndo(activity.resources.getQuantityString(R.plurals.message_restored, selected.size, selected.size)) {
                    ensureBackgroundThread {
                        unlisted.forEach(config::addSpamNumber)
                        visit.repository.restoreSpam(forgotten)
                        refreshTimeline()
                    }
                }
            }
        }
    }

    /** Background. The spam markers changed: the timeline and the header follow. */
    private fun refreshTimeline() {
        val items = visit.repository.items()
        visit.threadItems = items
        activity.runOnUiThread {
            visit.showFacts()
            visit.publish(items)
        }
    }

    /** The filter or the block changed: the menu, the composer and Never spam's restored messages follow. */
    private fun senderChanged() {
        ensureBackgroundThread {
            visit.readBlockedOnPhone()
            visit.repository.loadSpamReasons()
            refreshTimeline()
        }
    }

    private fun offerUndo(message: String, undo: () -> Unit) {
        visit.deletion.showUndo(message, SWIPE_UNDO_DURATION_MS, undo)
    }
}

package com.afsh.messages.activities

import android.text.TextUtils
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.blockNumbersOnPhone
import com.afsh.messages.helpers.containsNumber
import com.afsh.messages.models.Message
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.AfshSheet
import com.afsh.messages.ui.components.AfshSheetItem
import com.afsh.messages.ui.components.confirm
import com.afsh.messages.ui.components.showAfshDialog

/** Spam in the conversation: not spam, always or never spam for the sender, and blocking on the phone. */
internal class ThreadSpamActions(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val resources get() = activity.resources
    private val people get() = visit.people

    /** The names the confirmations show. */
    private fun names(numbers: List<String>) = visit.conversation?.title ?: TextUtils.join(", ", numbers)

    fun askNotSpam(selected: List<Message>) {
        activity.showAfshDialog { dismiss ->
            AfshSheet(title = activity.getString(R.string.not_spam_sheet_title), onDismiss = dismiss) {
                AfshSheetItem(
                    label = activity.getString(R.string.not_spam_only),
                    icon = AfshIcon.Inbox,
                    supporting = activity.getString(R.string.not_spam_only_text),
                    onClick = {
                        dismissThen {
                            dismiss()
                            unmarkSpam(selected, allowSender = false)
                        }
                    },
                )
                AfshSheetItem(
                    label = resources.getQuantityString(R.plurals.never_spam, 1),
                    icon = AfshIcon.Verified,
                    supporting = resources.getQuantityString(R.plurals.never_spam_text, 1),
                    onClick = {
                        dismissThen {
                            dismiss()
                            unmarkSpam(selected, allowSender = true)
                        }
                    },
                )
            }
        }
    }

    private fun unmarkSpam(selected: List<Message>, allowSender: Boolean) {
        ensureBackgroundThread {
            val config = activity.config
            if (allowSender) {
                (selected.map { it.senderPhoneNumber } + people.addresses)
                    .filter { it.isNotEmpty() }.distinct().forEach { config.addAllowedNumber(it) }
            } else {
                selected.map { it.senderPhoneNumber }.filter { it.isNotEmpty() && config.spamNumbers.containsNumber(it) }
                    .distinct().forEach { config.removeSpamNumber(it) }
            }
            visit.repository.forgetSpam(selected)
            val items = visit.repository.items()
            visit.threadItems = items
            activity.runOnUiThread {
                visit.showFacts()
                visit.publish(items)
                if (allowSender) {
                    activity.toast(R.string.sender_allowed)
                } else {
                    activity.toast(resources.getQuantityString(R.plurals.message_restored, selected.size))
                }
            }
        }
    }

    fun alwaysSpamSender() {
        val numbers = people.addresses
        activity.confirm(
            title = resources.getQuantityString(R.plurals.always_spam_title, 1, names(numbers)),
            explanation = resources.getQuantityString(R.plurals.always_spam_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.always_spam, 1),
        ) {
            ensureBackgroundThread {
                numbers.forEach { activity.config.addSpamNumber(it) }
                activity.runOnUiThread { visit.showFacts() }
            }
        }
    }

    fun neverSpamSender() {
        val numbers = people.addresses
        activity.confirm(
            title = resources.getQuantityString(R.plurals.never_spam_title, 1, names(numbers)),
            explanation = resources.getQuantityString(R.plurals.never_spam_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.never_spam, 1),
        ) {
            val data = visit.data
            val spam = data.list.filter { data.isSpam(it) }
            if (spam.isNotEmpty()) {
                unmarkSpam(spam, allowSender = true)
            } else {
                ensureBackgroundThread {
                    numbers.forEach { activity.config.addAllowedNumber(it) }
                    activity.runOnUiThread { visit.showFacts() }
                }
            }
        }
    }

    fun blockOnThisPhone() {
        val numbers = people.addresses
        activity.confirm(
            title = resources.getQuantityString(R.plurals.block_on_phone_title, 1, names(numbers)),
            explanation = resources.getQuantityString(R.plurals.block_on_phone_text, 1),
            confirmLabel = resources.getQuantityString(R.plurals.block_on_phone, 1),
            destructive = true,
        ) {
            if (activity.blockNumbersOnPhone(numbers)) visit.showFacts()
        }
    }
}

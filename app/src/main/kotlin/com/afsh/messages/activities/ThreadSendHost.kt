package com.afsh.messages.activities

import com.afsh.commons.extensions.openRequestExactAlarmSettings
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.toast
import com.afsh.messages.BuildConfig
import com.afsh.messages.R
import com.afsh.messages.helpers.MessageSendCoordinator
import com.afsh.messages.models.Message
import com.afsh.messages.ui.components.confirm

/** What [ThreadVisit.sender] needs from the conversation screen. */
internal class ThreadSendHost(private val visit: ThreadVisit) : MessageSendCoordinator.Host {
    private val activity get() = visit.activity

    override val threadId get() = visit.threadId
    override val conversation get() = visit.conversation
    override fun loaded(): List<Message> = visit.data.list

    override fun moveToThread(threadId: Long) {
        visit.repository.moveTo(threadId)
        visit.loader.syncActiveThread()
    }

    override fun showStored(message: Message) {
        visit.data.upsert(message)
        val newItems = visit.repository.items()
        activity.runOnUiThread {
            visit.threadItems = newItems
            visit.publish(newItems)
            visit.holder.scrollToBottom()
            if (visit.sender.needsRefresh) visit.loader.reload()
        }
    }

    override fun reload() = visit.loader.reload()
    override fun sent() = visit.holder.clearMessage()

    override fun scheduled() = activity.runOnUiThread {
        visit.holder.clearMessage()
        visit.holder.clearSchedule()
    }

    override fun retryFailed(message: Message) = activity.runOnUiThread { visit.holder.retryFailed(message) }

    override fun timeInPast(retryMillis: Long?) {
        activity.toast(R.string.must_pick_time_in_the_future)
        if (retryMillis != null) visit.composer.launchScheduleSendDialog(retryMillis)
    }

    override fun askForExactAlarms() = activity.confirm(
        title = activity.getString(com.afsh.commons.R.string.permission_required),
        explanation = activity.getString(com.afsh.commons.R.string.allow_alarm_scheduled_messages),
        confirmLabel = activity.getString(R.string.open_settings),
    ) {
        activity.openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
    }

    override fun attachmentsSaved() = activity.toast(com.afsh.commons.R.string.file_saved)
    override fun failed(error: Exception) = activity.showErrorToast(error)
    override fun failed(message: String?) =
        activity.showErrorToast(message ?: activity.getString(com.afsh.commons.R.string.unknown_error_occurred))
}

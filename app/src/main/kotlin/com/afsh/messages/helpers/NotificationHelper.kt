package com.afsh.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.Person
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.helpers.SimpleContactsHelper
import com.afsh.messages.R
import com.afsh.messages.activities.ThreadActivity
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.shortcutHelper
import com.afsh.messages.messaging.isShortCodeWithLetters
import com.afsh.messages.extensions.extractOtpCode
import com.afsh.messages.receivers.CopyOtpReceiver
import com.afsh.messages.receivers.DirectReplyReceiver
import com.afsh.messages.receivers.DeleteSmsReceiver
import com.afsh.messages.receivers.MarkAsReadReceiver
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.isolate

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    /**
     * Posts [threadId]'s message notification with [body] added to the messages it already shows, and
     * returns whether it posted. Blocking, and not thread-safe per thread: [MessagingEvents] calls it off
     * the main thread, one post at a time, so each post reads what the last one left. With [continuing]
     * false (the caller cancelled the notification since it last posted one) it starts afresh, since the
     * system may list a cancelled notification a moment longer. [stillAlerts] is asked once the content
     * is ready and before any of its PendingIntents change, since the user may have opened or muted the
     * thread meanwhile.
     */
    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        isMms: Boolean,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String,
        continuing: Boolean,
        stillAlerts: () -> Boolean,
    ): Boolean {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createDefaultChannel()
        }

        val shortcut = context.shortcutHelper.getShortcut(threadId)
            ?: context.shortcutHelper.createOrUpdateShortcut(threadId)

        val notificationId = threadId.hashCode()
        // The thread's message notification as it shows now; the thread's "not sent" one has a tag.
        val shown = if (continuing) {
            notificationManager.activeNotifications.find { it.id == notificationId && it.tag == null }?.notification
        } else {
            null
        }
        // Android's default lock screen shows a notification's own content: only its "hide sensitive
        // content" option shows the public version, and an app cannot ask for that on a channel that
        // already exists. So when the lock-screen setting hides the message, the notification leaves it out.
        val lockScreenVisibility = context.config.lockScreenVisibilitySetting
        val showsMessage = lockScreenShowsMessage(lockScreenVisibility)
        val style = if (showsMessage) getMessagesStyle(address, body, sender, oldMessages = shown?.let(::messagesOf).orEmpty()) else null
        val shownMessages = shownMessagesAfter(
            shown, messageId, isMms,
            count = style?.messages?.size ?: NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES,
        )

        // Before any PendingIntent below is updated: the live notification's actions share them.
        if (!stillAlerts()) return false

        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val deleteSmsIntent = DeleteSmsReceiver.intent(context, threadId, shownMessages.ids, shownMessages.areMms)
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    // Mutable: the system adds the RemoteInput text. The intent names its receiver.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                AfshIcon.Send.resource,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val messageCount = shownMessages.ids.size
        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            if (style != null) {
                setLargeIcon(bitmap ?: SimpleContactsHelper(context).getContactLetterIcon(sender))
                setStyle(style)
                setShortcutInfo(shortcut)
            } else {
                // Not the MMS picture or the contact photo: either can show what the setting hides.
                if (lockScreenShowsSender(lockScreenVisibility)) {
                    setLargeIcon(SimpleContactsHelper(context).getContactLetterIcon(sender))
                }
                setRedactedContent(sender, lockScreenVisibility, messageCount)
            }
            addExtras(shownMessages.toExtras())
            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            setPublicVersion(publicMessageNotification(notificationChannelId, sender, lockScreenVisibility, messageCount))

            color = context.primaryRoleColor()
            setSmallIcon(R.drawable.ic_message_bubble)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        }

        if (replyAction != null) {
            builder.addAction(replyAction)
        }

        body.extractOtpCode()?.let { code ->
            val copyOtpIntent = Intent(context, CopyOtpReceiver::class.java).apply {
                putExtra(OTP_CODE, code)
                putExtra(THREAD_ID, threadId)
            }
            // Actions show on the lock screen too: the code stays out of the label unless the setting shows the message.
            val copyLabel = if (showsMessage) {
                context.getString(R.string.copy_otp_code, code)
            } else {
                context.getString(R.string.copy_otp_code_hidden)
            }
            builder.addAction(
                AfshIcon.Copy.resource,
                copyLabel,
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    copyOtpIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).setChannelId(notificationChannelId)
        }

        builder.addAction(
            AfshIcon.Check.resource,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
            .setChannelId(notificationChannelId)
        if (isNoReplySms) {
            builder.addAction(
                AfshIcon.Delete.resource,
                context.getString(com.afsh.commons.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(notificationChannelId)
        }

        notificationManager.notify(notificationId, builder.build())
        context.shortcutHelper.reportReceiveMessageUsage(threadId)
        return true
    }

    /**
     * One "not sent" notification per thread, beside its message notification. A later failure in the
     * thread replaces it; [MessagingEvents] decides when it alerts and when it goes.
     */
    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createDefaultChannel()
        }

        val notificationId = threadId.hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), isolate(recipientName))
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.primaryRoleColor())
            .setSmallIcon(R.drawable.ic_message_bubble)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(SEND_FAILURE_TAG, notificationId, builder.build())
    }

    fun cancelSendFailureNotification(threadId: Long) {
        notificationManager.cancel(SEND_FAILURE_TAG, threadId.hashCode())
    }

    /**
     * Creates the shared channel, or renames it in the current app language. Android keeps the
     * name it was last given, so call this on start and on a locale change too.
     */
    fun createDefaultChannel() {
        createChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.channel_received_sms))
    }

    private fun createChannel(id: String, name: String) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    /** Where Android hides sensitive lock-screen content, it shows this instead. */
    private fun publicMessageNotification(
        channelId: String,
        sender: String,
        lockScreenVisibility: Int,
        messageCount: Int,
    ): Notification = NotificationCompat.Builder(context, channelId)
        .setRedactedContent(sender, lockScreenVisibility, messageCount)
        .setSmallIcon(R.drawable.ic_message_bubble)
        .setColor(context.primaryRoleColor())
        .setCategory(Notification.CATEGORY_MESSAGE)
        .build()

    /** Who wrote, or only the app, as the lock-screen setting allows; never the message. */
    private fun NotificationCompat.Builder.setRedactedContent(sender: String, lockScreenVisibility: Int, messageCount: Int) = apply {
        setContentTitle(if (lockScreenShowsSender(lockScreenVisibility)) sender else context.getString(R.string.app_launcher_name))
        setContentText(context.getString(if (messageCount > 1) R.string.new_messages else R.string.new_message))
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        name: String?,
        oldMessages: List<NotificationCompat.MessagingStyle.Message>,
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            oldMessages.forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun messagesOf(notification: Notification): List<NotificationCompat.MessagingStyle.Message> =
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages.orEmpty()

    /**
     * The messages [shown] lists plus the new one, oldest first, cut to the [count] that its
     * MessagingStyle keeps: what the notification's Delete action deletes.
     */
    private fun shownMessagesAfter(shown: Notification?, messageId: Long, isMms: Boolean, count: Int): ShownMessages {
        val extras = shown?.extras
        var ids = extras?.getLongArray(SHOWN_MESSAGE_IDS) ?: LongArray(0)
        var areMms = extras?.getBooleanArray(SHOWN_MESSAGES_ARE_MMS) ?: BooleanArray(0)
        if (ids.size != areMms.size) {
            ids = LongArray(0)
            areMms = BooleanArray(0)
        }
        return ShownMessages(
            ids = (ids + messageId).takeLast(count).toLongArray(),
            areMms = (areMms + isMms).takeLast(count).toBooleanArray(),
        )
    }

    /** The messages a conversation notification shows, by id and transport: SMS and MMS ids overlap. */
    private class ShownMessages(val ids: LongArray, val areMms: BooleanArray) {
        fun toExtras() = Bundle().apply {
            putLongArray(SHOWN_MESSAGE_IDS, ids)
            putBooleanArray(SHOWN_MESSAGES_ARE_MMS, areMms)
        }
    }

    private companion object {
        /** Sets a thread's "not sent" notification apart from its message notification, which has the same id. */
        const val SEND_FAILURE_TAG = "send_failure"
        const val SHOWN_MESSAGE_IDS = "com.afsh.messages.shown_message_ids"
        const val SHOWN_MESSAGES_ARE_MMS = "com.afsh.messages.shown_messages_are_mms"
    }
}

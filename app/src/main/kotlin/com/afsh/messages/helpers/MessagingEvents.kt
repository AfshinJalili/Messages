package com.afsh.messages.helpers

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.afsh.commons.extensions.appLockManager
import com.afsh.commons.extensions.notificationManager
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.activities.AppLockActivity
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.getNotificationBitmap
import com.afsh.messages.extensions.notificationHelper
import com.bumptech.glide.Glide
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Typed messaging-domain occurrences routed through [MessagingEvents].
 *
 * Plain values only (no Android types) so policy, dispatch, and filtering stay testable on JVM.
 * See GLOSSARY.md ("Messaging Event") and ADR-0001.
 */
sealed interface MessagingEvent {
    val threadId: Long?

    data class IncomingMessage(
        override val threadId: Long,
        val messageId: Long,
        val isMms: Boolean,
        val isSpam: Boolean,
        val address: String,
        val senderName: String,
        val body: String,
        val photoUri: String = "",
        val attachmentUri: String? = null,
    ) : MessagingEvent

    data class MessageSent(
        override val threadId: Long,
        val messageId: Long,
        val isMms: Boolean,
    ) : MessagingEvent

    data class SendFailed(
        override val threadId: Long,
        val messageId: Long,
        val isMms: Boolean,
        val recipientName: String,
        val reason: String,
    ) : MessagingEvent

    data class DeliveryUpdated(
        override val threadId: Long?,
        val messageId: Long,
    ) : MessagingEvent

    data class ReadStateChanged(
        override val threadId: Long,
        val read: Boolean,
        val unreadCount: Int? = null,
    ) : MessagingEvent

    data class MessageDeleted(
        override val threadId: Long,
        val messageId: Long,
        val isMms: Boolean,
    ) : MessagingEvent

    data class ScheduledMessageSent(
        override val threadId: Long,
        val messageId: Long,
    ) : MessagingEvent

    data class ConversationsChanged(
        override val threadId: Long? = null,
        val reconcile: Boolean = true,
    ) : MessagingEvent
}

/**
 * Whether [this] event should cause a conversation timeline for [targetThreadId] to refresh.
 *
 * Bulk structural [MessagingEvent.ConversationsChanged] events (`threadId == null`, such as import
 * or Undo deletion) refresh open threads; single-thread conversation-list metadata updates (such as
 * saving a draft on pause) do not reload the timeline.
 */
fun MessagingEvent.affectsThread(targetThreadId: Long): Boolean = when (this) {
    is MessagingEvent.IncomingMessage -> threadId == targetThreadId
    is MessagingEvent.MessageSent -> threadId == targetThreadId
    is MessagingEvent.SendFailed -> threadId == targetThreadId
    is MessagingEvent.DeliveryUpdated -> threadId == null || threadId == targetThreadId
    is MessagingEvent.ReadStateChanged -> threadId == targetThreadId
    is MessagingEvent.MessageDeleted -> threadId == targetThreadId
    is MessagingEvent.ScheduledMessageSent -> threadId == targetThreadId
    is MessagingEvent.ConversationsChanged -> threadId == null
}

/** Whether [this] event can change the Spam review list. */
fun MessagingEvent.affectsSpam(): Boolean = when (this) {
    is MessagingEvent.IncomingMessage -> isSpam
    is MessagingEvent.MessageDeleted -> true
    is MessagingEvent.ConversationsChanged -> true
    is MessagingEvent.ReadStateChanged -> true
    is MessagingEvent.MessageSent,
    is MessagingEvent.SendFailed,
    is MessagingEvent.DeliveryUpdated,
    is MessagingEvent.ScheduledMessageSent -> false
}

/** Whether [this] event can change conversation lists such as Archive or Recycle Bin. */
fun MessagingEvent.affectsConversationList(): Boolean = when (this) {
    is MessagingEvent.DeliveryUpdated -> false
    is MessagingEvent.IncomingMessage,
    is MessagingEvent.MessageSent,
    is MessagingEvent.SendFailed,
    is MessagingEvent.ReadStateChanged,
    is MessagingEvent.MessageDeleted,
    is MessagingEvent.ScheduledMessageSent,
    is MessagingEvent.ConversationsChanged -> true
}

/** Platform host adapter at the seam of [MessagingEventCoordinator]. */
interface MessagingEventHost {
    fun isConversationMuted(threadId: Long): Boolean
    fun showIncomingAlert(event: MessagingEvent.IncomingMessage)
    fun showSendFailureAlert(event: MessagingEvent.SendFailed)
    /** Removes the conversation's message notification. */
    fun cancelConversationNotification(threadId: Long)
    /** Removes the conversation's "not sent" notification. */
    fun cancelSendFailureNotification(threadId: Long)
    fun refreshInbox()
}

/**
 * Single coordinator that evaluates [AttentionPolicy] against [presence], runs platform
 * notification / reconcile side effects through [host], and broadcasts typed [MessagingEvent]s.
 */
class MessagingEventCoordinator(
    val presence: AppPresenceTracker,
    private val host: MessagingEventHost,
) {
    private val eventsFlow = MutableSharedFlow<MessagingEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MessagingEvent> = eventsFlow.asSharedFlow()

    private val inThreadFailuresFlow = MutableSharedFlow<MessagingEvent.SendFailed>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val inThreadFailures: SharedFlow<MessagingEvent.SendFailed> = inThreadFailuresFlow.asSharedFlow()

    /** The failed message each thread's "not sent" alert is for, so the parts of one message alert once. */
    private val sendFailureAlerts = ConcurrentHashMap<Long, MessageKey>()

    private data class MessageKey(val messageId: Long, val isMms: Boolean)

    fun publish(event: MessagingEvent): AttentionDecision {
        val decision = decide(event).let {
            if (it is AttentionDecision.ShowSendFailureAlert && !recordSendFailureAlert(it.event)) AttentionDecision.Suppress else it
        }

        when (decision) {
            is AttentionDecision.ShowIncomingAlert -> host.showIncomingAlert(decision.event)
            is AttentionDecision.ShowSendFailureAlert -> host.showSendFailureAlert(decision.event)
            is AttentionDecision.ShowInThreadFailureReason -> inThreadFailuresFlow.tryEmit(decision.event)
            is AttentionDecision.ClearIncomingAlert -> host.cancelConversationNotification(decision.threadId)
            is AttentionDecision.ClearSendFailureAlert -> clearSendFailureAlert(decision.threadId)
            AttentionDecision.Suppress -> Unit
        }

        if (event is MessagingEvent.ScheduledMessageSent ||
            (event is MessagingEvent.ConversationsChanged && event.reconcile)
        ) {
            host.refreshInbox()
        }

        eventsFlow.tryEmit(event)
        return decision
    }

    /**
     * Removes [threadId]'s message notification for an action that is not a [MessagingEvent], such as
     * marking its sender as spam or blocking it.
     */
    fun clearConversationNotification(threadId: Long) {
        host.cancelConversationNotification(threadId)
    }

    /** The user is now viewing [threadId]: none of its notifications has anything to tell them. */
    fun conversationEntered(threadId: Long) {
        host.cancelConversationNotification(threadId)
        clearSendFailureAlert(threadId)
    }

    private fun decide(event: MessagingEvent): AttentionDecision {
        val flags = when (event) {
            is MessagingEvent.IncomingMessage ->
                ConversationAttentionFlags(isMuted = host.isConversationMuted(event.threadId))
            else -> ConversationAttentionFlags()
        }
        return AttentionPolicy.decide(event, presence.current(), flags)
    }

    /** False when the thread's alert already shows this message: another part of it failed. */
    private fun recordSendFailureAlert(event: MessagingEvent.SendFailed): Boolean {
        val key = MessageKey(event.messageId, event.isMms)
        return sendFailureAlerts.put(event.threadId, key) != key
    }

    private fun clearSendFailureAlert(threadId: Long) {
        sendFailureAlerts.remove(threadId)
        host.cancelSendFailureNotification(threadId)
    }
}

/**
 * App-wide owner of [MessagingEvent] dispatch, [AppPresence], and [AttentionPolicy] execution.
 * Initialized once in [com.afsh.messages.App.onCreate].
 */
object MessagingEvents {
    @Volatile
    private var initialized = false

    @Volatile
    private var coordinator: MessagingEventCoordinator = createUnboundCoordinator()

    internal val presence: AppPresenceTracker get() = coordinator.presence
    val events: SharedFlow<MessagingEvent> get() = coordinator.events
    val inThreadFailures: SharedFlow<MessagingEvent.SendFailed> get() = coordinator.inThreadFailures

    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = application.applicationContext
            val host = AndroidMessagingEventHost(app)
            val powerManager = app.getSystemService(PowerManager::class.java)
            val keyguardManager = app.getSystemService(KeyguardManager::class.java)
            val tracker = AppPresenceTracker(
                isDeviceLockedOrOff = {
                    val screenOff = powerManager != null && !powerManager.isInteractive
                    val keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked
                    // isLockedNow: reading presence on a receiver thread must not lock the app.
                    screenOff || keyguardLocked || app.appLockManager.isLockedNow()
                },
                onEnterConversation = { threadId -> coordinator.conversationEntered(threadId) },
            )
            coordinator = MessagingEventCoordinator(tracker, host)
            application.registerActivityLifecycleCallbacks(PresenceLifecycleCallbacks(tracker))
            // System UI sends USER_PRESENT, so a not-exported receiver never gets it. It is a protected
            // broadcast, so other apps cannot send it.
            ContextCompat.registerReceiver(
                app,
                DeviceUnlockReceiver(tracker),
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_EXPORTED,
            )
            initialized = true
        }
    }

    fun currentPresence(): AppPresence = presence.current()

    fun publish(event: MessagingEvent): AttentionDecision = coordinator.publish(event)

    /** See [MessagingEventCoordinator.clearConversationNotification]. */
    fun clearConversationNotification(threadId: Long) = coordinator.clearConversationNotification(threadId)

    fun onConversationActive(token: Any, threadId: Long, isRecycleBin: Boolean) {
        presence.onConversationActive(token, threadId, isRecycleBin)
    }

    fun onConversationThreadUpdated(token: Any, threadId: Long, isRecycleBin: Boolean) {
        presence.onConversationThreadUpdated(token, threadId, isRecycleBin)
    }

    fun onConversationInactive(token: Any) {
        presence.onConversationInactive(token)
    }

    private fun createUnboundCoordinator(): MessagingEventCoordinator {
        val noOpHost = object : MessagingEventHost {
            override fun isConversationMuted(threadId: Long) = false
            override fun showIncomingAlert(event: MessagingEvent.IncomingMessage) = Unit
            override fun showSendFailureAlert(event: MessagingEvent.SendFailed) = Unit
            override fun cancelConversationNotification(threadId: Long) = Unit
            override fun cancelSendFailureNotification(threadId: Long) = Unit
            override fun refreshInbox() = Unit
        }
        return MessagingEventCoordinator(AppPresenceTracker(), noOpHost)
    }

    private class PresenceLifecycleCallbacks(
        private val tracker: AppPresenceTracker,
    ) : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is AppLockActivity) {
                tracker.onLockScreenActive(activity)
            } else {
                tracker.onScreenResumed(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            tracker.onScreenPaused(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            tracker.onScreenPaused(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }

    /** No Activity callback fires when the keyguard leaves after the conversation already resumed. */
    private class DeviceUnlockReceiver(
        private val tracker: AppPresenceTracker,
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tracker.onDeviceUnlocked()
        }
    }

    private class AndroidMessagingEventHost(private val context: Context) : MessagingEventHost {
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun isConversationMuted(threadId: Long): Boolean =
            context.config.isConversationMuted(threadId)

        override fun showIncomingAlert(event: MessagingEvent.IncomingMessage) {
            ensureBackgroundThread {
                val bitmap = loadIncomingBitmap(event)
                mainHandler.post {
                    context.notificationHelper.showMessageNotification(
                        messageId = event.messageId,
                        address = event.address,
                        body = event.body,
                        threadId = event.threadId,
                        bitmap = bitmap,
                        sender = event.senderName,
                    )
                }
            }
        }

        override fun showSendFailureAlert(event: MessagingEvent.SendFailed) {
            mainHandler.post {
                context.notificationHelper.showSendingFailedNotification(
                    recipientName = event.recipientName,
                    threadId = event.threadId,
                )
            }
        }

        override fun cancelConversationNotification(threadId: Long) {
            context.notificationManager.cancel(threadId.hashCode())
        }

        override fun cancelSendFailureNotification(threadId: Long) {
            context.notificationHelper.cancelSendFailureNotification(threadId)
        }

        override fun refreshInbox() {
            InboxRepository.refreshInbox(context)
        }

        private fun loadIncomingBitmap(event: MessagingEvent.IncomingMessage) =
            if (event.isMms && !event.attachmentUri.isNullOrEmpty()) {
                val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
                try {
                    Glide.with(context)
                        .asBitmap()
                        .load(Uri.parse(event.attachmentUri))
                        .centerCrop()
                        .submit(size, size)
                        .get()
                } catch (_: Exception) {
                    null
                }
            } else {
                context.getNotificationBitmap(event.photoUri)
            }
    }
}

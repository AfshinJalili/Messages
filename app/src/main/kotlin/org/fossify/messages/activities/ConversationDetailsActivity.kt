package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.getContactFromAddress
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.startContactDetailsIntent
import org.fossify.messages.helpers.OPEN_THREAD_SEARCH
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.components.showRenameDialog
import org.fossify.messages.ui.details.ConversationDetailsScreen
import org.fossify.messages.ui.details.ConversationDetailsUiState
import org.fossify.messages.ui.details.DetailsEvent
import org.fossify.messages.ui.details.Member
import org.fossify.messages.ui.details.SimChoice

/** 09 · Conversation / details. */
class ConversationDetailsActivity : ComposePageActivity() {
    private var state by mutableStateOf(ConversationDetailsUiState())
    private var conversation: Conversation? = null
    private val threadId by lazy { intent.getLongExtra(THREAD_ID, 0L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
    }

    @Composable
    override fun Page() = ConversationDetailsScreen(state, ::onEvent)

    @SuppressLint("MissingPermission")
    private fun load() = ensureBackgroundThread {
        val conversation = conversationsDB.getConversationWithThreadId(threadId)
        val participants = if (conversation?.isScheduled == true) {
            messagesDB.getThreadMessages(threadId).firstOrNull()?.participants.orEmpty()
        } else {
            getThreadParticipants(threadId, null)
        }
        val members = participants.mapNotNull { contact ->
            val number = contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: return@mapNotNull null
            Member(contact.name, number, contact.photoUri)
        }
        val single = members.singleOrNull()?.takeIf { conversation?.isGroupConversation != true }
        val sims = subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()
            .map { info ->
                val slot = getString(R.string.message_sim_label, "%d".format(info.simSlotIndex + 1))
                SimChoice(info.subscriptionId, getString(R.string.detail_pair, slot, info.displayName))
            }
        runOnUiThread {
            this.conversation = conversation
            state = ConversationDetailsUiState(
                title = conversation?.title ?: single?.name.orEmpty(),
                photoUri = conversation?.photoUri ?: single?.photoUri.orEmpty(),
                members = members,
                isGroup = members.size > 1,
                canDial = single != null && !isShortCodeWithLetters(single.number),
                muted = config.isConversationMuted(threadId),
                customNotifications = config.customNotifications.contains(threadId.toString()),
                sims = sims,
                sim = members.firstOrNull()?.let { config.getUseSIMIdAtNumber(it.number) } ?: 0,
                loading = false,
            )
        }
    }

    private fun onEvent(event: DetailsEvent) {
        when (event) {
            DetailsEvent.Back -> finish()
            DetailsEvent.Call -> state.members.singleOrNull()?.let { dialNumber(it.number) }
            DetailsEvent.Search -> openThreadSearch()
            DetailsEvent.ToggleMute -> {
                val muted = !state.muted
                config.setConversationMuted(threadId, muted)
                state = state.copy(muted = muted)
            }
            is DetailsEvent.OpenContact -> openContact(event.member.number)
            DetailsEvent.Rename -> conversation?.let { current ->
                showRenameDialog(current) { title ->
                    state = state.copy(title = title)
                    ensureBackgroundThread { conversation = renameConversation(current, newTitle = title) }
                }
            }
            is DetailsEvent.SetCustomNotifications -> setCustomNotifications(event.enabled)
            DetailsEvent.OpenNotificationSettings -> openNotificationSettings()
            is DetailsEvent.SetSim -> {
                state.members.forEach { config.saveUseSIMIdAtNumber(it.number, event.subscriptionId) }
                state = state.copy(sim = event.subscriptionId)
            }
        }
    }

    private fun openContact(number: String) {
        getContactFromAddress(number) { contact ->
            if (contact != null) {
                startContactDetailsIntent(contact)
            } else {
                Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = "vnd.android.cursor.item/contact"
                    putExtra(KEY_PHONE, number)
                    launchActivityIntent(this)
                }
            }
        }
    }

    private fun openThreadSearch() {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(OPEN_THREAD_SEARCH, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(this)
        }
    }

    private fun setCustomNotifications(enabled: Boolean) {
        if (enabled) {
            config.addCustomNotificationsByThreadId(threadId)
            createNotificationChannel()
        } else {
            config.removeCustomNotificationsByThreadId(threadId)
            notificationManager.deleteNotificationChannel(threadId.toString())
        }
        state = state.copy(customNotifications = enabled)
    }

    private fun openNotificationSettings() {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, threadId.toString())
            launchActivityIntent(this)
        }
    }

    private fun createNotificationChannel() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()
        NotificationChannel(threadId.toString(), state.title, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }
}

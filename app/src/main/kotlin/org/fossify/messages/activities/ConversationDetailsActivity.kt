package org.fossify.messages.activities

import org.fossify.messages.helpers.designFloat
import android.app.NotificationChannel
import android.content.res.ColorStateList
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.messages.R
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.helpers.OPEN_THREAD_SEARCH
import org.fossify.messages.messaging.isShortCodeWithLetters
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.adapters.ContactsAdapter
import org.fossify.messages.databinding.ActivityConversationDetailsBinding
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.setupSurfaceAppBar
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getContactFromAddress
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.startContactDetailsIntent
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.models.Conversation

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.conversationDetailsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.conversationDetailsNestedScrollview,
            topAppBar = binding.conversationDetailsAppbar,
        )

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                setupActions()
                setupTextViews()
                setupParticipants()
                setupCustomNotifications()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupSurfaceAppBar(binding.conversationDetailsAppbar)
        updateTextColors(binding.conversationDetailsHolder)

        val primaryColor = getProperPrimaryColor()
        arrayOf(
            binding.notificationsHeading,
            binding.conversationNameHeading,
            binding.membersHeading
        ).forEach {
            it.setTextColor(primaryColor)
        }
    }

    private fun setupActions() = binding.apply {
        val textColor = getProperTextColor()
        arrayOf(conversationCall, conversationContact, conversationSearch, conversationMute).forEach {
            it.setTextColor(textColor)
            it.iconTint = ColorStateList.valueOf(textColor)
            it.backgroundTintList = ColorStateList.valueOf(textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_surface_tint)))
        }

        val number = participants.singleOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber
        val isOneToOne = number != null && conversation?.isGroupConversation != true
        conversationCall.beVisibleIf(isOneToOne && !isShortCodeWithLetters(number!!))
        conversationCall.setOnClickListener { dialNumber(number!!) }
        conversationContact.beVisibleIf(isOneToOne)
        conversationContact.setOnClickListener { openContact(number!!) }
        conversationSearch.setOnClickListener { openThreadSearch() }
        conversationMute.setOnClickListener {
            config.setConversationMuted(threadId, !config.isConversationMuted(threadId))
            updateMuteButton()
        }
        updateMuteButton()
    }

    private fun updateMuteButton() = binding.conversationMute.apply {
        val isMuted = config.isConversationMuted(threadId)
        setText(if (isMuted) R.string.unmute_conversation else R.string.mute_conversation)
        setIconResource(if (isMuted) org.fossify.commons.R.drawable.ic_bell_vector else R.drawable.ic_bell_off_vector)
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

    private fun setupCustomNotifications() {
        binding.apply {
            notificationsHeading.beVisible()
            customNotificationsHolder.beVisible()
            customNotifications.isChecked = config.customNotifications.contains(threadId.toString())
            customNotificationsButton.beVisibleIf(customNotifications.isChecked)

            customNotificationsHolder.setOnClickListener {
                customNotifications.toggle()
                if (customNotifications.isChecked) {
                    customNotificationsButton.beVisible()
                    config.addCustomNotificationsByThreadId(threadId)
                    createNotificationChannel()
                } else {
                    customNotificationsButton.beGone()
                    config.removeCustomNotificationsByThreadId(threadId)
                    removeNotificationChannel()
                }
            }

            customNotificationsButton.setOnClickListener {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, threadId.toString())
                    startActivity(this)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes
            )
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        notificationManager.deleteNotificationChannel(threadId.toString())
    }

    private fun setupTextViews() {
        binding.conversationName.apply {
            ResourcesCompat.getDrawable(
                resources,
                org.fossify.commons.R.drawable.ic_edit_vector,
                theme
            )?.apply {
                applyColorFilter(getProperTextColor())
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(
                    this@ConversationDetailsActivity,
                    conversation!!
                ) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
    }

    companion object {
    }

    private fun setupParticipants() {
        val adapter = ContactsAdapter(this, participants, binding.participantsRecyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        binding.participantsRecyclerview.adapter = adapter
    }
}

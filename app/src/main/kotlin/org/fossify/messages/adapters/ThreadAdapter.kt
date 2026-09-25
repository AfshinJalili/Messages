package org.fossify.messages.adapters

import org.fossify.messages.helpers.designFloat
import android.provider.Telephony
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.transition.Fade
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import org.fossify.commons.extensions.getProperBackgroundColor
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.NewConversationActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.activities.VCardViewerActivity
import org.fossify.messages.databinding.ItemAttachmentDocumentBinding
import org.fossify.messages.databinding.ItemAttachmentImageBinding
import org.fossify.messages.databinding.ItemAttachmentVcardBinding
import org.fossify.messages.databinding.ItemMessageBinding
import org.fossify.messages.databinding.ItemThreadDateTimeBinding
import org.fossify.messages.databinding.ItemThreadErrorBinding
import org.fossify.messages.databinding.ItemThreadSendingBinding
import org.fossify.messages.databinding.ItemThreadSpamGroupBinding
import org.fossify.messages.databinding.ItemThreadSuccessBinding
import org.fossify.messages.databinding.ItemThreadUnreadSeparatorBinding
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.dialogs.MessageDetailsDialog
import org.fossify.messages.dialogs.SelectTextDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.extractOtpCode
import org.fossify.messages.extensions.getContactFromAddress
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.extensions.startContactDetailsIntent
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.helpers.THREAD_DATE_TIME
import org.fossify.messages.helpers.THREAD_RECEIVED_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_ERROR
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENDING
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENT
import org.fossify.messages.helpers.THREAD_SPAM_GROUP
import org.fossify.messages.helpers.THREAD_UNREAD_SEPARATOR
import org.fossify.messages.helpers.generateStableId
import org.fossify.messages.helpers.setupDocumentPreview
import org.fossify.messages.helpers.setupVCardPreview
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadDateTime
import org.fossify.messages.models.ThreadItem.ThreadError
import org.fossify.messages.models.ThreadItem.ThreadSending
import org.fossify.messages.models.ThreadItem.ThreadSent
import org.fossify.messages.models.ThreadItem.ThreadSpamGroup
import org.fossify.messages.models.spamReasonLabel
import org.fossify.messages.models.ThreadItem.ThreadUnreadSeparator
import org.joda.time.DateTime

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val isRecycleBin: Boolean,
    val unmarkSpam: (messages: List<Message>) -> Unit = {},
    val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()
    private var messageMenu: PopupWindow? = null

    /** Spam reason by SMS id. Set before [updateMessages]; a change rebinds every row. */
    var spamReasons: Map<Long, Int> = emptyMap()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private fun spamReason(message: Message) = if (message.isMMS) null else spamReasons[message.id]

    @SuppressLint("MissingPermission")
    private val simLabels = activity.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()
        .associate { it.subscriptionId to (it.simSlotIndex + 1).toString() }
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.8f).toInt()
    private val groupedTopMargin = (2 * activity.resources.displayMetrics.density).toInt()
    private val ungroupedTopMargin = (8 * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 3
        private const val GROUPING_WINDOW_SECS = 60
    }

    init {
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        val hasText = selectedMessages.any { it.body.isNotEmpty() }
        val showSaveAs = getSelectedItems().all {
            it is Message && (it.attachment?.attachments?.size ?: 0) > 0
        } && getSelectedAttachments().isNotEmpty()

        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = hasText
            findItem(R.id.cab_save_as).isVisible = showSaveAs
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_properties).isVisible = isOneItemSelected
            findItem(R.id.cab_restore).isVisible = isRecycleBin
            findItem(R.id.cab_not_spam).isVisible = selectedMessages.isNotEmpty() && selectedMessages.all { spamReason(it) != null }

            val allStarred = selectedMessages.all { activity.config.isMessageStarred(it.id, it.isMMS) }
            findItem(R.id.cab_star).isVisible = !isRecycleBin && !allStarred
            findItem(R.id.cab_unstar).isVisible = !isRecycleBin && allStarred
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_select_all -> selectAll()
            R.id.cab_properties -> showMessageDetails()
            R.id.cab_star -> setSelectedStarred(true)
            R.id.cab_unstar -> setSelectedStarred(false)
            R.id.cab_not_spam -> {
                val selected = getSelectedItems().filterIsInstance<Message>()
                finishActMode()
                unmarkSpam(selected)
            }
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = currentList.getOrNull(position) is Message

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onActionModeCreated() { messageMenu?.dismiss() }

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_DATE_TIME -> ItemThreadDateTimeBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENT -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            THREAD_UNREAD_SEPARATOR -> ItemThreadUnreadSeparatorBinding.inflate(layoutInflater, parent, false)
            THREAD_SPAM_GROUP -> ItemThreadSpamGroupBinding.inflate(layoutInflater, parent, false)
            else -> ItemMessageBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is ThreadError || item is Message || item is ThreadSpamGroup
        val isLongClickable = item is Message
        holder.bindView(item, isClickable, isLongClickable) { itemView, _ ->
            when (item) {
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadError -> setupThreadError(itemView)
                is ThreadSent -> setupThreadSuccess(itemView, item.delivered)
                is ThreadSending -> setupThreadSending(itemView)
                is ThreadUnreadSeparator -> setupUnreadSeparator(itemView)
                is ThreadSpamGroup -> setupSpamGroup(itemView, item)
                is Message -> setupView(holder, itemView, item, position)
            }
        }
        bindViewHolder(holder)
        if (item is Message) holder.itemView.setOnClickListener {
            if (selectedKeys.isNotEmpty()) holder.viewClicked(item)
            else {
                val binding = ItemMessageBinding.bind(holder.itemView)
                showMessageMenu(binding.threadMessageBody.takeIf { it.isVisible } ?: binding.threadMessageWrapper, holder, item)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.firstOrNull() is org.fossify.commons.models.RecyclerSelectionPayload && getItem(position) is Message) {
            val bubble = ItemMessageBinding.bind(holder.itemView).threadMessageWrapper
            bubble.animate().cancel()
            bubble.scaleX = 1f
            bubble.scaleY = 1f
            if (ValueAnimator.areAnimatorsEnabled()) {
                bubble.scaleX = 0.98f
                bubble.scaleY = 0.98f
                bubble.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> item.getStableId()
            is ThreadDateTime -> {
                generateStableId(THREAD_DATE_TIME, item.date.toLong())
            }
            is ThreadError -> generateStableId(THREAD_SENT_MESSAGE_ERROR, item.messageId)
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
            is ThreadSent -> generateStableId(THREAD_SENT_MESSAGE_SENT, item.messageId)
            is ThreadUnreadSeparator -> generateStableId(THREAD_UNREAD_SEPARATOR, 0)
            is ThreadSpamGroup -> generateStableId(THREAD_SPAM_GROUP, item.key)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is ThreadUnreadSeparator -> THREAD_UNREAD_SEPARATOR
            is ThreadSpamGroup -> THREAD_SPAM_GROUP
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        if (selectedMessages.isEmpty()) return

        val textToCopy = if (selectedMessages.size == 1) {
            selectedMessages.first().body
        } else {
            selectedMessages.filter { it.body.isNotEmpty() }.joinToString("\n\n") { message ->
                val format = "${activity.config.dateFormat}, ${activity.getTimeFormat()}"
                val dateTime = DateTime(message.millis()).toString(format)
                val sender = if (message.isReceivedMessage()) message.senderName else activity.getString(R.string.me)
                "[$dateTime] $sender: ${message.body}"
            }
        }

        if (textToCopy.isNotEmpty()) {
            activity.copyToClipboard(textToCopy)
        }
    }

    private fun getSelectedAttachments(): List<Attachment> {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        return selectedMessages.flatMap { it.attachment?.attachments.orEmpty() }
    }

    private fun saveAs() {
        val attachments = getSelectedAttachments()
        if (attachments.isNotEmpty()) {
            (activity as? ThreadActivity)?.saveMMS(attachments)
        }
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun showMessageDetails() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        MessageDetailsDialog(activity, message)
    }

    private fun askConfirmDelete(messages: List<Message> = getSelectedItems().filterIsInstance<Message>()) {
        val itemsCnt = messages.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = if (activity.config.useRecycleBin && !isRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(activity, question, activity.config.useRecycleBin && !isRecycleBin) { skipRecycleBin ->
            ensureBackgroundThread {
                val messagesToRemove = messages
                if (messagesToRemove.isNotEmpty()) {
                    val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin && !isRecycleBin
                    deleteMessages(messagesToRemove.filterIsInstance<Message>(), toRecycleBin, false)
                }
            }
        }
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                val messagesToRestore = getSelectedItems()
                if (messagesToRestore.isNotEmpty()) {
                    deleteMessages(messagesToRestore.filterIsInstance<Message>(), false, true)
                }
            }
        }
    }

    private fun forwardMessage(message: Message? = getSelectedItems().firstOrNull() as? Message) {
        message ?: return
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
            }

            activity.startActivity(this)
        }
    }

    private fun setSelectedStarred(starred: Boolean) {
        val messages = getSelectedItems().filterIsInstance<Message>()
        messages.forEach { activity.config.setMessageStarred(it.id, it.isMMS, starred) }
        val positions = messages.map { currentList.indexOf(it) }
        finishActMode()
        positions.forEach { notifyItemChanged(it) }
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        return currentList.filter {
            selectedKeys.contains((it as? Message)?.getSelectionKey() ?: 0)
        } as ArrayList<ThreadItem>
    }

    fun updateMessages(
        newMessages: ArrayList<ThreadItem>,
        scrollPosition: Int = -1,
        smoothScroll: Boolean = false
    ) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                if (smoothScroll) {
                    recyclerView.smoothScrollToPosition(scrollPosition)
                } else {
                    recyclerView.scrollToPosition(scrollPosition)
                }
            }
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message, position: Int) {
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.isSelected = selectedKeys.contains(message.getSelectionKey())
            threadMessageBubble.updateLayoutParams<RelativeLayout.LayoutParams> {
                width = if (message.attachment?.attachments?.isNotEmpty() == true) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            }
            threadMessageWrapper.animate().cancel()
            threadMessageWrapper.scaleX = 1f
            threadMessageWrapper.scaleY = 1f
            threadMessageBody.apply {
                setMessageBody(message.body)
                specialClicksEnabled = { selectedKeys.isEmpty() }
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                beVisibleIf(message.body.isNotEmpty())
                setOnLongClickListener {
                    holder.viewLongClicked()
                    true
                }

                setOnClickListener {
                    if (selectedKeys.isNotEmpty()) holder.viewClicked(message)
                    else showMessageMenu(this, holder, message)
                }
            }

            val isStarred = activity.config.isMessageStarred(message.id, message.isMMS)
            val simLabel = simLabels[message.subscriptionId] ?: "?"
            threadMessageSim.apply {
                text = simLabel
                val simColor = when (simLabel) {
                    "1" -> activity.getColor(R.color.message_sim_one)
                    "2" -> activity.getColor(R.color.message_sim_two)
                    else -> textColor
                }
                backgroundTintList = android.content.res.ColorStateList.valueOf(simColor)
                setTextColor(simColor.getContrastColor())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * resources.designFloat(R.dimen.type_scale_sim_number))
                updateLayoutParams<LinearLayout.LayoutParams> {
                    width = (fontSize * resources.designFloat(R.dimen.type_scale_metadata)).toInt()
                    height = width
                }
            }
            threadMessageTime.apply {
                val time = (message.date * 1000L).formatTime(context)
                @SuppressLint("SetTextI18n")
                text = if (isStarred) "★ $time" else time
                spamReason(message)?.let {
                    text = activity.getString(R.string.spam_footer, activity.getString(R.string.inbox_spam), activity.getString(spamReasonLabel(it)), text)
                }
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * resources.designFloat(R.dimen.type_scale_message_footer))
                beVisible()
                val delivered = message.status == Telephony.Sms.STATUS_COMPLETE
                val statusIcon = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT && !message.isScheduled) {
                    if (delivered) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector
                } else 0
                val icon = if (statusIcon == 0) null else AppCompatResources.getDrawable(activity, statusIcon)?.mutate()?.apply {
                    setBounds(0, 0, textSize.toInt(), textSize.toInt())
                }
                setCompoundDrawablesRelative(null, null, icon, null)
                val description = "${activity.getString(R.string.message_sim_label, simLabel)}, $text"
                contentDescription = if (statusIcon != 0) "$description, ${activity.getString(if (delivered) R.string.message_delivered else R.string.message_sent)}" else description
            }

            setupOtpChip(messageBinding = this, message = message)

            if (message.isReceivedMessage()) {
                setupReceivedMessageView(messageBinding = this, message = message)
            } else {
                setupSentMessageView(messageBinding = this, message = message)
            }

            // After the sent/received pass, which unconditionally re-shows the avatar.
            setupGrouping(this, message, position)

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                threadMessageAttachmentsHolder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }

                    threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    private fun showMessageMenu(anchor: View, holder: ViewHolder, message: Message) {
        messageMenu?.dismiss()
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, padding / 2, 0, padding / 2)
            background = GradientDrawable().apply {
                setColor(activity.getProperBackgroundColor())
                cornerRadius = 12 * density
            }
        }
        val scroll = ScrollView(activity).apply {
            addView(content)
            isFillViewport = true
        }
        val visibleFrame = android.graphics.Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val popup = PopupWindow(scroll, minOf((240 * density).toInt(), visibleFrame.width() - padding * 2), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(content.background)
            elevation = 8 * density
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            if (ValueAnimator.areAnimatorsEnabled()) {
                enterTransition = Fade().setDuration(150)
                exitTransition = Fade().setDuration(100)
            }
        }
        messageMenu = popup
        val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { popup.dismiss() }
        }
        anchor.addOnAttachStateChangeListener(detachListener)
        popup.setOnDismissListener {
            anchor.removeOnAttachStateChangeListener(detachListener)
            if (messageMenu === popup) messageMenu = null
        }
        fun action(label: Int, icon: Int, run: () -> Unit) {
            content.addView(TextView(activity).apply {
                setText(label)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_size)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
                compoundDrawablePadding = padding
                val drawable = AppCompatResources.getDrawable(activity, icon)?.mutate()?.apply {
                    applyColorFilter(textColor)
                    setBounds(0, 0, (24 * density).toInt(), (24 * density).toInt())
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                val selectable = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
                setBackgroundResource(selectable.resourceId)
                setOnClickListener {
                    popup.dismiss()
                    if (currentList.any { it is Message && it.getStableId() == message.getStableId() }) run()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (message.body.isNotEmpty()) action(org.fossify.commons.R.string.copy, org.fossify.commons.R.drawable.ic_copy_vector) { activity.copyToClipboard(message.body) }
        action(org.fossify.commons.R.string.delete, org.fossify.commons.R.drawable.ic_delete_vector) { askConfirmDelete(listOf(message)) }
        if (message.body.isNotBlank()) action(org.fossify.commons.R.string.select_text, org.fossify.commons.R.drawable.ic_select_all_vector) { SelectTextDialog(activity, message.body) }
        if (!isRecycleBin) {
            val starred = activity.config.isMessageStarred(message.id, message.isMMS)
            action(if (starred) R.string.unstar_message else R.string.star_message,
                if (starred) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector) {
                activity.config.setMessageStarred(message.id, message.isMMS, !starred)
                val position = currentList.indexOfFirst { it is Message && it.getStableId() == message.getStableId() }
                if (position >= 0) notifyItemChanged(position)
            }
        }
        action(R.string.forward_message, R.drawable.ic_forward_vector) { forwardMessage(message) }
        if (message.body.isNotEmpty()) action(org.fossify.commons.R.string.share, org.fossify.commons.R.drawable.ic_share_vector) { activity.shareTextIntent(message.body) }
        if (message.isScheduled) action(org.fossify.commons.R.string.properties, org.fossify.commons.R.drawable.ic_info_vector) { holder.viewClicked(message) }
        content.measure(
            View.MeasureSpec.makeMeasureSpec(popup.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        // Give the dropdown a bounded height so Android can flip it above the anchor.
        // ScrollView keeps every action reachable when the keyboard or large text reduces space.
        popup.height = minOf(content.measuredHeight, popup.getMaxAvailableHeight(anchor).coerceAtLeast(1))
        popup.showAsDropDown(anchor)
    }

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.END)
                connect(threadMessageWrapper.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(threadMessageHolder)
            }

            threadMessageSenderPhoto.beVisible()
            threadMessageSenderPhoto.setOnClickListener {
                val contact = message.getSender()!!
                activity.getContactFromAddress(contact.phoneNumbers.first().normalizedNumber) {
                    if (it != null) {
                        activity.startContactDetailsIntent(it)
                    }
                }
            }

            threadMessageBubble.background = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
            threadMessageTime.setTextColor(textColor)
            threadMessageBody.apply {
                setTextColor(textColor)
                setLinkTextColor(activity.getProperPrimaryColor())
            }

            if (!activity.isFinishing && !activity.isDestroyed) {
                val contactLetterIcon = SimpleContactsHelper(activity).getContactLetterIcon(message.senderName)
                val placeholder = contactLetterIcon.toDrawable(activity.resources)

                val options = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholder)
                    .centerCrop()

                Glide.with(activity)
                    .load(message.senderPhotoUri)
                    .placeholder(placeholder)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(threadMessageSenderPhoto)
            }
        }
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.START)
                connect(threadMessageWrapper.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                applyTo(threadMessageHolder)
            }

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            threadMessageBubble.updateLayoutParams<RelativeLayout.LayoutParams> {
                removeRule(RelativeLayout.END_OF)
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
            threadMessageBubble.background = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)?.apply {
                applyColorFilter(primaryColor)
            }
            threadMessageTime.setTextColor(contrastColor)
            threadMessageTime.compoundDrawablesRelative.filterNotNull().forEach { it.applyColorFilter(contrastColor) }
            threadMessageBody.apply {
                setTextColor(contrastColor)
                setLinkTextColor(contrastColor)

                if (message.isScheduled) {
                    typeface = Typeface.create(FontHelper.getTypeface(activity), Typeface.ITALIC)
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(contrastColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    typeface = FontHelper.getTypeface(activity)
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(maxChatBubbleWidth, maxChatBubbleWidth * MAX_MEDIA_HEIGHT_RATIO)
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    threadMessagePlayOutline.beGone()
                    threadMessageAttachmentsHolder.removeView(imageView.root)
                    return false
                }

                override fun onResourceReady(dr: Drawable, a: Any, t: Target<Drawable>, d: DataSource, i: Boolean) = false
            })
            .into(imageView.attachmentImage)

        imageView.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                foregroundColor = if (message.isReceivedMessage()) textColor else activity.getProperPrimaryColor().getContrastColor(),
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                foregroundColor = if (message.isReceivedMessage()) textColor else activity.getProperPrimaryColor().getContrastColor(),
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(attachmentView)
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        ItemThreadDateTimeBinding.bind(view).threadDateTime.apply {
            text = org.fossify.messages.helpers.ThreadDates.label(dateTime.date)
            org.fossify.messages.helpers.ThreadDates.style(this, activity)
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            threadSuccess.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector)
            threadSuccess.applyColorFilter(textColor)
        }
    }

    private fun setupThreadError(view: View) {
        val binding = ItemThreadErrorBinding.bind(view)
        binding.threadError.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize - 4)
    }

    private fun setupOtpChip(messageBinding: ItemMessageBinding, message: Message) {
        val code = if (message.isReceivedMessage()) message.body.extractOtpCode() else null
        messageBinding.threadMessageOtp.apply {
            beVisibleIf(code != null)
            if (code != null) {
                text = activity.getString(R.string.copy_otp_code, code)
                setOnClickListener { activity.copyToClipboard(code) }
            }
        }
    }

    /**
     * Google Messages draws one avatar per run of messages, on the last one. Keeping the space
     * reserved (INVISIBLE, not GONE) is what holds the rest of the run aligned with it.
     */
    private fun setupGrouping(messageBinding: ItemMessageBinding, message: Message, position: Int) {
        val next = currentList.getOrNull(position + 1) as? Message
        val groupedWithNext = next != null &&
            next.isReceivedMessage() == message.isReceivedMessage() &&
            next.senderPhoneNumber == message.senderPhoneNumber &&
            next.date - message.date <= GROUPING_WINDOW_SECS

        val previous = currentList.getOrNull(position - 1) as? Message
        val groupedWithPrevious = previous != null &&
            previous.isReceivedMessage() == message.isReceivedMessage() &&
            previous.senderPhoneNumber == message.senderPhoneNumber &&
            message.date - previous.date <= GROUPING_WINDOW_SECS

        if (message.isReceivedMessage() && groupedWithNext) {
            messageBinding.threadMessageSenderPhoto.visibility = View.INVISIBLE
        }

        messageBinding.threadMessageHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = if (currentList.getOrNull(position - 1) is ThreadDateTime) 0 else if (groupedWithPrevious) groupedTopMargin else ungroupedTopMargin
        }
    }

    private fun setupUnreadSeparator(view: View) {
        ItemThreadUnreadSeparatorBinding.bind(view).apply {
            val accent = activity.getProperPrimaryColor()
            threadUnreadLabel.setTextColor(accent)
            threadUnreadLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize - 4)
            threadUnreadRuleStart.setBackgroundColor(accent.adjustAlpha(resources.designFloat(R.dimen.opacity_rule)))
            threadUnreadRuleEnd.setBackgroundColor(accent.adjustAlpha(resources.designFloat(R.dimen.opacity_rule)))
        }
    }

    private fun setupSpamGroup(view: View, group: ThreadSpamGroup) {
        ItemThreadSpamGroupBinding.bind(view).apply {
            val color = textColor.adjustAlpha(resources.designFloat(R.dimen.opacity_secondary))
            threadSpamGroupLabel.text = resources.getQuantityString(R.plurals.spam_group_count, group.messageIds.size, group.messageIds.size)
            threadSpamGroupLabel.setTextColor(color)
            threadSpamGroupChevron.applyColorFilter(color)
            threadSpamGroupChevron.rotation = if (group.expanded) 180f else 0f
        }
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = (holder as ThreadViewHolder).binding
            if (binding is ItemMessageBinding) {
                Glide.with(activity).clear(binding.threadMessageSenderPhoto)
            }
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
            is ThreadDateTime -> {
                val new = newItem as ThreadDateTime
                oldItem.date == new.date
            }

            is ThreadUnreadSeparator -> true
            is ThreadSpamGroup -> oldItem.key == (newItem as ThreadSpamGroup).key
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is ThreadDateTime -> true
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
            is ThreadUnreadSeparator -> true
            is ThreadSpamGroup -> oldItem == newItem
        }
    }
}

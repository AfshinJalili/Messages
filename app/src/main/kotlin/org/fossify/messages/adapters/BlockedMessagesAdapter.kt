package org.fossify.messages.adapters

import org.fossify.messages.helpers.designFloat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.models.BlockedMessagesThread
import org.fossify.messages.models.spamReasonLabel

class BlockedMessagesAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
    private val restoreThreads: (List<BlockedMessagesThread>) -> Unit,
    private val deleteThreads: (List<BlockedMessagesThread>) -> Unit,
    private val allowThreads: (List<BlockedMessagesThread>) -> Unit,
) : MyRecyclerViewListAdapter<BlockedMessagesThread>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = BlockedMessagesThreadDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
        setHasStableIds(true)
    }

    fun updateThreads(threads: ArrayList<BlockedMessagesThread>) {
        submitList(threads.toList())
    }

    override fun getActionMenuId() = R.menu.cab_blocked_messages

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_restore -> performAction(restoreThreads)
            R.id.cab_allow_sender -> performAction(allowThreads)
            R.id.cab_delete -> performAction(deleteThreads)
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = itemCount

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.key

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.key == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = getItem(position)
        holder.bindView(
            thread,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, thread)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).key.toLong()

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(ItemConversationBinding.bind(holder.itemView).conversationImage)
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun setupView(view: View, thread: BlockedMessagesThread) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)
            // reason chip: why the newest message in this thread was blocked, and how much spam the thread has
            draftIndicator.apply {
                beVisible()
                text = activity.getString(R.string.label_with_count, activity.getString(spamReasonLabel(thread.reason)), thread.count)
                setTextColor(properPrimaryColor)
            }
            pinIndicator.beVisibleIf(false)
            conversationFrame.isSelected = selectedKeys.contains(thread.key)

            conversationAddress.apply {
                text = thread.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * resources.designFloat(R.dimen.type_scale_primary))
            }

            conversationBodyShort.apply {
                text = thread.snippet
                alpha = resources.designFloat(R.dimen.opacity_secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * resources.designFloat(R.dimen.type_scale_secondary))
            }

            conversationDate.apply {
                text = thread.date.formatDateOrTime(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false
                )
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * resources.designFloat(R.dimen.type_scale_metadata))
            }

            val customTypeface = FontHelper.getTypeface(activity)
            conversationAddress.setTypeface(customTypeface, Typeface.BOLD)
            conversationBodyShort.setTypeface(customTypeface, Typeface.NORMAL)
            conversationDate.setTypeface(customTypeface, Typeface.NORMAL)
            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(textColor)
            }

            setupBadgeCount(unreadCountBadge, thread.unreadCount)
            SimpleContactsHelper(activity).loadContactImage(
                path = thread.photoUri,
                imageView = conversationImage,
                placeholderName = thread.title,
                placeholderImage = null
            )
        }
    }

    private fun setupBadgeCount(view: TextView, count: Int) {
        view.apply {
            beVisibleIf(count > 0)
            text = if (count > MAX_UNREAD_BADGE_COUNT) {
                "$MAX_UNREAD_BADGE_COUNT+"
            } else {
                count.toString()
            }
            setTextColor(properPrimaryColor.getContrastColor())
            background?.applyColorFilter(properPrimaryColor)
        }
    }

    private fun performAction(action: (List<BlockedMessagesThread>) -> Unit) {
        val selectedThreads = currentList.filter { selectedKeys.contains(it.key) }
        finishActMode()
        action(selectedThreads)
    }

    private class BlockedMessagesThreadDiffCallback : DiffUtil.ItemCallback<BlockedMessagesThread>() {
        override fun areItemsTheSame(
            oldItem: BlockedMessagesThread,
            newItem: BlockedMessagesThread,
        ) = oldItem.threadId == newItem.threadId

        override fun areContentsTheSame(
            oldItem: BlockedMessagesThread,
            newItem: BlockedMessagesThread,
        ) = oldItem == newItem
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
    }
}

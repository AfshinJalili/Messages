package org.fossify.messages.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.extensions.config

/**
 * Gmail/Google Messages style inbox swipe. Directions are absolute (LEFT/RIGHT) on purpose: the
 * setting is phrased as the gesture the user makes, so it must not mirror under RTL. The underlay
 * is drawn only in the strip the row has uncovered, so it works with the translucent row background.
 */
class ConversationSwipeCallback(
    private val context: Context,
    private val isSwipeEnabled: () -> Boolean,
    private val onSwipe: (position: Int, action: SwipeAction) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val paint = Paint()
    private val iconInset = context.resources.getDimensionPixelSize(R.dimen.conversation_row_horizontal_padding)
    private val icons = HashMap<SwipeAction, Drawable?>()

    private fun actionFor(direction: Int): SwipeAction {
        val action = if (direction == ItemTouchHelper.LEFT) {
            context.config.swipeLeftAction
        } else {
            context.config.swipeRightAction
        }

        return if (action == SwipeAction.ARCHIVE && !context.config.isArchiveAvailable) {
            SwipeAction.NONE
        } else {
            action
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        if (!isSwipeEnabled()) {
            return 0
        }

        var directions = 0
        if (actionFor(ItemTouchHelper.LEFT) != SwipeAction.NONE) {
            directions = directions or ItemTouchHelper.LEFT
        }
        if (actionFor(ItemTouchHelper.RIGHT) != SwipeAction.NONE) {
            directions = directions or ItemTouchHelper.RIGHT
        }
        return makeMovementFlags(0, directions)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.3f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            onSwipe(position, actionFor(direction))
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            drawUnderlay(c, viewHolder.itemView, dX)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawUnderlay(canvas: Canvas, item: View, dX: Float) {
        val swipingRight = dX > 0
        val action = actionFor(if (swipingRight) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT)
        if (action == SwipeAction.NONE) {
            return
        }

        val left = if (swipingRight) item.left.toFloat() else item.right + dX
        val right = if (swipingRight) item.left + dX else item.right.toFloat()
        paint.color = ContextCompat.getColor(context, action.color)
        canvas.drawRect(left, item.top.toFloat(), right, item.bottom.toFloat(), paint)

        val icon = icons.getOrPut(action) {
            AppCompatResources.getDrawable(context, action.icon)?.mutate()?.apply { setTint(ContextCompat.getColor(context, R.color.on_action)) }
        } ?: return
        if (right - left < iconInset + icon.intrinsicWidth) {
            return
        }

        val iconLeft = if (swipingRight) {
            item.left + iconInset
        } else {
            item.right - iconInset - icon.intrinsicWidth
        }
        val iconTop = item.top + (item.height - icon.intrinsicHeight) / 2
        icon.setBounds(
            iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconTop + icon.intrinsicHeight
        )
        icon.draw(canvas)
    }
}

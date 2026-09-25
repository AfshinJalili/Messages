package org.fossify.messages.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.fossify.messages.R

/** [id] is what gets persisted, so never renumber an entry. */
enum class SwipeAction(
    val id: Int,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    NONE(0, R.string.swipe_action_none, 0),
    ARCHIVE(1, R.string.archive, R.drawable.ic_archive_vector),
    DELETE(2, org.fossify.commons.R.string.delete, org.fossify.commons.R.drawable.ic_delete_vector),
    MUTE(3, R.string.mute_conversation, R.drawable.ic_bell_off_vector);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Directions are physical on purpose: the setting is phrased as the gesture the user makes, so it
 * must not mirror under RTL. Archive goes dead where the telephony provider cannot archive.
 */
fun Config.swipeAction(towardsRight: Boolean): SwipeAction {
    val action = if (towardsRight) swipeRightAction else swipeLeftAction
    return if (action == SwipeAction.ARCHIVE && !isArchiveAvailable) SwipeAction.NONE else action
}

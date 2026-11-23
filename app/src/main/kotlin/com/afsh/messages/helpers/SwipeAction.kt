package com.afsh.messages.helpers

import androidx.annotation.StringRes
import com.afsh.messages.R

/** [id] is what gets persisted, so never renumber an entry. */
enum class SwipeAction(
    val id: Int,
    @StringRes val label: Int,
) {
    NONE(0, R.string.swipe_action_none),
    ARCHIVE(1, R.string.archive),
    DELETE(2, com.afsh.commons.R.string.delete),
    MUTE(3, R.string.mute_conversation);

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

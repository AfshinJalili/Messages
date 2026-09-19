package org.fossify.messages.helpers

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.fossify.messages.R

/** [id] is what gets persisted, so never renumber an entry. */
enum class SwipeAction(
    val id: Int,
    @StringRes val label: Int,
    @ColorRes val color: Int,
    @DrawableRes val icon: Int,
) {
    NONE(0, R.string.swipe_action_none, 0, 0),
    ARCHIVE(1, R.string.archive, R.color.swipe_archive_background, R.drawable.ic_archive_vector),
    DELETE(
        2,
        org.fossify.commons.R.string.delete,
        R.color.swipe_delete_background,
        org.fossify.commons.R.drawable.ic_delete_vector
    ),
    MUTE(3, R.string.mute_conversation, R.color.swipe_mute_background, R.drawable.ic_bell_off_vector);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

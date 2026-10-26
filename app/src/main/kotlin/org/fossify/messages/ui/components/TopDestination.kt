package org.fossify.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.fossify.messages.R

/** The three top-level destinations of the primary navigation. */
enum class TopDestination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    INBOX(R.string.inbox_nav_inbox, R.drawable.ic_lucide_messages_square),
    LIBRARY(R.string.inbox_nav_library, R.drawable.ic_lucide_layers),
    SETTINGS(org.fossify.commons.R.string.settings, R.drawable.ic_lucide_settings),
}

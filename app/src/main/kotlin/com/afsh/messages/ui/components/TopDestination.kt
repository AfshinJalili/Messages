package com.afsh.messages.ui.components

import androidx.annotation.StringRes
import com.afsh.messages.R

/**
 * The three top-level destinations of the primary navigation. Each has a filled icon variant for its selected state,
 * matching Google Messages.
 */
enum class TopDestination(@StringRes val label: Int, val icon: AfshIcon) {
    INBOX(R.string.inbox_nav_inbox, AfshIcon.Conversations),
    LIBRARY(R.string.inbox_nav_library, AfshIcon.Layers),
    SETTINGS(org.fossify.commons.R.string.settings, AfshIcon.Settings),
}

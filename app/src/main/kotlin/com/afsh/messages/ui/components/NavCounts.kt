package com.afsh.messages.ui.components

import androidx.compose.runtime.Immutable

/** The count badge on each primary destination: unread spam on Library, unread messages on Inbox. */
@Immutable
data class NavCounts(val library: Int = 0, val inbox: Int = 0) {
    fun of(destination: TopDestination): Int = when (destination) {
        TopDestination.INBOX -> inbox
        TopDestination.LIBRARY -> library
        TopDestination.SETTINGS -> 0
    }
}

/** The Inbox count shows only when its setting is on; it is off by default. */
fun navCounts(unreadSpam: Int, unreadMessages: Int, inboxCountShown: Boolean) =
    NavCounts(library = unreadSpam, inbox = if (inboxCountShown) unreadMessages else 0)

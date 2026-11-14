package com.afsh.messages.helpers

import android.content.Context
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.extensions.getUnreadSpamCounts
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.components.NavCounts
import com.afsh.messages.ui.components.navCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fossify.commons.helpers.ensureBackgroundThread

/**
 * The navigation badge counts every screen shows. The inbox publishes them as it updates; other
 * pages refresh them on resume, so a badge never depends on which screen built the navigation.
 */
object NavBadges {
    private val state = MutableStateFlow(NavCounts())
    val counts: StateFlow<NavCounts> = state.asStateFlow()

    fun publish(counts: NavCounts) {
        state.value = counts
    }

    /** Recounts from Telephony and Room off the main thread. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        ensureBackgroundThread {
            runCatching {
                navCounts(
                    unreadSpam = app.getUnreadSpamCounts().values.sum(),
                    unreadMessages = app.conversationsDB.getNonArchived().unreadMessageCount(),
                    inboxCountShown = app.config.showInboxUnreadCount,
                )
            }.onSuccess(::publish)
        }
    }
}

/** Unread messages across [this]; a conversation marked unread counts as one. */
fun List<Conversation>.unreadMessageCount() = filter { !it.read }.sumOf { it.unreadCount.coerceAtLeast(1) }

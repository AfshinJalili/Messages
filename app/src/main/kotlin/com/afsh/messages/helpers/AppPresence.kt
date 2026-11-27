package com.afsh.messages.helpers

/**
 * Where the user's attention currently is.
 *
 * Pure domain state; see GLOSSARY.md ("App Presence") and ADR-0001.
 */
sealed interface AppPresence {
    data object Background : AppPresence
    data object Locked : AppPresence
    data object InApp : AppPresence
    data class ViewingConversation(val threadId: Long) : AppPresence
}

/**
 * Tracks [AppPresence] across Activity lifecycles, including wide list-detail split views where
 * the Inbox and a conversation are resumed side by side, App Lock, device lock/screen-off, and
 * Recycle Bin conversation mode.
 *
 * Thread-safe: lifecycle callbacks mutate on the main thread while receivers read [current] on
 * background threads. [onEnterConversation] runs outside the lock when presence newly transitions
 * into [AppPresence.ViewingConversation].
 */
class AppPresenceTracker(
    private val isDeviceLockedOrOff: () -> Boolean = { false },
    private val onEnterConversation: (Long) -> Unit = {},
) {
    private data class ConversationEntry(val threadId: Long, val isRecycleBin: Boolean)

    private val lock = Any()
    private val resumedScreens = LinkedHashSet<Any>()
    private val activeConversations = LinkedHashMap<Any, ConversationEntry>()
    private val activeLockScreens = LinkedHashSet<Any>()
    private var lastNotifiedConversationThreadId: Long? = null

    fun current(): AppPresence = synchronized(lock) { resolveLocked() }

    fun onScreenResumed(token: Any) {
        update { resumedScreens.add(token) }
    }

    fun onScreenPaused(token: Any) {
        update {
            resumedScreens.remove(token)
            activeConversations.remove(token)
            activeLockScreens.remove(token)
        }
    }

    fun onConversationActive(token: Any, threadId: Long, isRecycleBin: Boolean) {
        update {
            resumedScreens.add(token)
            // Re-insert so the most recently activated conversation sits last.
            activeConversations.remove(token)
            activeConversations[token] = ConversationEntry(threadId = threadId, isRecycleBin = isRecycleBin)
        }
    }

    /**
     * Updates the thread id for an already-resumed conversation (for example when a newly created
     * or scheduled thread acquires its real Telephony id). Ignored if [token] is no longer resumed
     * so asynchronous updates cannot resurrect a paused screen.
     */
    fun onConversationThreadUpdated(token: Any, threadId: Long, isRecycleBin: Boolean) {
        update {
            if (token in resumedScreens) {
                activeConversations.remove(token)
                activeConversations[token] = ConversationEntry(threadId = threadId, isRecycleBin = isRecycleBin)
            }
        }
    }

    fun onConversationInactive(token: Any) {
        update {
            activeConversations.remove(token)
        }
    }

    fun onLockScreenActive(token: Any) {
        update {
            resumedScreens.add(token)
            activeLockScreens.add(token)
        }
    }

    /**
     * Re-resolves presence once the keyguard is gone. After an unlock the conversation resumes while the
     * keyguard still reports locked, so without this the open thread would stay [AppPresence.Locked].
     */
    fun onDeviceUnlocked() {
        update {}
    }

    private inline fun update(mutation: () -> Unit) {
        val enteredThreadId: Long? = synchronized(lock) {
            mutation()
            val resolved = resolveLocked()
            val activeThreadId = (resolved as? AppPresence.ViewingConversation)?.threadId
            val entered = if (activeThreadId != null && activeThreadId != lastNotifiedConversationThreadId) {
                activeThreadId
            } else {
                null
            }
            lastNotifiedConversationThreadId = activeThreadId
            entered
        }
        if (enteredThreadId != null) {
            onEnterConversation(enteredThreadId)
        }
    }

    private fun resolveLocked(): AppPresence {
        if (resumedScreens.isEmpty()) return AppPresence.Background
        if (activeLockScreens.isNotEmpty() || isDeviceLockedOrOff()) return AppPresence.Locked
        // Wide split-view has both Inbox and Conversation resumed; the unlocked non-Recycle-Bin
        // conversation pane takes precedence.
        val active = activeConversations.entries.lastOrNull { (token, entry) ->
            token in resumedScreens && !entry.isRecycleBin && entry.threadId != 0L
        }?.value
        return if (active != null) AppPresence.ViewingConversation(active.threadId) else AppPresence.InApp
    }
}

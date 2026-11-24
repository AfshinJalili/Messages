package com.afsh.messages.activities

import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.conversationsDB
import com.afsh.messages.helpers.ConversationPreview
import com.afsh.messages.helpers.MessagingEvent
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.THREAD_NUMBER
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.affectsThread
import com.afsh.messages.models.Message
import com.afsh.messages.ui.thread.InitialScroll
import com.afsh.messages.ui.thread.landing
import kotlinx.coroutines.launch

/**
 * Brings the visit's messages to the screen: the cached thread first, then the provider's, reloads when
 * messaging events say the thread changed, older pages, jumps and search, the landing position, and marking
 * the thread read once the reader settles with the newest message on screen. [ThreadVisit.repository] does the I/O.
 */
internal class ThreadLoader(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val holder get() = visit.holder
    private val repository get() = visit.repository
    private val data get() = visit.data
    private val people get() = visit.people

    private var refreshPendingWhileStopped = false

    private val readCheck = Runnable {
        if (visit.isVisible && !activity.isDestroyed) markReadIfSeen()
    }

    // region Lifecycle

    fun started() {
        if (refreshPendingWhileStopped) {
            refreshPendingWhileStopped = false
            ensureBackgroundThread { reload() }
        }
    }

    fun resumed() {
        visit.isVisible = true
        // Registering active presence also cancels any existing tray notification for this thread
        // (unless locked or viewing Recycle Bin).
        MessagingEvents.onConversationActive(activity, visit.threadId, visit.isRecycleBin)
        activity.root.post(readCheck)
        ensureBackgroundThread {
            val conversation = activity.conversationsDB.getConversationWithThreadId(visit.threadId)
            if (conversation != null) {
                visit.conversation = conversation
                activity.runOnUiThread { visit.showFacts() }
            }
        }
    }

    fun paused() {
        repository.flushReadState(holder.newestSeen(includeScrolling = true))
        MessagingEvents.publish(MessagingEvent.ConversationsChanged(threadId = visit.threadId, reconcile = false))
        visit.isVisible = false
        MessagingEvents.onConversationInactive(activity)
        activity.root.removeCallbacks(readCheck)
    }

    fun destroyed() {
        MessagingEvents.onConversationInactive(activity)
        // Clear any pending page load completion to prevent awaits that never resume
        data.pageLoadCompletion?.complete(Unit)
        data.pageLoadCompletion = null
    }

    // endregion

    // region Loading

    /** Opens the thread: the cached messages, then the provider's; [onLoaded] runs once those are shown. */
    fun load(onLoaded: () -> Unit) {
        ensureBackgroundThread {
            visit.conversation = activity.conversationsDB.getConversationWithThreadId(visit.threadId)
        }
        loadCached { loadFromProvider(onLoaded) }
    }

    fun loadCached(callback: () -> Unit) {
        ensureBackgroundThread {
            repository.loadCached()
            resolveParticipants()
            showLoaded()

            activity.runOnUiThread {
                if (data.isEmpty && !people.isSpecialNumber) {
                    activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    holder.requestFocus()
                }
                visit.showFacts()
                showSims()
                holder.refreshComposer()
                callback()
            }
        }
    }

    fun loadFromProvider(callback: () -> Unit) {
        if (visit.conversation == null && visit.isLaunchedFromShortcut) {
            if (activity.isTaskRoot) {
                activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            activity.finish()
            return
        }
        ensureBackgroundThread {
            people.loadPrivateContacts()
            val messagesUnchanged = repository.loadFromProvider()
            val hadParticipants = people.participants.isNotEmpty()
            if (hadParticipants && messagesUnchanged && !people.hasUnnamed) {
                landUnchanged(callback)
                return@ensureBackgroundThread
            }

            resolveParticipants()
            // check if no participant came from a privately stored contact in Simple Contacts
            val replacedPrivateNames = people.applyPrivateNames(data.list)
            if (hadParticipants && messagesUnchanged && !replacedPrivateNames) {
                landUnchanged(callback)
                return@ensureBackgroundThread
            }

            val intent = activity.intent
            if (!people.fallBackTo(intent.getStringExtra(THREAD_TITLE) ?: "", intent.getStringExtra(THREAD_NUMBER))) {
                activity.toast(com.afsh.commons.R.string.unknown_error_occurred)
                activity.finish()
                return@ensureBackgroundThread
            }

            repository.cacheLoaded()
            ConversationPreview.refresh(activity, visit.threadId)
            data.providerReady = true
            showLoaded()
            activity.runOnUiThread {
                visit.showFacts()
                showSims()
                callback()
            }
        }
    }

    /** The provider changed nothing the screen shows: only the landing is left. */
    private fun landUnchanged(callback: () -> Unit) {
        data.providerReady = true
        activity.runOnUiThread {
            if (data.isEmpty) visit.publish()
            holder.land(true, ::landingTarget)
            callback()
        }
    }

    /** Blocking. Reloads the thread from the provider, as a messaging event or a send asks. */
    fun reload() {
        if (visit.isRecycleBin) return
        visit.sender.reloaded()
        if (repository.reload(people.addresses)) syncActiveThread()
        showLoaded()
        activity.runOnUiThread { showSims() }
    }

    /** Blocking. Builds the timeline and shows it with the header; the landing starts once the provider's messages are in. */
    private fun showLoaded() {
        repository.loadSpamReasons()
        val items = repository.items()
        visit.threadItems = items
        activity.runOnUiThread {
            visit.showFacts()
            // Inbound refresh preserves the viewport. Sending has its own explicit scroll.
            visit.publish(items)
            // Cached rows can be stale about read state, so the landing position waits for the provider.
            holder.land(data.providerReady || visit.isRecycleBin, ::landingTarget)
        }
    }

    private fun landingTarget(): InitialScroll? {
        val items = visit.threadItems
        if (visit.openSpam && !visit.isRecycleBin) {
            repository.markSpamRead(items.filterIsInstance<Message>().filter { data.isSpam(it) }.map { it.id })
        }
        return landing(visit.isRecycleBin, visit.openSpam, items, data::isSpam)
    }

    private fun resolveParticipants() {
        if (people.resolve(visit.threadId, data.list, activity.intent.getStringExtra(THREAD_NUMBER))) {
            activity.runOnUiThread { disableShortCodeReply() }
        }
    }

    private fun disableShortCodeReply() {
        if (people.isSpecialNumber && !visit.isRecycleBin) {
            activity.currentFocus?.clearFocus()
            activity.hideKeyboard()
            holder.setText("")
            visit.showFacts()
        }
    }

    /** Runs once for the cached thread and again after the provider refresh. */
    private fun showSims() {
        val (cards, index) = people.sendSims(data.list.lastOrNull()) ?: return
        holder.showSims(cards, index)
    }

    /** The thread moved (see [ThreadRepository.reload]); the notifications follow it. */
    fun syncActiveThread() {
        activity.runOnUiThread {
            if (visit.isVisible && !activity.isDestroyed) {
                MessagingEvents.onConversationThreadUpdated(activity, visit.threadId, visit.isRecycleBin)
            }
        }
    }

    /** Reloads on messaging events for this thread, and shows its send failures. */
    fun observeMessagingEvents() {
        activity.lifecycleScope.launch {
            MessagingEvents.events.collect { event ->
                if (!event.affectsThread(visit.threadId)) return@collect
                // Our own markReadIfSeen already updated local read state.
                if (event is MessagingEvent.ReadStateChanged && visit.isVisible && event.read && !data.hasUnread()) {
                    return@collect
                }
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    ensureBackgroundThread { reload() }
                } else {
                    refreshPendingWhileStopped = true
                }
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessagingEvents.inThreadFailures.collect { failure ->
                    if (failure.threadId == visit.threadId && failure.reason.isNotEmpty()) {
                        activity.snackbarHost.showSnackbar(failure.reason)
                    }
                }
            }
        }
    }

    // endregion

    // region Older pages, jumps and search

    fun loadOlder() = repository.loadOlder { items ->
        visit.threadItems = items
        visit.publish(items)
    }

    /**
     * Read state stays blocked until the jump has scrolled ([com.afsh.messages.ui.thread.ThreadEvent.JumpSettled]),
     * and timeline page loads pause, so a page load cannot win the race and drop the jump.
     */
    fun jumpTo(messageId: Long, isMms: Boolean? = null) {
        holder.startJump()
        repository.loadUntil(
            activity.lifecycleScope,
            messageId,
            isMms,
            onLoaded = { items ->
                if (items != null) {
                    visit.threadItems = items
                    visit.publish(items)
                }
                holder.scrollToJump(messageId, isMms)
            },
            onFailed = holder::jumpFailed,
        )
    }

    fun searchNext(query: String) {
        ensureBackgroundThread {
            // the Room cache also holds messages older than the loaded page
            val matches = repository.search(query)
            activity.runOnUiThread {
                val next = holder.stepSearch(matches)
                if (next == null) {
                    activity.toast(R.string.no_matching_messages)
                } else {
                    jumpTo(next.id, next.isMMS)
                }
            }
        }
    }

    // endregion

    // region Read state

    /** The viewport or landing changed: check whether the thread can now be marked read. */
    fun checkReadState() {
        activity.root.removeCallbacks(readCheck)
        activity.root.post(readCheck)
    }

    fun postReadCheck() {
        activity.root.post(readCheck)
    }

    private fun markReadIfSeen() = repository.markReadIfSeen(holder.newestSeen()) {
        if (!activity.isDestroyed) visit.refreshTimeline()
    }

    // endregion
}

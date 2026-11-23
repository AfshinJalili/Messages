package com.afsh.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.OPEN_THREAD_SEARCH
import com.afsh.messages.helpers.SEARCHED_MESSAGE_ID
import com.afsh.messages.helpers.SEARCHED_MESSAGE_IS_MMS
import com.afsh.messages.helpers.THREAD_TITLE
import com.afsh.messages.helpers.UndoDeletion
import com.afsh.messages.ui.AfshSystemBars
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.afshTextScale
import com.afsh.messages.ui.thread.ThreadRoute

/**
 * The conversation screen's Android shell: lifecycle, window insets, intents, permissions, activity results and
 * Back. Everything else belongs to one part of the [visit] (see [ThreadVisit]).
 */
class ThreadActivity : SimpleActivity(), UndoDeletion.UndoHost {
    private val visit by lazy { ThreadVisit(this) }
    private val holder get() = visit.holder

    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)

    @VisibleForTesting
    internal val snackbarHost = SnackbarHostState()
    @VisibleForTesting
    internal val timelineItems get() = holder.ui.items
    @VisibleForTesting
    internal val timelineSettledAtBottom get() = holder.listAtBottom && !holder.listScrolling

    internal val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            holder.back()
        }
    }

    /** The whole screen is one Compose tree; this view also posts the read-state checks. */
    internal val root by lazy { ComposeView(this) }

    internal val sendLater = registerForActivityResult(SendLaterActivity.Contract()) { millis ->
        if (millis != null) visit.composer.onSendTimeChosen(millis)
    }
    internal val editScheduled = registerForActivityResult(ScheduledMessageActivity.Contract()) { result ->
        result?.let { (messageId, decision) -> visit.composer.onScheduledDecision(messageId, decision) }
    }
    internal val dictate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        visit.composer.dictated(result.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visit.data.newOnOpen = savedInstanceState?.getLongArray(SAVED_NEW_ON_OPEN)?.toSet()
        // The camera may outlive this process; its result comes back to the recreated screen.
        savedInstanceState?.getString(SAVED_CAPTURED_IMAGE)?.let { visit.composer.capturedImageUri = it.toUri() }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        refreshAfshTheme()
        root.setContent {
            AfshTheme(dark = darkTheme, textScale = textScale) {
                AfshSystemBars(window)
                ThreadRoute(holder, snackbarHost, sendOnEnter = config.sendOnEnter)
            }
        }
        // Keeps the side padding for display cutouts; the composer pads itself for the keyboard and navigation bar.
        setupEdgeToEdge()

        if (intent.extras == null) {
            toast(com.afsh.commons.R.string.unknown_error_occurred)
            finish()
            return
        }
        intent.getStringExtra(THREAD_TITLE)?.let { holder.showTitle(it) }

        visit.loader.observeMessagingEvents()
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) open(savedInstanceState) else finish()
        }
        visit.showFacts()
    }

    /** Text and attachments from before a recreation win over the intent extras. */
    private fun open(savedInstanceState: Bundle?) {
        val restoredAttachments = savedInstanceState?.getStringArrayList(SAVED_COMPOSER_ATTACHMENTS).orEmpty().map(String::toUri)
        visit.composer.restore(savedInstanceState?.getString(SAVED_COMPOSER_TEXT), restoredAttachments)
        visit.loader.load {
            val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
            val searchedIsMms = intent.getBooleanExtra(SEARCHED_MESSAGE_IS_MMS, false)
            intent.removeExtra(SEARCHED_MESSAGE_ID)
            if (searchedMessageId != -1L) visit.loader.jumpTo(searchedMessageId, searchedIsMms)
            if (intent.getBooleanExtra(OPEN_THREAD_SEARCH, false)) {
                intent.removeExtra(OPEN_THREAD_SEARCH)
                holder.openSearch()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visit.loader.started()
    }

    override fun onResume() {
        super.onResume()
        refreshAfshTheme()
        // mute and starring can change on other screens
        visit.showFacts()
        visit.publish()
        visit.loader.resumed()
        visit.composer.loadDraft()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && visit.isVisible) visit.loader.postReadCheck()
    }

    override fun onPause() {
        super.onPause()
        visit.composer.saveDraft()
        visit.loader.paused()
    }

    override fun onStop() {
        super.onStop()
        visit.composer.saveDraft()
    }

    override fun onDestroy() {
        super.onDestroy()
        visit.loader.destroyed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_COMPOSER_TEXT, holder.text)
        // Compressed copies live in the cache; the originals are re-added and compressed again.
        outState.putStringArrayList(SAVED_COMPOSER_ATTACHMENTS, ArrayList(holder.attachments.map { it.id }))
        visit.data.newOnOpen?.let { outState.putLongArray(SAVED_NEW_ON_OPEN, it.toLongArray()) }
        visit.composer.capturedImageUri?.let { outState.putString(SAVED_CAPTURED_IMAGE, it.toString()) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        visit.composer.onResult(requestCode, resultCode, resultData)
    }

    override fun refreshAfterDeletion() = visit.deletion.refreshAfterDeletion()

    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit) = visit.deletion.showUndo(message, durationMs, onUndo)

    private fun refreshAfshTheme() {
        darkTheme = afshDark()
        textScale = afshTextScale()
    }

    private companion object {
        const val SAVED_COMPOSER_TEXT = "composer_text"
        const val SAVED_NEW_ON_OPEN = "new_on_open"
        const val SAVED_COMPOSER_ATTACHMENTS = "composer_attachments"
        const val SAVED_CAPTURED_IMAGE = "captured_image"
    }
}

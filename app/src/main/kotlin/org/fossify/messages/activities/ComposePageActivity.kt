package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fossify.commons.helpers.isQPlus
import org.fossify.messages.helpers.UndoDeletion
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.openLineDark
import org.fossify.messages.ui.openLineTextScale

/**
 * Hosts one Compose page in the Open Line theme. Pages share the pine app bar, so the status bar
 * keeps light icons; the theme and text size follow the app settings on every resume.
 *
 * Every Undo on the page, including deletion Undo from [UndoDeletion], shares [snackbarHost]. A new
 * offer settles the one it replaces, so an action is never left pending without its Undo.
 */
abstract class ComposePageActivity : SimpleActivity(), UndoDeletion.UndoHost {
    protected val snackbarHost = SnackbarHostState()
    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    private var undoJob: Job? = null
    private var undoSettle: (() -> Unit)? = null
    private var undoIsDeletion = false

    @Composable
    protected abstract fun Page()

    /** Reloads the page after a deletion commits or is undone. */
    protected open fun reload() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshTheme()
        setContentView(
            ComposeView(this).apply {
                setContent {
                    OpenLineTheme(dark = darkTheme, textScale = textScale) { Page() }
                }
            },
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onResume() {
        super.onResume()
        refreshTheme()
        if (isQPlus()) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the page commits a pending page action rather than dropping it, and its Undo goes
        // too. A pending deletion stays with UndoDeletion, which offers it again on the next screen.
        val settle = undoSettle.takeUnless { undoIsDeletion }
        undoJob?.cancel()
        undoSettle = null
        settle?.invoke()
    }

    override fun refreshAfterDeletion() = reload()

    private fun refreshTheme() {
        darkTheme = openLineDark()
        textScale = openLineTextScale()
    }

    /** Library pages sit under the Library destination of the primary navigation. */
    protected fun libraryNavigation(unreadSpam: Int = 0) =
        PageNavigation(TopDestination.LIBRARY, ::navigateTo, unreadSpam)

    /** Settings sub-pages keep the navigation with Settings active (R5-34). */
    protected fun settingsNavigation() = PageNavigation(TopDestination.SETTINGS, ::navigateTo)

    protected fun navigateTo(destination: TopDestination) {
        when (destination) {
            TopDestination.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> startActivity(MainActivity.topLevelIntent(this, destination))
        }
    }

    /**
     * Hides [threadIds] at once and deletes them with [delete] once the Undo window closes. The
     * page must leave [UndoDeletion.threads] out of what it shows.
     */
    protected fun deleteConversationsWithUndo(threadIds: Collection<Long>, delete: (Long) -> Unit) {
        val ids = threadIds.toSet() - UndoDeletion.threads
        if (ids.isEmpty()) return
        UndoDeletion.threads.addAll(ids)
        reload()
        refreshConversations()
        UndoDeletion.offer(
            this,
            undo = {
                UndoDeletion.threads.removeAll(ids)
                reload()
            },
            commit = { ids.forEach(delete) },
            completed = {
                UndoDeletion.threads.removeAll(ids)
                reload()
            },
        )
    }

    /** Offers Undo for a reversible action that runs [onTimeout] when the offer ends without Undo. */
    protected fun offerUndo(message: String, onUndo: () -> Unit, onTimeout: () -> Unit) {
        launchUndo(message, UNDO_DURATION_MS, onUndo, onTimeout, deletion = false)
    }

    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        val job = launchUndo(
            getString(message),
            durationMs.toLong(),
            onUndo,
            onTimeout = { UndoDeletion.commitNow(this) },
            deletion = true,
        )
        val settle = undoSettle
        // UndoDeletion dismisses its own snackbar when batches merge, time out or re-show; that is
        // not a replacement by another action, so the deletion must not commit early.
        return {
            job.cancel()
            if (undoSettle === settle) undoSettle = null
        }
    }

    private fun launchUndo(
        message: String,
        durationMs: Long,
        onUndo: () -> Unit,
        onTimeout: () -> Unit,
        deletion: Boolean,
    ): Job {
        undoJob?.cancel()
        undoSettle?.invoke()
        undoSettle = onTimeout
        undoIsDeletion = deletion
        return lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs) {
                snackbarHost.showSnackbar(
                    message = message,
                    actionLabel = getString(org.fossify.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            undoSettle = null
            if (result == SnackbarResult.ActionPerformed) onUndo() else onTimeout()
        }.also { undoJob = it }
    }

    private companion object {
        // The same window UndoDeletion gives a deletion.
        const val UNDO_DURATION_MS = 5000L
    }
}

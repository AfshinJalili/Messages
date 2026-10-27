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
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.openLineDark
import org.fossify.messages.ui.openLineTextScale

/**
 * Hosts one Compose page in the Open Line theme. Pages share the pine app bar, so the status bar
 * keeps light icons; the theme and text size follow the app settings on every resume. Deletion
 * Undo from [UndoDeletion] shows on the page's own snackbar host.
 */
abstract class ComposePageActivity : SimpleActivity(), UndoDeletion.UndoHost {
    protected val snackbarHost = SnackbarHostState()
    private var darkTheme by mutableStateOf(false)
    private var textScale by mutableFloatStateOf(1f)
    private var undoJob: Job? = null

    @Composable
    protected abstract fun Page()

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

    private fun refreshTheme() {
        darkTheme = openLineDark()
        textScale = openLineTextScale()
    }

    /** Library pages sit under the Library destination of the primary navigation. */
    protected fun libraryNavigation(unreadSpam: Int = 0) =
        PageNavigation(TopDestination.LIBRARY, ::navigateTo, unreadSpam)

    protected fun navigateTo(destination: TopDestination) {
        when (destination) {
            TopDestination.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> startActivity(MainActivity.topLevelIntent(this, destination))
        }
    }

    override fun showUndo(message: Int, durationMs: Int, onUndo: () -> Unit): () -> Unit {
        undoJob?.cancel()
        val job = lifecycleScope.launch {
            val result = withTimeoutOrNull(durationMs.toLong()) {
                snackbarHost.showSnackbar(
                    message = getString(message),
                    actionLabel = getString(org.fossify.commons.R.string.undo),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
        undoJob = job
        return { job.cancel() }
    }
}

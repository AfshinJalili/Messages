package com.afsh.messages.ui.components

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.afsh.messages.R
import com.afsh.messages.models.Conversation
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.afshTextScale

/**
 * Shows a Compose dialog from an activity whose content is not one Compose tree (the thread is
 * three ComposeViews in a layout; the inbox hosts its own). The dialog gets the AFSH theme and
 * is removed with its host view once [content] calls `dismiss`. Like the AlertDialogs it replaces,
 * it does not outlive recreation.
 */
fun Activity.showAfshDialog(content: @Composable (dismiss: () -> Unit) -> Unit) {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val host = ComposeView(this)
    val dismiss = { root.removeView(host) }
    host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    host.setContent {
        AfshTheme(dark = afshDark(), textScale = afshTextScale()) { content(dismiss) }
    }
    root.addView(host, ViewGroup.LayoutParams(0, 0))
}

/** A [ConfirmationDialog] from any activity; [onConfirm] runs after the dialog closes. */
fun Activity.confirm(
    title: String,
    explanation: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
) = showAfshDialog { dismiss ->
    ConfirmationDialog(
        title = title,
        explanation = explanation,
        confirmLabel = confirmLabel,
        dismissLabel = getString(org.fossify.commons.R.string.cancel),
        onConfirm = {
            dismiss()
            onConfirm()
        },
        onDismiss = dismiss,
        destructive = destructive,
    )
}

/** A [MessageDialog] from any activity: information with one way out, OK. */
fun Activity.inform(title: String, text: String) = showAfshDialog { dismiss ->
    MessageDialog(
        title = title,
        text = text,
        dismissLabel = getString(org.fossify.commons.R.string.ok),
        onDismiss = dismiss,
    )
}

/** A [ChoiceDialog] from any activity; [onChoose] runs after the dialog closes. */
fun <T> Activity.choose(title: String, choices: List<Choice<T>>, onChoose: (T) -> Unit) = showAfshDialog { dismiss ->
    ChoiceDialog(
        title = title,
        choices = choices,
        selected = null,
        dismissLabel = getString(org.fossify.commons.R.string.cancel),
        onChoose = {
            dismiss()
            onChoose(it)
        },
        onDismiss = dismiss,
    )
}

/** 39 · Rename, prefilled with the custom name if there is one; [onRename] gets the trimmed name. */
fun Activity.showRenameDialog(conversation: Conversation, onRename: (String) -> Unit) = showAfshDialog { dismiss ->
    RenameDialog(
        title = getString(R.string.rename_conversation),
        current = if (conversation.usesCustomTitle) conversation.title else "",
        placeholder = conversation.title,
        explanation = getString(R.string.rename_explanation),
        confirmLabel = getString(R.string.save_name),
        dismissLabel = getString(org.fossify.commons.R.string.cancel),
        onRename = {
            dismiss()
            onRename(it)
        },
        onDismiss = dismiss,
    )
}

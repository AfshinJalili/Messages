package com.afsh.messages.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh

/** The leading control of [PageAppBar]: back on a sub-page, ✕ to leave selection (R1-01). */
enum class UpAction(val icon: AfshIcon, @StringRes val label: Int) {
    BACK(AfshIcon.ArrowBack, org.fossify.commons.R.string.back),
    CLOSE(AfshIcon.Close, org.fossify.commons.R.string.close),
}

/** Height of a [PageAppBar] with a subtitle: the stock 64dp plus one body line. */
private val SubtitledBarHeight = 88.dp

/**
 * Component / page app bar: the header band that opens every secondary page, drawn by the stock Material 3
 * [TopAppBar]. [onBack] adds the leading target, drawn as [upAction]; [actions] sit after the title. Pass the page's
 * [scrollBehavior] (as [PageScaffold] does) so the bar follows the page's scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    upAction: UpAction = UpAction.BACK,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val header = Afsh.colors.header
    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Afsh.colors.onBandVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(upAction.icon.painter(), contentDescription = stringResource(upAction.label), modifier = Modifier.size(24.dp))
                }
            }
        },
        actions = actions,
        expandedHeight = if (subtitle != null) SubtitledBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        // The band keeps its color when the page scrolls under it.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = header.container,
            scrolledContainerColor = header.container,
            navigationIconContentColor = header.content,
            titleContentColor = header.content,
            actionIconContentColor = header.content,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/** A 48dp icon action for [PageAppBar]; its [label] is the TalkBack description. */
@Composable
fun PageAppBarAction(icon: AfshIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon.painter(), contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

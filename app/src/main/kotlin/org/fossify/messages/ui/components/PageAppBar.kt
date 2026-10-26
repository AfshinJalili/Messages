package org.fossify.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine

/** The leading control of [PageAppBar]: back on a sub-page, ✕ to leave selection (R1-01). */
enum class UpAction(@DrawableRes val icon: Int, @StringRes val label: Int) {
    BACK(R.drawable.ic_lucide_arrow_left, org.fossify.commons.R.string.back),
    CLOSE(R.drawable.ic_lucide_x, org.fossify.commons.R.string.close),
}

/**
 * Component / page app bar: the pine band that opens every secondary page. [onBack] adds the
 * 48dp leading target, drawn as [upAction]; [actions] sit after the title.
 */
@Composable
fun PageAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    upAction: UpAction = UpAction.BACK,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onPrimaryContainer),
                    ) {
                        Icon(
                            painterResource(upAction.icon),
                            contentDescription = stringResource(upAction.label),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                actions()
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OpenLine.colors.onBandVariant)
            }
        }
    }
}

/** A 48dp icon action for [PageAppBar]. */
@Composable
fun PageAppBarAction(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

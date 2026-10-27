package org.fossify.messages.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ActionHeight = 64.dp
private val ActionIconSize = 22.dp

/** One action of a [SelectionBar]. [destructive] paints it coral. */
@Immutable
data class BarAction(val label: String, val icon: Int, val destructive: Boolean = false, val onClick: () -> Unit)

/** The bar that replaces the navigation while rows are selected (41 · selection mode). */
@Composable
fun SelectionBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(8.dp),
        ) {
            actions.forEach { action -> SelectionAction(action, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SelectionAction(action: BarAction, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = ActionHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(action.icon),
            contentDescription = null,
            tint = if (action.destructive) colors.error else colors.primary,
            modifier = Modifier.size(ActionIconSize),
        )
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (action.destructive) colors.error else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

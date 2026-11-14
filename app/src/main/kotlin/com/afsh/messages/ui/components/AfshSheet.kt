package com.afsh.messages.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshCorners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val ItemMinHeight = 48.dp
private val ItemIconSize = 24.dp
private val ItemIconTileSize = 48.dp

/**
 * What a sheet's content can do with its sheet. [dismissThen] slides the sheet away first, then
 * dismisses it and runs the action, so a dialog, picker or page opened by the action never opens
 * under the sheet. When the hide is cancelled (the person drags the sheet back up) nothing runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
class AfshSheetScope internal constructor(
    private val state: SheetState,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit,
    column: ColumnScope,
) : ColumnScope by column {
    fun dismissThen(action: () -> Unit) {
        scope.launch {
            state.hide()
            onDismiss()
            action()
        }
    }
}

/**
 * Component / sheet: the one frame every bottom sheet in the app is drawn in, the Material 3
 * modal bottom sheet. A 28dp-topped surfaceContainer sheet that opens fully expanded, with the
 * stock drag handle, scrim, Back and drag-to-dismiss, and system bar and keyboard insets.
 * [content] goes in a column that scrolls when landscape, split screen or a large font leave
 * less room than it needs.
 *
 * [title] names the pane for TalkBack and tests. It is shown as a titleLarge heading unless
 * [showTitle] is false (the message sheet, whose first line is its own date line).
 * [dimBehind] false drops the scrim, for a sheet whose caller dims the page itself.
 *
 * Rows are [AfshSheetItem]. Do not use `ModalBottomSheet` or `BottomSheetScaffold` directly:
 * `tests/check_sheets.py` fails on them outside this file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfshSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    dimBehind: Boolean = true,
    content: @Composable AfshSheetScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = Afsh.spacing
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = AfshCorners.Sheet,
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurface,
        scrimColor = if (dimBehind) BottomSheetDefaults.ScrimColor else Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // On the content, not the sheet: the stock sheet names its own surface "Bottom sheet".
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .padding(bottom = spacing.lg),
        ) {
            if (showTitle) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    modifier = Modifier
                        .padding(horizontal = spacing.xxl, vertical = spacing.sm)
                        .semantics { heading() },
                )
            }
            val sheetScope = remember(state, scope, onDismiss, this) { AfshSheetScope(state, scope, onDismiss, this) }
            sheetScope.content()
        }
    }
}

/**
 * One row of a sheet, on the Material 3 list item: a leading icon, the label, and an optional
 * [supporting] line. [danger] draws the icon and label in the error colour (Delete).
 * [tile] puts the icon on a secondaryContainer tile (the attach sheet).
 */
@Composable
fun AfshSheetItem(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    danger: Boolean = false,
    tile: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = Afsh.spacing
    val iconTint = if (danger) colors.error else colors.primary
    ListItem(
        headlineContent = {
            Text(
                label,
                style = if (supporting != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (supporting != null) FontWeight.Bold else null,
            )
        },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = {
            if (tile) {
                Box(
                    Modifier
                        .size(ItemIconTileSize)
                        .clip(MaterialTheme.shapes.small)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(ItemIconSize))
                }
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(ItemIconSize))
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (danger) colors.error else colors.onSurface,
            supportingColor = colors.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ItemMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm),
    )
}

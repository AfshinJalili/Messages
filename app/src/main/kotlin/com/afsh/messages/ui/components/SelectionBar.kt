package com.afsh.messages.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.afsh.messages.R
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
private val CheckSize = 24.dp
private val TargetSize = 48.dp

/** Selection bars rise in as fast as the app's other panels (search, 250 ms) and leave a little quicker. */
const val SELECTION_ENTER_MS = 250
const val SELECTION_EXIT_MS = 200

private fun <T> enterSpec() = tween<T>(SELECTION_ENTER_MS, easing = FastOutSlowInEasing)
private fun <T> exitSpec() = tween<T>(SELECTION_EXIT_MS, easing = FastOutLinearInEasing)

/** A bar rising from the bottom edge: it grows from its top, so the content above gives way smoothly. */
val BarEnter: EnterTransition = expandVertically(enterSpec(), expandFrom = Alignment.Top) + fadeIn(enterSpec())
val BarExit: ExitTransition = shrinkVertically(exitSpec(), shrinkTowards = Alignment.Top) + fadeOut(exitSpec())

/** Shows a bottom bar with [BarEnter] and hides it with [BarExit]. */
@Composable
fun AnimatedBottomBar(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = BarEnter, exit = BarExit) { content() }
}

/** Swaps one bottom bar for another (navigation for selection): the new one rises while the old one sinks. */
@Composable
fun <T> BottomBarSwitch(bar: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = bar,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(enterSpec()) { it } + fadeIn(enterSpec())) togetherWith
                (slideOutVertically(exitSpec()) { it } + fadeOut(exitSpec())) using
                SizeTransform(clip = false) { _, _ -> enterSpec() }
        },
        label = "bottom bar",
    ) { content(it) }
}

/**
 * [value] while it is not [empty]; once it empties (the selection was cleared), the last non-empty one,
 * so a leaving bar keeps its icons until it is gone instead of blanking first.
 */
@Composable
fun <T : Any> keptWhileLeaving(value: T, empty: Boolean): T {
    val kept = remember { arrayOfNulls<Any>(1) }
    if (!empty || kept[0] == null) kept[0] = value
    @Suppress("UNCHECKED_CAST")
    return kept[0] as T
}

/**
 * One icon of a selection bar. Icon-only, like the rest of the redesign: the label is its TalkBack
 * description and its long-press tooltip. [destructive] paints it in the error colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionIconAction(icon: Int, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(TargetSize)) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The round check that slides in beside each row while selecting. Its row already says "selected"
 * to TalkBack, so it says nothing itself.
 */
@Composable
fun SelectionCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.primary else colors.surface, enterSpec<Color>(), label = "check fill")
    val ring by animateColorAsState(if (checked) colors.primary else colors.outline, enterSpec<Color>(), label = "check ring")
    Box(
        modifier
            .size(CheckSize)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = fadeIn(enterSpec()), exit = fadeOut(exitSpec())) {
            Icon(painterResource(R.drawable.ic_lucide_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

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
                .heightIn(min = BottomBarHeight)
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

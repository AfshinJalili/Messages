package com.afsh.messages.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.Avatar
import com.afsh.messages.ui.ContentFontVisualTransformation
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.components.actionLabel
import com.afsh.messages.ui.components.afshFieldColors
import com.afsh.messages.ui.components.fieldMinHeight
import com.afsh.messages.ui.components.selectionTitle
import com.afsh.messages.ui.forFieldContent
import com.afsh.messages.ui.lineHeightSp

const val THREAD_TOP_BAR_TAG = "thread_top_bar"

/** Below this window height the bar drops the subtitle and shrinks to the stock 56 dp (landscape phones). */
private const val COMPACT_HEIGHT_DP = 480
private val CompactBarHeight = 56.dp
private val BarHeight = 72.dp
private val AvatarSize = 44.dp

/** What the conversation top bar shows. Selection wins over search, as the selection bar covers the composer. */
enum class ThreadTopBarMode { CONVERSATION, SEARCH, SELECTION }

val ThreadUiState.topBarMode: ThreadTopBarMode
    get() = when {
        selecting -> ThreadTopBarMode.SELECTION
        searching -> ThreadTopBarMode.SEARCH
        else -> ThreadTopBarMode.CONVERSATION
    }

/**
 * The bar is never shorter than its design height, and grows with the title (and subtitle) it holds, so a large
 * font scale or Persian's taller line height never clips the name. [minContent] raises the floor for SEARCH
 * mode, where the field's [fieldMinHeight] exceeds the compact 56 dp bar.
 */
internal fun threadTopBarHeight(compact: Boolean, title: Dp, subtitle: Dp?, padding: Dp, minContent: Dp = 0.dp): Dp {
    val base = if (compact) CompactBarHeight else BarHeight
    val identity = maxOf(AvatarSize, title + (subtitle ?: 0.dp), minContent)
    return maxOf(base, identity + padding * 2)
}

/**
 * The conversation's top bar: a stock Material 3 small top app bar on the header color role. It shows the contact
 * (tap for details), call and the overflow menu; the in-thread search field; or, while messages are selected, ✕ and
 * the count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(state: ThreadUiState, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val header = state.header
    val mode = state.topBarMode
    val compact = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
    val subtitle = when (mode) {
        ThreadTopBarMode.SELECTION -> selectionTitle(state.selected.size)
        ThreadTopBarMode.CONVERSATION -> if (compact) "" else header.subtitle
        ThreadTopBarMode.SEARCH -> ""
    }
    val typography = MaterialTheme.typography
    val spacing = Afsh.spacing
    val height = with(LocalDensity.current) {
        threadTopBarHeight(
            compact = compact,
            title = typography.titleLarge.lineHeightSp().toDp(),
            subtitle = if (subtitle.isEmpty()) null else typography.bodySmall.lineHeightSp().toDp() + spacing.xxs,
            padding = spacing.sm,
            minContent = if (mode == ThreadTopBarMode.SEARCH) fieldMinHeight() else 0.dp,
        )
    }
    val content = Afsh.colors.header.content
    TopAppBar(
        title = {
            when (mode) {
                ThreadTopBarMode.SEARCH -> SearchField(onEvent, Modifier.fillMaxWidth().padding(end = spacing.md))
                ThreadTopBarMode.SELECTION -> Identity(header.title, subtitle)
                ThreadTopBarMode.CONVERSATION -> Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = ThreadMenuAction.DETAILS in header.actions) { onEvent(ThreadEvent.OpenDetails) }
                        .padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Avatar(header.title, header.photoUri, header.isGroup, size = AvatarSize)
                    Identity(header.title, subtitle, Modifier.weight(1f))
                }
            }
        },
        navigationIcon = {
            when (mode) {
                ThreadTopBarMode.SELECTION -> TopBarAction(AfshIcon.Close, stringResource(com.afsh.commons.R.string.close)) {
                    onEvent(ThreadEvent.ClearSelection)
                }
                ThreadTopBarMode.SEARCH -> TopBarAction(AfshIcon.ArrowBack, stringResource(com.afsh.commons.R.string.close)) {
                    onEvent(ThreadEvent.SearchClose)
                }
                ThreadTopBarMode.CONVERSATION -> TopBarAction(AfshIcon.ArrowBack, stringResource(com.afsh.commons.R.string.back)) {
                    onEvent(ThreadEvent.Back)
                }
            }
        },
        actions = {
            if (mode == ThreadTopBarMode.CONVERSATION) {
                if (header.canDial) {
                    TopBarAction(AfshIcon.Call, stringResource(com.afsh.commons.R.string.dial_number)) { onEvent(ThreadEvent.Dial) }
                }
                if (header.actions.isNotEmpty()) {
                    OverflowMenu(header.actions, onEvent)
                }
            }
        },
        expandedHeight = height,
        windowInsets = WindowInsets.statusBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Afsh.colors.header.container,
            scrolledContainerColor = Afsh.colors.header.container,
            navigationIconContentColor = content,
            titleContentColor = content,
            actionIconContentColor = content,
        ),
        modifier = modifier.testTag(THREAD_TOP_BAR_TAG),
    )
}

/** The label sits on the 48 dp button, not the 24 dp icon, so accessibility tools see one labelled target. */
@Composable
private fun TopBarAction(icon: AfshIcon, label: String, tint: Color = LocalContentColor.current, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon.painter(), contentDescription = null, tint = tint)
    }
}

@Composable
private fun Identity(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Afsh.spacing.xxs)) {
        ContentText(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Afsh.colors.onBandVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverflowMenu(actions: List<ThreadMenuAction>, onEvent: (ThreadEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TopBarAction(AfshIcon.More, stringResource(com.afsh.commons.R.string.more_options)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            actionLabel(action.label, count = 1, quantity = action.labelIsQuantity),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = { Icon(action.icon.painter(), contentDescription = null) },
                    colors = if (action.destructive) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        MenuDefaults.itemColors()
                    },
                    onClick = {
                        open = false
                        onEvent(ThreadEvent.Menu(action))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            onEvent(ThreadEvent.SearchChanged)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = fieldMinHeight())
            .focusRequester(focus)
            // Hardware keyboards and some IMEs send Enter instead of the search action.
            .onPreviewKeyEvent {
                val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                if (enter && it.type == KeyEventType.KeyUp && query.isNotBlank()) onEvent(ThreadEvent.SearchSubmit(query))
                enter
            },
        visualTransformation = ContentFontVisualTransformation,
        placeholder = {
            Text(
                stringResource(R.string.search_in_conversation),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                AfshIcon.Search.painter(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                TopBarAction(AfshIcon.ChevronUp, stringResource(R.string.thread_search_next)) {
                    onEvent(ThreadEvent.SearchSubmit(query))
                }
            }
        } else {
            null
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.forFieldContent(query),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onEvent(ThreadEvent.SearchSubmit(query)) }),
        shape = MaterialTheme.shapes.medium,
        colors = afshFieldColors(),
    )
}

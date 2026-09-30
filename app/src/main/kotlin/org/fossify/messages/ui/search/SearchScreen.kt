package org.fossify.messages.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.helpers.searchRanges
import org.fossify.messages.models.SearchResult
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.components.*
import org.fossify.messages.ui.forContent
import org.fossify.messages.ui.withContentFonts

private val PagePadding = 20.dp
private val ContentGap = 12.dp
private val SmallGap = 4.dp
private val ControlHeight = 56.dp
private val TouchTarget = 48.dp
private val ContentWidth = 640.dp
private val FocusStroke = 2.dp
private const val EXCERPT_LINES = 3

@Composable
fun SearchContent(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onFilter: (SearchFilter) -> Unit,
    onOpen: (SearchResult) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches = remember(state.matches, state.filter, state.entry) { state.visibleMatches }
    val listState = rememberLazyListState()
    LaunchedEffect(state.query, state.filter) { listState.scrollToItem(0) }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(ContentGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "filters") { SearchFilters(state, onFilter) }
        when {
            state.showingHistory -> {
                item(key = "recent-heading") { SearchHeading(stringResource(R.string.recent_searches)) }
                items(state.recent, key = { "recent:$it" }) { query ->
                    RecentSearchRow(query, onQuery)
                }
                item(key = "help") {
                    Text(
                        stringResource(R.string.search_help),
                        modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.loading && matches.isEmpty() -> item(key = "loading") {
                Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                    repeat(3) { OpenLineLoadingResult(stringResource(R.string.loading_messages)) }
                }
            }
            state.failed && matches.isEmpty() -> item(key = "error") { SearchError(onRetry) }
            matches.isEmpty() -> item(key = "empty") { SearchEmpty(state.query) }
            else -> {
                if (state.loading) {
                    item(key = "refresh") {
                        LinearProgressIndicator(Modifier.widthIn(max = ContentWidth).fillMaxWidth())
                    }
                }
                if (state.failed) {
                    item(key = "refresh-error") { SearchError(onRetry) }
                }
                item(key = "count") {
                    val conversations = matches.map { it.result.threadId }.distinct().size
                    SearchHeading(stringResource(R.string.search_result_count, matches.size, conversations))
                }
                val groups = matches.groupBy { it.person }
                groups.forEach { (people, results) ->
                    item(key = "heading:$people") {
                        SearchHeading(stringResource(if (people) R.string.search_people else R.string.messages))
                    }
                    items(results, key = { it.key }) { match -> SearchResultRow(match.result, state.query, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun SearchFilters(state: SearchUiState, onFilter: (SearchFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val filters = if (state.entry) {
        listOf(SearchFilter.ALL, SearchFilter.PEOPLE, SearchFilter.MEDIA)
    } else {
        SearchFilter.entries
    }
    Row(
        Modifier.widthIn(max = ContentWidth).fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        filters.forEach { filter ->
            val active = filter == state.filter
            val label = when {
                state.entry && filter == SearchFilter.ALL -> R.string.search_recent
                state.entry && filter == SearchFilter.MEDIA -> R.string.search_photos
                else -> filter.label
            }
            OpenLineFilterChip(stringResource(label), active, { onFilter(filter) })
        }
    }
}

@Composable
private fun SearchHeading(text: String) {
    Text(
        text,
        Modifier.widthIn(max = ContentWidth).fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchEmpty(query: String) {
    Column(
        Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        SearchHeading(stringResource(R.string.search_no_matches))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(PagePadding),
                verticalArrangement = Arrangement.spacedBy(ContentGap),
            ) {
                val title = if (query.isBlank()) {
                    stringResource(R.string.search_nothing_yet)
                } else {
                    stringResource(R.string.search_nothing_for, query)
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.search_empty_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, query: String, onOpen: (SearchResult) -> Unit) {
    Column(
        Modifier.widthIn(max = ContentWidth).fillMaxWidth()
            .clickable { onOpen(result) }
            .padding(vertical = ContentGap, horizontal = SmallGap),
        verticalArrangement = Arrangement.spacedBy(SmallGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ContentGap), verticalAlignment = Alignment.CenterVertically) {
            HighlightedText(result.title, query, Modifier.weight(1f), title = true)
            Text(
                result.date,
                Modifier.widthIn(max = DateWidth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HighlightedText(searchExcerpt(result.snippet, query), query, Modifier.fillMaxWidth())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val DateWidth = 120.dp

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    title: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val term = query.trim()
    val highlighted = remember(text, term, colors.secondaryContainer, colors.onSecondaryContainer) {
        buildAnnotatedString {
            append(text)
            if (term.isNotEmpty()) {
                searchRanges(text, term).forEach { range ->
                    addStyle(SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer,
                        fontWeight = FontWeight.Bold), range.first, range.last + 1)
                }
            }
        }
    }
    Text(
        remember(highlighted) { highlighted.withContentFonts() },
        modifier,
        style = (if (title) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium).forContent(text),
        color = colors.onSurface,
        maxLines = if (title) 1 else EXCERPT_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RecentSearchRow(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.widthIn(max = ContentWidth).fillMaxWidth().heightIn(min = ControlHeight)
            .clickable(role = Role.Button) { onQuery(query) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        Icon(
            painterResource(R.drawable.ic_ol_clock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(query, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Suppress("UnusedPrivateMember") // Invoked by Android Studio preview tooling.
@Preview(showBackground = true)
@Composable
private fun SearchEntryPreview() {
    OpenLineTheme(dark = false) {
        Column {
            SearchInput(query = "", onQuery = {}, onBack = {})
            SearchContent(SearchUiState(open = true), onQuery = {}, onFilter = {}, onOpen = {}, onRetry = {})
        }
    }
}

@Composable
private fun SearchError(onRetry: () -> Unit) {
    Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
        Text(stringResource(R.string.search_error), color = MaterialTheme.colorScheme.onSurface)
        OpenLineButton(stringResource(R.string.search_retry), onRetry, kind = OpenLineButtonKind.QUIET)
    }
}

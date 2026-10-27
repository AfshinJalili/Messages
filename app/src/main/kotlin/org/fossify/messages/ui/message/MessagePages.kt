package org.fossify.messages.ui.message

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.DetailRow
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageScaffold

private val PageContentPadding = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)
private val StatusIconSize = 16.dp

/** What the tick beside the time says. */
enum class DeliveryMark { NONE, SENT, DELIVERED }

/**
 * Everything 13 shows, worked out by the thread, which knows the participants and the SIMs. The
 * rows are label to value, in order.
 */
@Immutable
data class MessageFacts(val body: String, val time: String, val mark: DeliveryMark, val rows: List<Pair<String, String>>) {
    fun toBundle() = Bundle().apply {
        putString(BODY, body)
        putString(TIME, time)
        putString(MARK, mark.name)
        putStringArrayList(LABELS, ArrayList(rows.map { it.first }))
        putStringArrayList(VALUES, ArrayList(rows.map { it.second }))
    }

    companion object {
        private const val BODY = "body"
        private const val TIME = "time"
        private const val MARK = "mark"
        private const val LABELS = "labels"
        private const val VALUES = "values"

        fun from(bundle: Bundle) = MessageFacts(
            body = bundle.getString(BODY).orEmpty(),
            time = bundle.getString(TIME).orEmpty(),
            mark = DeliveryMark.entries.firstOrNull { it.name == bundle.getString(MARK) } ?: DeliveryMark.NONE,
            rows = bundle.getStringArrayList(LABELS).orEmpty().zip(bundle.getStringArrayList(VALUES).orEmpty()),
        )
    }
}

/** 13 · Message / details: the message as it looks in the thread, then its facts. */
@Composable
fun MessageDetailsScreen(facts: MessageFacts, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PageScaffold(
        title = stringResource(R.string.message_details),
        subtitle = stringResource(R.string.message_details_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .then(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.primaryContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (facts.body.isNotEmpty()) {
                    Text(facts.body, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
                }
                DeliveryLine(facts.time, facts.mark)
            }
            facts.rows.forEach { (label, value) -> DetailRow(label, value) }
        }
    }
}

@Composable
private fun DeliveryLine(time: String, mark: DeliveryMark) {
    val accent = OpenLine.colors.accent
    val label = when (mark) {
        DeliveryMark.NONE -> null
        DeliveryMark.SENT -> stringResource(R.string.message_sent)
        DeliveryMark.DELIVERED -> stringResource(R.string.message_delivered)
    }
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(time, label).joinToString(", ") },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.bodySmall, color = accent)
        if (mark != DeliveryMark.NONE) {
            val icon = if (mark == DeliveryMark.DELIVERED) R.drawable.ic_lucide_check_check else R.drawable.ic_lucide_check
            Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(StatusIconSize))
        }
    }
}

/**
 * 44 · Message / select text. The message is a read-only field so the selection handles work as
 * anywhere else; Copy selection copies what is selected, or the whole message when nothing is.
 */
@Composable
fun SelectTextScreen(sender: String, body: String, onBack: () -> Unit, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(body)) }
    val selection = value.selection
    val selected = if (selection.collapsed) "" else body.substring(selection.min, selection.max)
    PageScaffold(
        title = stringResource(org.fossify.commons.R.string.select_text),
        subtitle = stringResource(R.string.select_text_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .then(PageContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(sender, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(colors.primary, colors.secondaryContainer),
                ) {
                    BasicTextField(
                        value = value,
                        // Read-only: only the selection may change, never the text.
                        onValueChange = { value = it.copy(text = body, selection = it.selection.coerceIn(body.length)) },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OpenLineButton(
                stringResource(if (selected.isEmpty()) R.string.copy_whole_message else R.string.copy_selection),
                onClick = { onCopy(selected.ifEmpty { body }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun TextRange.coerceIn(length: Int) = TextRange(start.coerceIn(0, length), end.coerceIn(0, length))

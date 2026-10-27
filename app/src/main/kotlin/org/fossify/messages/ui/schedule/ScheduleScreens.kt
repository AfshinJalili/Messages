package org.fossify.messages.ui.schedule

import org.fossify.messages.ui.forUserText
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageScaffold
import org.fossify.messages.ui.components.SectionHeading
import org.fossify.messages.ui.components.TextFieldSlab
import org.fossify.messages.ui.components.upcomingDayAndTime
import java.util.Calendar
import java.util.TimeZone

private val DateChipWidth = 74.dp
private val DateChipHeight = 48.dp
private val MenuIconSize = 20.dp
private val PickerShape = RoundedCornerShape(24.dp)
private const val CHOSEN_DAYS = 14
private const val MILLIS_PER_MINUTE = 60_000L

/** Who it goes to, and on which SIM when the phone has two: "To Mina Farahani · SIM 1". */
data class ScheduleTarget(val text: String, val recipient: String)

/** The first full hour at least an hour from [now]: a sensible default send time. */
fun defaultSendTime(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = now
    add(Calendar.HOUR_OF_DAY, 2)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * 12 · Schedule / send later: the message, then the date and time it goes. With no [target] text
 * (the composer was empty) the page only picks the time.
 */
@Composable
fun SendLaterScreen(
    target: ScheduleTarget,
    initial: Long,
    onBack: () -> Unit,
    onSchedule: (Long) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Long = System::currentTimeMillis,
) {
    var millis by rememberSaveable { mutableStateOf(initial) }
    val future = millis > now()
    PageScaffold(
        title = stringResource(R.string.send_later),
        subtitle = stringResource(R.string.send_later_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (target.text.isNotEmpty()) {
                SectionHeading(stringResource(R.string.message_label))
                MessagePreview(target)
            }
            SectionHeading(stringResource(R.string.date_label))
            DateChoice(millis, now = now(), onChange = { millis = it })
            SectionHeading(stringResource(R.string.time_label))
            TimeChoice(millis, onChange = { millis = it })
            if (!future) {
                Text(
                    stringResource(R.string.must_pick_time_in_the_future),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OpenLineButton(
                stringResource(if (target.text.isNotEmpty()) R.string.schedule_message else R.string.use_this_time),
                onClick = { onSchedule(millis) },
                modifier = Modifier.fillMaxWidth(),
                enabled = future,
            )
        }
    }
}

@Composable
private fun MessagePreview(target: ScheduleTarget) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(target.text, style = MaterialTheme.typography.bodyLarge.forUserText(target.text), color = colors.onSurface)
        Text(target.recipient, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** The month, then the next two weeks as chips; Other date opens the calendar for anything later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChoice(millis: Long, now: Long, onChange: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val days = remember(now / MILLIS_PER_MINUTE) {
        (0 until CHOSEN_DAYS).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
            }
        }
    }
    val selectedIndex = days.indexOfFirst { it.sameDay(millis) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            DateFormat.format("LLLL yyyy", millis).toString(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
        LazyRow(Modifier.selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.timeInMillis / MILLIS_PER_MINUTE }) { day ->
                DateChip(DateFormat.format("EEE d", day).toString(), selected = day.sameDay(millis)) {
                    onChange(withDate(millis, day))
                }
            }
            item(key = "other") {
                val other = selectedIndex < 0
                DateChip(
                    if (other) DateFormat.format("d MMM", millis).toString() else stringResource(R.string.other_date),
                    selected = other,
                ) { calendarOpen = true }
            }
        }
    }
    if (calendarOpen) {
        val startOfToday = remember(now / MILLIS_PER_MINUTE) { days.first().startOfDayUtc() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = Calendar.getInstance().apply { timeInMillis = millis }.startOfDayUtc(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= startOfToday
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    calendarOpen = false
                    pickerState.selectedDateMillis?.let { utc ->
                        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        onChange(
                            Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
                            }.timeInMillis,
                        )
                    }
                }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { calendarOpen = false }) { Text(stringResource(org.fossify.commons.R.string.cancel)) }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = colors.surfaceContainer),
        ) {
            DatePicker(pickerState)
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .widthIn(min = DateChipWidth)
            .heightIn(min = DateChipHeight)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) OpenLine.colors.accent else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OpenLine.colors.onAccent else colors.onSurface,
        )
    }
}

/** The time in display type; a tap opens the clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChoice(millis: Long, onChange: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var clockOpen by rememberSaveable { mutableStateOf(false) }
    val is24Hour = remember(context) { context.getTimeFormat().contains('H') }
    val changeTime = stringResource(R.string.change_time)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = changeTime) { clockOpen = true }
            .padding(16.dp),
    ) {
        Text(
            DateFormat.format(context.getTimeFormat(), millis).toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.onSurface,
        )
    }
    if (clockOpen) {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val pickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = is24Hour,
        )
        Dialog(onDismissRequest = { clockOpen = false }) {
            Surface(shape = PickerShape, color = colors.surfaceContainer) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(pickerState, colors = TimePickerDefaults.colors(clockDialColor = colors.surface))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { clockOpen = false }) { Text(stringResource(org.fossify.commons.R.string.cancel)) }
                        TextButton(onClick = {
                            clockOpen = false
                            onChange(
                                calendar.apply {
                                    set(Calendar.HOUR_OF_DAY, pickerState.hour)
                                    set(Calendar.MINUTE, pickerState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis,
                            )
                        }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
                    }
                }
            }
        }
    }
}

/** What 47 can ask its thread to do. */
sealed interface ScheduledEvent {
    data object Back : ScheduledEvent
    data object ChangeTime : ScheduledEvent
    data class Save(val text: String) : ScheduledEvent
    data object SendNow : ScheduledEvent
    data object Cancel : ScheduledEvent
}

/**
 * 47 · Scheduled / edit: when it goes (tap to change), the text, Save changes, then Send now and
 * Cancel scheduled message. Save needs text and a change: a new time or new words.
 */
@Composable
fun ScheduledMessageScreen(
    target: ScheduleTarget,
    millis: Long,
    timeChanged: Boolean,
    onEvent: (ScheduledEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(target.text) }
    PageScaffold(
        title = stringResource(R.string.scheduled_message),
        subtitle = stringResource(R.string.scheduled_message_subtitle),
        onBack = { onEvent(ScheduledEvent.Back) },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val changeTime = stringResource(R.string.change_time)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.secondaryContainer)
                    .clickable(role = Role.Button, onClickLabel = changeTime) { onEvent(ScheduledEvent.ChangeTime) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    remember(millis) { upcomingDayAndTime(context, millis) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onSecondaryContainer,
                )
                Text(target.recipient, style = MaterialTheme.typography.bodySmall, color = OpenLine.colors.onSecondaryContainerVariant)
            }
            TextFieldSlab(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.message_label),
                singleLine = false,
                textStyle = MaterialTheme.typography.bodyLarge.forUserText(text),
            )
            OpenLineButton(
                stringResource(R.string.save_changes),
                onClick = { onEvent(ScheduledEvent.Save(text.trim())) },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank() && (timeChanged || text.trim() != target.text),
            )
            MenuItem(R.drawable.ic_lucide_send, stringResource(R.string.send_now)) { onEvent(ScheduledEvent.SendNow) }
            MenuItem(R.drawable.ic_lucide_x, stringResource(R.string.cancel_scheduled_message)) { onEvent(ScheduledEvent.Cancel) }
        }
    }
}

/** Component / menu item: a primary icon and a bold label, 48dp tall. */
@Composable
private fun MenuItem(icon: Int, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(MenuIconSize))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
    }
}

private fun Calendar.sameDay(millis: Long): Boolean {
    val other = Calendar.getInstance().apply { timeInMillis = millis }
    return get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

/** [millis] moved to [day], keeping its time of day. */
private fun withDate(millis: Long, day: Calendar): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
}.timeInMillis

/** The date picker works in UTC midnights. */
private fun Calendar.startOfDayUtc(): Long {
    val (year, month, day) = Triple(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month, day)
    }.timeInMillis
}

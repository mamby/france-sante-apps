package net.mamby.health.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.RecurrenceCalculator
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SampleWorkspaceBanner
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    isDemo: Boolean,
    today: LocalDate,
    notificationsBlocked: Boolean,
    now: Instant,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onStartVault: () -> Unit,
    onUpsert: (Reminder) -> Unit,
    onDelete: (UUID) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    AppScreenScaffold(
        title = stringResource(R.string.reminders_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { if (isDemo) onStartVault() else adding = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_reminder))
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            if (isDemo) item(span = { GridItemSpan(maxLineSpan) }) { SampleWorkspaceBanner(onStartVault) }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.reminders_intro)) }
            if (notificationsBlocked) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(stringResource(R.string.notifications_blocked)) {
                        Button(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.open_notification_settings))
                        }
                    }
                }
            }
            if (reminders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.no_reminders_title), stringResource(R.string.no_reminders_body))
                }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    val next = RecurrenceCalculator.nextOccurrence(reminder, now, zoneId)
                    SectionCard(reminder.title) {
                        Text(stringResource(reminder.recurrence.labelResource()))
                        Text(next?.localizedDateTime(zoneId) ?: stringResource(R.string.common_not_set))
                        Text(stringResource(if (reminder.isEnabled) R.string.status_enabled else R.string.status_disabled))
                        Button(onClick = { editing = reminder }) { Text(stringResource(R.string.common_edit)) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.reminder_delivery_notice)) }
        }
    }
    if (adding || editing != null) {
        ReminderDialog(
            existing = editing,
            today = today,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                onUpsert(it)
                adding = false
                editing = null
            },
            onDelete = editing?.let { reminder ->
                {
                    onDelete(reminder.id)
                    editing = null
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(
    existing: Reminder?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var start by remember { mutableStateOf(existing?.startsOn ?: today) }
    var time by remember { mutableStateOf(existing?.timeOfDay ?: LocalTime.of(9, 0)) }
    var recurrence by remember { mutableStateOf(existing?.recurrence ?: ReminderRecurrence.NONE) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(existing?.daysOfWeek ?: emptySet()) }
    var hasEnd by remember { mutableStateOf(existing?.endsOn != null) }
    var end by remember { mutableStateOf(existing?.endsOn ?: today.plusMonths(1)) }
    var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }
    var deleteVisible by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_reminder else R.string.edit_reminder),
        saveEnabled = title.isNotBlank() && (!hasEnd || !end.isBefore(start)),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                Reminder(
                    id = existing?.id ?: UUID.randomUUID(),
                    title = title.trim(),
                    startsOn = start,
                    timeOfDay = time,
                    recurrence = recurrence,
                    daysOfWeek = days,
                    endsOn = end.takeIf { hasEnd },
                    isEnabled = enabled,
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.reminder_title)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.reminder_notes)) }, minLines = 2)
            DateField(stringResource(R.string.reminder_start_date), start, { start = it })
            TimeField(stringResource(R.string.reminder_time), time, { time = it })
            ExposedDropdownMenuBox(recurrenceExpanded, { recurrenceExpanded = it }) {
                OutlinedTextField(
                    value = stringResource(recurrence.labelResource()),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.reminder_recurrence)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recurrenceExpanded) },
                )
                ExposedDropdownMenu(recurrenceExpanded, { recurrenceExpanded = false }) {
                    ReminderRecurrence.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(stringResource(candidate.labelResource())) },
                            onClick = {
                                recurrence = candidate
                                recurrenceExpanded = false
                            },
                        )
                    }
                }
            }
            if (recurrence == ReminderRecurrence.WEEKLY) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(stringResource(day.labelResource())) },
                        )
                    }
                }
            }
            SwitchField(stringResource(R.string.reminder_end_date), hasEnd, { hasEnd = it })
            if (hasEnd) DateField(stringResource(R.string.reminder_end_date), end, { end = it })
            SwitchField(stringResource(R.string.reminder_enabled), enabled, { enabled = it })
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteVisible = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteVisible) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_reminder_title),
            stringResource(R.string.delete_reminder_message),
            { deleteVisible = false },
            {
                deleteVisible = false
                onDelete?.invoke()
            },
        )
    }
}

private fun DayOfWeek.labelResource(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_monday
    DayOfWeek.TUESDAY -> R.string.day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday
    DayOfWeek.THURSDAY -> R.string.day_thursday
    DayOfWeek.FRIDAY -> R.string.day_friday
    DayOfWeek.SATURDAY -> R.string.day_saturday
    DayOfWeek.SUNDAY -> R.string.day_sunday
}

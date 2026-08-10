package net.mamby.health.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.Locale
import net.mamby.health.R
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.ScheduleAlert
import net.mamby.health.core.model.ScheduleCalculator
import net.mamby.health.core.model.ScheduleOccurrence
import net.mamby.health.core.model.ScheduleRecurrence
import net.mamby.health.core.model.ScheduleTiming
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun ScheduleScreen(
    schedules: List<Schedule>,
    profileNames: List<String>,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    notificationsBlocked: Boolean,
    onUpsert: (Schedule) -> Unit,
    onSelected: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    creationRequest: Long = 0,
) {
    var editorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(creationRequest) { if (creationRequest > 0) editorVisible = true }
    val entries = remember(schedules, now, zoneId) {
        schedules.map { schedule -> ScheduleListEntry(schedule, displayOccurrence(schedule, now, zoneId)) }
    }
    val upcoming = entries.filter { it.occurrence != null }
        .sortedWith(compareBy<ScheduleListEntry> { it.occurrence?.startsAt }.thenBy { it.schedule.title })
    val completed = entries.filter { it.occurrence == null }.sortedByDescending { it.schedule.updatedAt }

    AppScreenScaffold(
        title = stringResource(R.string.schedule_title),
        floatingActionButton = {
            FloatingActionButton(onClick = { editorVisible = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_schedule))
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.schedule_intro)) }
            if (notificationsBlocked && schedules.any { it.alert != null }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(stringResource(R.string.notifications_blocked)) {
                        Button(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.open_notification_settings))
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.upcoming)) }
            if (upcoming.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.no_schedules_title), stringResource(R.string.no_schedules_body))
                }
            } else {
                items(upcoming, key = { it.schedule.id }) { entry ->
                    ScheduleCard(entry, zoneId) { onSelected(entry.schedule.id.toString()) }
                }
            }
            if (completed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.completed)) }
                items(completed, key = { it.schedule.id }) { entry ->
                    ScheduleCard(entry, zoneId) { onSelected(entry.schedule.id.toString()) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.schedule_delivery_notice)) }
        }
    }
    if (editorVisible) {
        ScheduleDialog(
            existing = null,
            profileNames = profileNames,
            today = today,
            zoneId = zoneId,
            onDismiss = { editorVisible = false },
            onSave = {
                onUpsert(it)
                editorVisible = false
            },
        )
    }
}

@Composable
fun ScheduleDetailScreen(
    schedule: Schedule,
    profileNames: List<String>,
    today: LocalDate,
    zoneId: ZoneId,
    onBack: (() -> Unit)?,
    onUpsert: (Schedule) -> Unit,
    onDelete: () -> Unit,
) {
    var editorVisible by remember(schedule.id) { mutableStateOf(false) }
    var deleteVisible by remember(schedule.id) { mutableStateOf(false) }
    AppScreenScaffold(schedule.title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            PageHeader()
            SectionCard(stringResource(R.string.schedule_details)) {
                LabeledValue(stringResource(R.string.schedule_when), schedule.timing.localized(zoneId))
                LabeledValue(stringResource(R.string.schedule_recurrence), schedule.recurrence.localized())
                LabeledValue(stringResource(R.string.schedule_alert), schedule.alert.localized())
                LabeledValue(stringResource(R.string.schedule_people), schedule.people.joinToString())
                LabeledValue(stringResource(R.string.schedule_location), schedule.location.orEmpty())
                LabeledValue(stringResource(R.string.schedule_notes), schedule.notes.orEmpty())
            }
            Button(onClick = { editorVisible = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleteVisible = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editorVisible) {
        ScheduleDialog(
            existing = schedule,
            profileNames = profileNames,
            today = today,
            zoneId = zoneId,
            onDismiss = { editorVisible = false },
            onSave = {
                onUpsert(it)
                editorVisible = false
            },
        )
    }
    if (deleteVisible) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_schedule_title),
            stringResource(R.string.delete_schedule_message),
            { deleteVisible = false },
            {
                deleteVisible = false
                onDelete()
            },
        )
    }
}

@Composable
private fun ScheduleCard(entry: ScheduleListEntry, zoneId: ZoneId, onOpen: () -> Unit) {
    SectionCard(entry.schedule.title) {
        Text(entry.occurrence?.startsAt?.localizedDateTime(zoneId) ?: stringResource(R.string.completed))
        entry.schedule.people.takeIf(List<String>::isNotEmpty)?.let { Text(it.joinToString()) }
        entry.schedule.location?.let { Text(it) }
        Button(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
    }
}

private data class ScheduleListEntry(val schedule: Schedule, val occurrence: ScheduleOccurrence?)

private fun displayOccurrence(schedule: Schedule, now: Instant, zoneId: ZoneId): ScheduleOccurrence? {
    val next = ScheduleCalculator.nextOccurrence(schedule, now, zoneId)
    if (next != null) return next
    if (schedule.recurrence != ScheduleRecurrence.None) return null
    val current = when (val timing = schedule.timing) {
        is ScheduleTiming.InstantTimed -> ScheduleOccurrence(timing.startsAt, timing.endsAt)
        is ScheduleTiming.LocalTimed -> {
            val start = timing.startsOn.atTime(timing.timeOfDay).atZone(zoneId).toInstant()
            ScheduleOccurrence(start, timing.durationMinutes?.let { start.plusSeconds(it * 60) })
        }
        is ScheduleTiming.AllDay -> {
            val start = timing.startsOn.atStartOfDay(zoneId).toInstant()
            val end = (timing.endsOn ?: timing.startsOn).plusDays(1).atStartOfDay(zoneId).toInstant()
            ScheduleOccurrence(start, end)
        }
    }
    return current.takeIf { it.endsAt?.isAfter(now) == true }
}

private enum class RecurrenceKind { NONE, DAILY, WEEKLY, MONTHLY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    existing: Schedule?,
    profileNames: List<String>,
    today: LocalDate,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    val initial = existing.toEditorState(today, zoneId)
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var allDay by remember(existing?.id) { mutableStateOf(existing?.timing is ScheduleTiming.AllDay) }
    var startDate by remember(existing?.id) { mutableStateOf(initial.startDate) }
    var startTime by remember(existing?.id) { mutableStateOf(initial.startTime) }
    var hasEnd by remember(existing?.id) { mutableStateOf(initial.hasEnd) }
    var endDate by remember(existing?.id) { mutableStateOf(initial.endDate) }
    var endTime by remember(existing?.id) { mutableStateOf(initial.endTime) }
    var recurrenceKind by remember(existing?.id) { mutableStateOf(initial.recurrenceKind) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var weeklyDays by remember(existing?.id) { mutableStateOf(initial.weeklyDays) }
    var hasRepeatUntil by remember(existing?.id) { mutableStateOf(initial.repeatUntil != null) }
    var repeatUntil by remember(existing?.id) { mutableStateOf(initial.repeatUntil ?: startDate.plusMonths(1)) }
    var alertEnabled by remember(existing?.id) { mutableStateOf(existing?.alert != null) }
    var timedAlertMinutes by remember(existing?.id) {
        mutableStateOf((existing?.alert as? ScheduleAlert.Timed)?.minutesBefore ?: 10L)
    }
    var timedAlertExpanded by remember { mutableStateOf(false) }
    var allDayAlertDays by remember(existing?.id) {
        mutableStateOf((existing?.alert as? ScheduleAlert.AllDay)?.daysBefore ?: 0)
    }
    var allDayAlertTime by remember(existing?.id) {
        mutableStateOf((existing?.alert as? ScheduleAlert.AllDay)?.timeOfDay ?: LocalTime.of(9, 0))
    }
    var location by remember(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var people by remember(existing?.id) { mutableStateOf(existing?.people.orEmpty()) }
    var personInput by remember { mutableStateOf("") }
    val startLocal = LocalDateTime.of(startDate, startTime)
    val endLocal = LocalDateTime.of(endDate, endTime)
    val validEnd = !hasEnd || if (allDay) !endDate.isBefore(startDate) else endLocal.isAfter(startLocal)
    val validRecurrence = recurrenceKind != RecurrenceKind.WEEKLY || weeklyDays.isNotEmpty()
    val validRepeatUntil = !hasRepeatUntil || !repeatUntil.isBefore(startDate)
    val timedAlertOptions = (listOf(0L, 10L, 60L, 1_440L) + timedAlertMinutes).distinct().sorted()

    FormDialog(
        title = stringResource(if (existing == null) R.string.add_schedule else R.string.edit_schedule),
        saveEnabled = title.isNotBlank() && validEnd && validRecurrence && validRepeatUntil,
        onDismiss = onDismiss,
        onSave = {
            val recurrence = when (recurrenceKind) {
                RecurrenceKind.NONE -> ScheduleRecurrence.None
                RecurrenceKind.DAILY -> ScheduleRecurrence.Daily(repeatUntil.takeIf { hasRepeatUntil })
                RecurrenceKind.WEEKLY -> ScheduleRecurrence.Weekly(weeklyDays, repeatUntil.takeIf { hasRepeatUntil })
                RecurrenceKind.MONTHLY -> ScheduleRecurrence.Monthly(startDate.dayOfMonth, repeatUntil.takeIf { hasRepeatUntil })
            }
            val timing = when {
                allDay -> ScheduleTiming.AllDay(startDate, endDate.takeIf { hasEnd })
                recurrence == ScheduleRecurrence.None -> ScheduleTiming.InstantTimed(
                    startsAt = startLocal.atZone(zoneId).toInstant(),
                    endsAt = endLocal.atZone(zoneId).toInstant().takeIf { hasEnd },
                )
                else -> ScheduleTiming.LocalTimed(
                    startsOn = startDate,
                    timeOfDay = startTime,
                    durationMinutes = Duration.between(startLocal, endLocal).toMinutes().takeIf { hasEnd },
                )
            }
            onSave(
                Schedule(
                    id = existing?.id ?: UUID.randomUUID(),
                    title = title,
                    timing = timing,
                    recurrence = recurrence,
                    alert = if (!alertEnabled) null else if (allDay) {
                        ScheduleAlert.AllDay(allDayAlertDays, allDayAlertTime)
                    } else {
                        ScheduleAlert.Timed(timedAlertMinutes)
                    },
                    people = people,
                    location = location,
                    notes = notes,
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_entry_title)) })
            SwitchField(stringResource(R.string.schedule_all_day), allDay, { allDay = it })
            DateField(stringResource(R.string.schedule_start_date), startDate, { startDate = it })
            if (!allDay) TimeField(stringResource(R.string.schedule_start_time), startTime, { startTime = it })
            SwitchField(stringResource(R.string.schedule_has_end), hasEnd, { hasEnd = it })
            if (hasEnd) {
                DateField(stringResource(R.string.schedule_end_date), endDate, { endDate = it })
                if (!allDay) TimeField(stringResource(R.string.schedule_end_time), endTime, { endTime = it })
            }
            ExposedDropdownMenuBox(recurrenceExpanded, { recurrenceExpanded = it }) {
                OutlinedTextField(
                    recurrenceKind.localized(), {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.schedule_recurrence)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recurrenceExpanded) },
                )
                ExposedDropdownMenu(recurrenceExpanded, { recurrenceExpanded = false }) {
                    RecurrenceKind.entries.forEach { candidate ->
                        DropdownMenuItem({ Text(candidate.localized()) }, {
                            recurrenceKind = candidate
                            recurrenceExpanded = false
                        })
                    }
                }
            }
            if (recurrenceKind == RecurrenceKind.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in weeklyDays,
                            onClick = { weeklyDays = if (day in weeklyDays) weeklyDays - day else weeklyDays + day },
                            label = { Text(day.localized()) },
                        )
                    }
                }
            }
            if (recurrenceKind != RecurrenceKind.NONE) {
                SwitchField(stringResource(R.string.schedule_repeat_until), hasRepeatUntil, { hasRepeatUntil = it })
                if (hasRepeatUntil) DateField(stringResource(R.string.schedule_repeat_until), repeatUntil, { repeatUntil = it })
            }
            SwitchField(stringResource(R.string.schedule_alert), alertEnabled, { alertEnabled = it })
            if (alertEnabled && allDay) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    listOf(0, 1).forEach { days ->
                        FilterChip(
                            selected = allDayAlertDays == days,
                            onClick = { allDayAlertDays = days },
                            label = { Text(stringResource(if (days == 0) R.string.alert_on_day else R.string.alert_one_day_before)) },
                        )
                    }
                }
                TimeField(stringResource(R.string.schedule_alert_time), allDayAlertTime, { allDayAlertTime = it })
            } else if (alertEnabled) {
                ExposedDropdownMenuBox(timedAlertExpanded, { timedAlertExpanded = it }) {
                    OutlinedTextField(
                        timedAlertMinutes.localizedAlert(), {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        readOnly = true,
                        label = { Text(stringResource(R.string.schedule_alert)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(timedAlertExpanded) },
                    )
                    ExposedDropdownMenu(timedAlertExpanded, { timedAlertExpanded = false }) {
                        timedAlertOptions.forEach { minutes ->
                            DropdownMenuItem({ Text(minutes.localizedAlert()) }, {
                                timedAlertMinutes = minutes
                                timedAlertExpanded = false
                            })
                        }
                    }
                }
            }
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_location)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_notes)) }, minLines = 2)
            Text(stringResource(R.string.schedule_people))
            Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                OutlinedTextField(
                    personInput,
                    { personInput = it },
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.schedule_person_name)) },
                )
                Button(onClick = {
                    personInput.trim().takeIf(String::isNotEmpty)?.let { people = (people + it).deduplicatedNames() }
                    personInput = ""
                }) { Text(stringResource(R.string.common_add)) }
            }
            profileNames.deduplicatedNames()
                .filterNot { suggestion -> people.any { it.equals(suggestion, ignoreCase = true) } }
                .forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { people = (people + suggestion).deduplicatedNames() },
                    label = { Text(suggestion) },
                )
            }
            people.forEach { person ->
                FilterChip(
                    selected = true,
                    onClick = { people = people.filterNot { it.equals(person, ignoreCase = true) } },
                    label = { Text(person) },
                )
            }
        }
    }
}

private data class EditorState(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val hasEnd: Boolean,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val recurrenceKind: RecurrenceKind,
    val weeklyDays: Set<DayOfWeek>,
    val repeatUntil: LocalDate?,
)

private fun Schedule?.toEditorState(today: LocalDate, zoneId: ZoneId): EditorState {
    val defaultTime = LocalTime.of(9, 0)
    val startDate: LocalDate
    val startTime: LocalTime
    val endDate: LocalDate
    val endTime: LocalTime
    val hasEnd: Boolean
    when (val timing = this?.timing) {
        is ScheduleTiming.InstantTimed -> {
            val start = timing.startsAt.atZone(zoneId)
            val end = timing.endsAt?.atZone(zoneId)
            startDate = start.toLocalDate()
            startTime = start.toLocalTime()
            endDate = end?.toLocalDate() ?: startDate
            endTime = end?.toLocalTime() ?: startTime.plusHours(1)
            hasEnd = end != null
        }
        is ScheduleTiming.LocalTimed -> {
            startDate = timing.startsOn
            startTime = timing.timeOfDay
            val end = LocalDateTime.of(startDate, startTime).plusMinutes(timing.durationMinutes ?: 60)
            endDate = end.toLocalDate()
            endTime = end.toLocalTime()
            hasEnd = timing.durationMinutes != null
        }
        is ScheduleTiming.AllDay -> {
            startDate = timing.startsOn
            startTime = defaultTime
            endDate = timing.endsOn ?: startDate
            endTime = defaultTime.plusHours(1)
            hasEnd = timing.endsOn != null
        }
        null -> {
            startDate = today
            startTime = defaultTime
            endDate = today
            endTime = defaultTime.plusHours(1)
            hasEnd = false
        }
    }
    val recurrence = this?.recurrence ?: ScheduleRecurrence.None
    return EditorState(
        startDate,
        startTime,
        hasEnd,
        endDate,
        endTime,
        when (recurrence) {
            ScheduleRecurrence.None -> RecurrenceKind.NONE
            is ScheduleRecurrence.Daily -> RecurrenceKind.DAILY
            is ScheduleRecurrence.Weekly -> RecurrenceKind.WEEKLY
            is ScheduleRecurrence.Monthly -> RecurrenceKind.MONTHLY
        },
        (recurrence as? ScheduleRecurrence.Weekly)?.daysOfWeek ?: setOf(startDate.dayOfWeek),
        recurrence.repeatUntil,
    )
}

@Composable
private fun RecurrenceKind.localized(): String = stringResource(
    when (this) {
        RecurrenceKind.NONE -> R.string.recurrence_none
        RecurrenceKind.DAILY -> R.string.recurrence_daily
        RecurrenceKind.WEEKLY -> R.string.recurrence_weekly
        RecurrenceKind.MONTHLY -> R.string.recurrence_monthly
    },
)

@Composable
private fun ScheduleRecurrence.localized(): String = when (this) {
    ScheduleRecurrence.None -> stringResource(R.string.recurrence_none)
    is ScheduleRecurrence.Daily -> stringResource(R.string.recurrence_daily)
    is ScheduleRecurrence.Weekly -> stringResource(R.string.recurrence_weekly)
    is ScheduleRecurrence.Monthly -> stringResource(R.string.recurrence_monthly)
}

@Composable
private fun ScheduleAlert?.localized(): String = when (this) {
    null -> stringResource(R.string.common_not_set)
    is ScheduleAlert.Timed -> minutesBefore.localizedAlert()
    is ScheduleAlert.AllDay -> stringResource(if (daysBefore == 0) R.string.alert_on_day else R.string.alert_one_day_before) +
        " · " + timeOfDay.localizedTime()
}

@Composable
private fun Long.localizedAlert(): String = when (this) {
    0L -> stringResource(R.string.alert_at_start)
    10L -> stringResource(R.string.alert_ten_minutes_before)
    60L -> stringResource(R.string.alert_one_hour_before)
    1_440L -> stringResource(R.string.alert_one_day_before)
    else -> stringResource(R.string.minutes_before, this)
}

@Composable
private fun DayOfWeek.localized(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.day_monday
        DayOfWeek.TUESDAY -> R.string.day_tuesday
        DayOfWeek.WEDNESDAY -> R.string.day_wednesday
        DayOfWeek.THURSDAY -> R.string.day_thursday
        DayOfWeek.FRIDAY -> R.string.day_friday
        DayOfWeek.SATURDAY -> R.string.day_saturday
        DayOfWeek.SUNDAY -> R.string.day_sunday
    },
)

@Composable
private fun ScheduleTiming.localized(zoneId: ZoneId): String = when (this) {
    is ScheduleTiming.InstantTimed -> startsAt.localizedDateTime(zoneId)
    is ScheduleTiming.LocalTimed -> "${startsOn.localizedDate()} · ${timeOfDay.localizedTime()}"
    is ScheduleTiming.AllDay -> if (endsOn == null) {
        startsOn.localizedDate()
    } else {
        "${startsOn.localizedDate()} – ${endsOn.localizedDate()}"
    }
}

private fun List<String>.deduplicatedNames(): List<String> {
    val seen = mutableSetOf<String>()
    return map(String::trim).filter(String::isNotEmpty).filter { seen.add(it.lowercase(Locale.ROOT)) }
}

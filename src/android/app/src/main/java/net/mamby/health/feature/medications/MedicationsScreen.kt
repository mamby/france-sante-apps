package net.mamby.health.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.feature.ProfileOwned
import net.mamby.health.feature.ownedItems
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.CareDirectoryPicker
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.ProfilePickerField
import net.mamby.health.ui.components.RemovableInputChip
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MedicationsScreen(
    records: List<ProfileRecord>,
    today: LocalDate,
    onAddProfile: (String, (UUID) -> Unit) -> Unit,
    onUpsert: (UUID, Medication) -> Unit,
    onSelected: (UUID, String) -> Unit,
    creationRequest: Long = 0,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var editorProfileId by remember { mutableStateOf<UUID?>(null) }
    fun startCreation() {
        editorProfileId = filterProfileId ?: records.singleOrNull()?.profile?.id
        editorVisible = true
    }
    LaunchedEffect(creationRequest) { if (creationRequest > 0) startCreation() }
    val filteredRecords = filterProfileId?.let { id -> records.filter { it.profile.id == id } } ?: records
    val medications = remember(filteredRecords) {
        filteredRecords.ownedItems(ProfileRecord::medications).sortedWith(
            compareByDescending<ProfileOwned<Medication>> { it.value.isActive }
                .thenBy { it.value.name }
                .thenBy { it.profileId },
        )
    }
    AppScreenScaffold(
        title = stringResource(R.string.medications_title),
        floatingActionButton = {
            FloatingActionButton(onClick = ::startCreation) {
                Icon(painterResource(R.drawable.ic_lucide_plus), stringResource(R.string.add_medication))
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileFilterChip(records, filterProfileId, { filterProfileId = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.medications_intro)) }
            if (medications.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_medications_title),
                        stringResource(R.string.no_medications_body),
                    )
                }
            } else {
                items(
                    items = medications,
                    key = { "${it.profileId}:${it.value.id}" },
                ) { owned ->
                    val medication = owned.value
                    SectionCard(medication.name) {
                        if (filterProfileId == null && records.size > 1) ProfileMarker(owned.profile)
                        Text(medication.dose)
                        Text(stringResource(if (medication.isActive) R.string.status_active else R.string.status_inactive))
                        Text(medication.instructions)
                        Button(onClick = { onSelected(owned.profileId, medication.id.toString()) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
    if (editorVisible) {
        val owner = records.firstOrNull { it.profile.id == editorProfileId }
        MedicationDialog(
            existing = null,
            directory = owner?.careDirectory.orEmpty(),
            today = today,
            ownerSelected = owner != null,
            profilePicker = {
                ProfilePickerField(records, editorProfileId, { editorProfileId = it }, onAddProfile)
            },
            onDismiss = { editorVisible = false },
            onSave = {
                onUpsert(requireNotNull(editorProfileId), it)
                editorVisible = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDialog(
    existing: Medication?,
    directory: List<CareDirectoryEntry>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    ownerSelected: Boolean = true,
    profilePicker: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var dose by remember { mutableStateOf(existing?.dose.orEmpty()) }
    var instructions by remember { mutableStateOf(existing?.instructions.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var prescriberEntryId by remember { mutableStateOf(existing?.prescriberEntryId) }
    var pharmacyEntryId by remember { mutableStateOf(existing?.pharmacyEntryId) }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }
    var remindersEnabled by remember { mutableStateOf(existing?.remindersEnabled ?: false) }
    var recurrence by remember { mutableStateOf(existing?.schedule?.recurrence ?: ReminderRecurrence.NONE) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var reminderTimes by remember { mutableStateOf(existing?.schedule?.reminderTimes ?: emptyList()) }
    var pendingTime by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var days by remember { mutableStateOf(existing?.schedule?.daysOfWeek ?: emptySet()) }
    var hasStart by remember { mutableStateOf(existing?.schedule?.startsOn != null) }
    var start by remember { mutableStateOf(existing?.schedule?.startsOn ?: today) }
    var hasEnd by remember { mutableStateOf(existing?.schedule?.endsOn != null) }
    var end by remember { mutableStateOf(existing?.schedule?.endsOn ?: today.plusMonths(1)) }
    LaunchedEffect(directory) {
        prescriberEntryId = prescriberEntryId?.takeIf { id -> directory.any { it.id == id } }
        pharmacyEntryId = pharmacyEntryId?.takeIf { id -> directory.any { it.id == id } }
    }

    FormDialog(
        title = stringResource(if (existing == null) R.string.add_medication else R.string.edit_medication),
        saveEnabled = ownerSelected && name.isNotBlank() && dose.isNotBlank() && instructions.isNotBlank() &&
            (!remindersEnabled || reminderTimes.isNotEmpty()),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                Medication(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = name.trim(),
                    dose = dose.trim(),
                    instructions = instructions.trim(),
                    schedule = MedicationSchedule(
                        recurrence = recurrence,
                        reminderTimes = reminderTimes.sorted(),
                        daysOfWeek = days,
                        startsOn = start.takeIf { hasStart },
                        endsOn = end.takeIf { hasEnd },
                    ),
                    isActive = active,
                    remindersEnabled = remindersEnabled,
                    prescriberEntryId = prescriberEntryId,
                    pharmacyEntryId = pharmacyEntryId,
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            profilePicker?.invoke()
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.medication_name)) })
            OutlinedTextField(dose, { dose = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.medication_dose)) })
            OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.medication_instructions)) }, minLines = 2)
            CareDirectoryPicker(
                entries = directory.filter { it.kind == CareDirectoryKind.DOCTOR || it.kind == CareDirectoryKind.OTHER },
                selectedId = prescriberEntryId,
                onSelected = { prescriberEntryId = it },
                label = stringResource(R.string.medication_prescriber_directory),
            )
            CareDirectoryPicker(
                entries = directory.filter { it.kind == CareDirectoryKind.PHARMACY },
                selectedId = pharmacyEntryId,
                onSelected = { pharmacyEntryId = it },
                label = stringResource(R.string.medication_pharmacy_directory),
            )
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.medication_notes)) }, minLines = 2)
            SwitchField(stringResource(R.string.medication_active), active, { active = it })
            SwitchField(stringResource(R.string.medication_reminders), remindersEnabled, { remindersEnabled = it })
            if (remindersEnabled) {
                ExposedDropdownMenuBox(recurrenceExpanded, { recurrenceExpanded = it }) {
                    OutlinedTextField(
                        value = stringResource(recurrence.labelResource()),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        readOnly = true,
                        label = { Text(stringResource(R.string.medication_recurrence)) },
                        trailingIcon = { DropdownTrailingIcon(recurrenceExpanded) },
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
                Text(stringResource(R.string.medication_times))
                reminderTimes.forEach { time ->
                    RemovableInputChip(
                        label = time.localizedTime(),
                        onRemove = { reminderTimes = reminderTimes - time },
                    )
                }
                TimeField(stringResource(R.string.add_reminder_time), pendingTime, { pendingTime = it })
                Button(
                    onClick = {
                        if (pendingTime !in reminderTimes) reminderTimes = reminderTimes + pendingTime
                    },
                ) { Text(stringResource(R.string.add_reminder_time)) }
                if (recurrence == ReminderRecurrence.WEEKLY) {
                    WeekdaySelector(days) { days = it }
                }
                SwitchField(stringResource(R.string.medication_start_date), hasStart, { hasStart = it })
                if (hasStart) DateField(stringResource(R.string.medication_start_date), start, { start = it })
                SwitchField(stringResource(R.string.medication_end_date), hasEnd, { hasEnd = it })
                if (hasEnd) DateField(stringResource(R.string.medication_end_date), end, { end = it })
            }
        }
    }
}

@Composable
private fun WeekdaySelector(
    selected: Set<DayOfWeek>,
    onSelectedChange: (Set<DayOfWeek>) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = {
                    onSelectedChange(if (day in selected) selected - day else selected + day)
                },
                label = { Text(stringResource(day.labelResource())) },
            )
        }
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

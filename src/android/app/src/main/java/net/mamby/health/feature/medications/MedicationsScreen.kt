package net.mamby.health.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.feature.ProfileOwned
import net.mamby.health.feature.ownedItems
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FloatingAddButton
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.EditorFieldPair
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.RemovableInputChip
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MedicationsScreen(
    records: List<ProfileRecord>,
    onAdd: (UUID?) -> Unit,
    onSelected: (UUID, String) -> Unit,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
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
            FloatingAddButton(
                label = stringResource(R.string.add_medication),
                onClick = { onAdd(filterProfileId ?: records.singleOrNull()?.profile?.id) },
            )
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
}

data class MedicationDraft(
    val profileId: UUID,
    val id: UUID,
    val name: String,
    val dose: String,
    val instructions: String,
    val notes: String,
    val active: Boolean,
    val remindersEnabled: Boolean,
    val recurrence: ReminderRecurrence,
    val reminderTimes: List<LocalTime>,
    val pendingTime: LocalTime,
    val days: Set<DayOfWeek>,
    val hasStart: Boolean,
    val start: LocalDate,
    val hasEnd: Boolean,
    val end: LocalDate,
    val updatedAt: Instant,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationEditorScreen(
    records: List<ProfileRecord>,
    existingOwner: ProfileRecord?,
    existing: Medication?,
    initialProfileId: UUID,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (UUID, Medication, (Boolean) -> Unit) -> Unit,
) {
    val editorState = rememberEditorState {
        existing.toDraft(initialProfileId, today)
    }
    val draft = editorState.value
    val selectedOwner = existingOwner?.takeIf { it.profile.id == draft.profileId }
        ?: records.firstOrNull { it.profile.id == draft.profileId }
    var recurrenceExpanded by remember { mutableStateOf(false) }

    AppEditorScaffold(
        title = stringResource(if (existing == null) R.string.new_medication else R.string.edit_medication),
        isDirty = editorState.isDirty,
        saveEnabled = selectedOwner != null && draft.name.isNotBlank() && draft.dose.isNotBlank() &&
            draft.instructions.isNotBlank() && (!draft.remindersEnabled || draft.reminderTimes.isNotEmpty()),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(draft.profileId, draft.toMedication()) { saved ->
                editorState.isSaving = false
                if (saved) onCancel()
            }
        },
    ) {
        selectedOwner?.let { ProfileOwnerHeader(it.profile) }
        EditorFieldPair(
            first = { modifier ->
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { editorState.value = draft.copy(name = it) },
                    modifier = modifier,
                    label = { Text(stringResource(R.string.medication_name)) },
                    singleLine = true,
                )
            },
            second = { modifier ->
                OutlinedTextField(
                    value = draft.dose,
                    onValueChange = { editorState.value = draft.copy(dose = it) },
                    modifier = modifier,
                    label = { Text(stringResource(R.string.medication_dose)) },
                    singleLine = true,
                )
            },
        )
        OutlinedTextField(
            value = draft.instructions,
            onValueChange = { editorState.value = draft.copy(instructions = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.medication_instructions)) },
            minLines = 2,
        )
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { editorState.value = draft.copy(notes = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.medication_notes)) },
            minLines = 2,
        )
        SwitchField(
            stringResource(R.string.medication_active),
            draft.active,
            { editorState.value = draft.copy(active = it) },
        )
        EditorSection(stringResource(R.string.medication_schedule)) {
            SwitchField(
                stringResource(R.string.medication_reminders),
                draft.remindersEnabled,
                { editorState.value = draft.copy(remindersEnabled = it) },
            )
            if (draft.remindersEnabled) {
                ExposedDropdownMenuBox(recurrenceExpanded, { recurrenceExpanded = it }) {
                    OutlinedTextField(
                        value = stringResource(draft.recurrence.labelResource()),
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
                                    editorState.value = draft.copy(recurrence = candidate)
                                    recurrenceExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.medication_times))
                draft.reminderTimes.forEach { time ->
                    RemovableInputChip(
                        label = time.localizedTime(),
                        onRemove = {
                            editorState.value = draft.copy(reminderTimes = draft.reminderTimes - time)
                        },
                    )
                }
                TimeField(
                    stringResource(R.string.add_reminder_time),
                    draft.pendingTime,
                    { editorState.value = draft.copy(pendingTime = it) },
                )
                Button(
                    onClick = {
                        if (draft.pendingTime !in draft.reminderTimes) {
                            editorState.value = draft.copy(reminderTimes = draft.reminderTimes + draft.pendingTime)
                        }
                    },
                ) { Text(stringResource(R.string.add_reminder_time)) }
                if (draft.recurrence == ReminderRecurrence.WEEKLY) {
                    WeekdaySelector(draft.days) { editorState.value = draft.copy(days = it) }
                }
                EditorFieldPair(
                    first = { modifier ->
                        Column(
                            modifier = modifier,
                            verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                        ) {
                            SwitchField(
                                stringResource(R.string.medication_start_date),
                                draft.hasStart,
                                { editorState.value = draft.copy(hasStart = it) },
                            )
                            if (draft.hasStart) {
                                DateField(
                                    stringResource(R.string.medication_start_date),
                                    draft.start,
                                    { editorState.value = draft.copy(start = it) },
                                )
                            }
                        }
                    },
                    second = { modifier ->
                        Column(
                            modifier = modifier,
                            verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                        ) {
                            SwitchField(
                                stringResource(R.string.medication_end_date),
                                draft.hasEnd,
                                { editorState.value = draft.copy(hasEnd = it) },
                            )
                            if (draft.hasEnd) {
                                DateField(
                                    stringResource(R.string.medication_end_date),
                                    draft.end,
                                    { editorState.value = draft.copy(end = it) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekdaySelector(
    selected: Set<DayOfWeek>,
    onSelectedChange: (Set<DayOfWeek>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
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

private fun Medication?.toDraft(profileId: UUID, today: LocalDate): MedicationDraft = MedicationDraft(
    profileId = profileId,
    id = this?.id ?: UUID.randomUUID(),
    name = this?.name.orEmpty(),
    dose = this?.dose.orEmpty(),
    instructions = this?.instructions.orEmpty(),
    notes = this?.notes.orEmpty(),
    active = this?.isActive ?: true,
    remindersEnabled = this?.remindersEnabled ?: false,
    recurrence = this?.schedule?.recurrence ?: ReminderRecurrence.NONE,
    reminderTimes = this?.schedule?.reminderTimes ?: emptyList(),
    pendingTime = LocalTime.of(8, 0),
    days = this?.schedule?.daysOfWeek ?: emptySet(),
    hasStart = this?.schedule?.startsOn != null,
    start = this?.schedule?.startsOn ?: today,
    hasEnd = this?.schedule?.endsOn != null,
    end = this?.schedule?.endsOn ?: today.plusMonths(1),
    updatedAt = this?.updatedAt ?: Instant.EPOCH,
)

private fun MedicationDraft.toMedication(): Medication = Medication(
    id = id,
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
    notes = notes.trim().ifBlank { null },
    updatedAt = updatedAt,
)

private fun DayOfWeek.labelResource(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_monday
    DayOfWeek.TUESDAY -> R.string.day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday
    DayOfWeek.THURSDAY -> R.string.day_thursday
    DayOfWeek.FRIDAY -> R.string.day_friday
    DayOfWeek.SATURDAY -> R.string.day_saturday
    DayOfWeek.SUNDAY -> R.string.day_sunday
}

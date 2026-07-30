package net.mamby.health.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SampleWorkspaceBanner
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun AppointmentsScreen(
    appointments: List<Appointment>,
    documents: List<MedicalDocument>,
    isDemo: Boolean,
    zoneId: ZoneId,
    now: Instant,
    onStartVault: () -> Unit,
    onSettings: () -> Unit,
    onUpsert: (Appointment) -> Unit,
    onSelected: (String) -> Unit,
) {
    var editorVisible by remember { mutableStateOf(false) }
    val upcoming = appointments.filter { it.startsAt.isAfter(now) }.sortedBy { it.startsAt }
    val past = appointments.filterNot { it.startsAt.isAfter(now) }.sortedByDescending { it.startsAt }
    AppScreenScaffold(
        title = stringResource(R.string.appointments_title),
        onSettings = onSettings,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isDemo) onStartVault() else editorVisible = true
            }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_appointment))
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
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.appointments_intro)) }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.upcoming)) }
            if (upcoming.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.no_appointments_title), stringResource(R.string.no_appointments_body))
                }
            } else {
                items(upcoming, key = { it.id }) { appointment ->
                    AppointmentCard(appointment, zoneId) { onSelected(appointment.id.toString()) }
                }
            }
            if (past.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.past)) }
                items(past, key = { it.id }) { appointment ->
                    AppointmentCard(appointment, zoneId) { onSelected(appointment.id.toString()) }
                }
            }
        }
    }
    if (editorVisible) {
        AppointmentDialog(
            existing = null,
            documents = documents,
            zoneId = zoneId,
            today = now.atZone(zoneId).toLocalDate(),
            onDismiss = { editorVisible = false },
            onSave = {
                onUpsert(it)
                editorVisible = false
            },
        )
    }
}

@Composable
private fun AppointmentCard(appointment: Appointment, zoneId: ZoneId, onOpen: () -> Unit) {
    SectionCard(appointment.title) {
        Text(appointment.startsAt.localizedDateTime(zoneId))
        Text(appointment.clinician)
        Text(appointment.location)
        Button(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
    }
}

@Composable
fun AppointmentDialog(
    existing: Appointment?,
    documents: List<MedicalDocument>,
    zoneId: ZoneId,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
) {
    val existingDateTime = existing?.startsAt?.atZone(zoneId)
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var clinician by remember { mutableStateOf(existing?.clinician.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var date by remember { mutableStateOf(existingDateTime?.toLocalDate() ?: today) }
    var time by remember { mutableStateOf(existingDateTime?.toLocalTime() ?: LocalTime.of(9, 0)) }
    var reminderEnabled by remember { mutableStateOf(existing?.reminderLeadMinutes != null) }
    var reminderLead by remember { mutableStateOf((existing?.reminderLeadMinutes ?: 60).toString()) }
    var relatedDocuments by remember { mutableStateOf(existing?.relatedDocumentIds?.toSet() ?: emptySet()) }
    val reminderValue = reminderLead.toLongOrNull()
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_appointment else R.string.edit_appointment),
        saveEnabled = title.isNotBlank() && clinician.isNotBlank() && location.isNotBlank() &&
            (!reminderEnabled || reminderValue != null),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                Appointment(
                    id = existing?.id ?: UUID.randomUUID(),
                    title = title.trim(),
                    clinician = clinician.trim(),
                    location = location.trim(),
                    startsAt = date.atTime(time).atZone(zoneId).toInstant(),
                    relatedDocumentIds = relatedDocuments.toList(),
                    notes = notes.trim().ifBlank { null },
                    reminderLeadMinutes = reminderValue.takeIf { reminderEnabled },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.appointment_title)) })
            OutlinedTextField(clinician, { clinician = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.appointment_clinician)) })
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.appointment_location)) })
            DateField(stringResource(R.string.appointment_date), date, { date = it })
            TimeField(stringResource(R.string.appointment_time), time, { time = it })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.appointment_notes)) }, minLines = 2)
            SwitchField(stringResource(R.string.appointment_reminder), reminderEnabled, { reminderEnabled = it })
            if (reminderEnabled) {
                OutlinedTextField(
                    value = reminderLead,
                    onValueChange = { reminderLead = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.appointment_reminder_lead)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(stringResource(R.string.appointment_related_documents))
            if (documents.isEmpty()) Text(stringResource(R.string.no_related_documents))
            documents.forEach { document ->
                FilterChip(
                    selected = document.id in relatedDocuments,
                    onClick = {
                        relatedDocuments = if (document.id in relatedDocuments) {
                            relatedDocuments - document.id
                        } else {
                            relatedDocuments + document.id
                        }
                    },
                    label = { Text(document.title) },
                )
            }
        }
    }
}

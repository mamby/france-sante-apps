package net.mamby.health.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.ZoneId
import net.mamby.health.R
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun AppointmentDetailScreen(
    appointment: Appointment,
    documents: List<MedicalDocument>,
    zoneId: ZoneId,
    today: LocalDate,
    onBack: () -> Unit,
    onUpsert: (Appointment) -> Unit,
    onDelete: () -> Unit,
    onDocumentSelected: (String) -> Unit,
) {
    var editorVisible by remember { mutableStateOf(false) }
    var deleteVisible by remember { mutableStateOf(false) }
    val related = documents.filter { it.id in appointment.relatedDocumentIds }
    AppScreenScaffold(appointment.title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            SectionCard(stringResource(R.string.appointments_title)) {
                LabeledValue(stringResource(R.string.appointment_clinician), appointment.clinician)
                LabeledValue(stringResource(R.string.appointment_location), appointment.location)
                LabeledValue(stringResource(R.string.appointment_date), appointment.startsAt.localizedDateTime(zoneId))
                LabeledValue(stringResource(R.string.appointment_notes), appointment.notes.orEmpty())
                LabeledValue(
                    stringResource(R.string.appointment_reminder),
                    appointment.reminderLeadMinutes?.let { stringResource(R.string.minutes_before, it) }.orEmpty(),
                )
            }
            SectionCard(stringResource(R.string.appointment_related_documents)) {
                if (related.isEmpty()) Text(stringResource(R.string.no_related_documents))
                related.forEach { document ->
                    Button(onClick = { onDocumentSelected(document.id.toString()) }) { Text(document.title) }
                }
            }
            Button(onClick = { editorVisible = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleteVisible = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editorVisible) {
        AppointmentDialog(
            existing = appointment,
            documents = documents,
            zoneId = zoneId,
            today = today,
            onDismiss = { editorVisible = false },
            onSave = {
                onUpsert(it)
                editorVisible = false
            },
        )
    }
    if (deleteVisible) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_appointment_title),
            stringResource(R.string.delete_appointment_message),
            { deleteVisible = false },
            {
                deleteVisible = false
                onDelete()
            },
        )
    }
}

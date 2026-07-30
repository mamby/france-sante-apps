package net.mamby.health.feature.medications

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
import net.mamby.health.R
import net.mamby.health.core.model.Medication
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MedicationDetailScreen(
    medication: Medication,
    today: LocalDate,
    onBack: () -> Unit,
    onUpsert: (Medication) -> Unit,
    onDelete: () -> Unit,
) {
    var editorVisible by remember { mutableStateOf(false) }
    var deleteVisible by remember { mutableStateOf(false) }
    AppScreenScaffold(medication.name, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            SectionCard(stringResource(R.string.medication_schedule)) {
                LabeledValue(stringResource(R.string.medication_dose), medication.dose)
                LabeledValue(stringResource(R.string.medication_instructions), medication.instructions)
                LabeledValue(
                    stringResource(R.string.medication_active),
                    stringResource(if (medication.isActive) R.string.status_active else R.string.status_inactive),
                )
                LabeledValue(stringResource(R.string.medication_recurrence), stringResource(medication.schedule.recurrence.labelResource()))
                LabeledValue(stringResource(R.string.medication_times), medication.schedule.reminderTimes.joinToString { it.localizedTime() })
                LabeledValue(stringResource(R.string.medication_notes), medication.notes.orEmpty())
            }
            Button(onClick = { editorVisible = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleteVisible = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editorVisible) {
        MedicationDialog(
            existing = medication,
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
            stringResource(R.string.delete_medication_title),
            stringResource(R.string.delete_medication_message),
            { deleteVisible = false },
            {
                deleteVisible = false
                onDelete()
            },
        )
    }
}

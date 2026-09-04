package net.mamby.health.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.HealthProfile
import net.mamby.androidkit.compose.layout.AndroidKitPage
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.detailTitleBarActions
import net.mamby.health.ui.components.DetailSection
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedTimes
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MedicationDetailScreen(
    medication: Medication,
    profile: HealthProfile,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var deleteVisible by remember(profile.id) { mutableStateOf(false) }
    AndroidKitPage(
        title = medication.name,
        onBack = onBack,
        actions = detailTitleBarActions(
            onEdit = onEdit,
            onDelete = { deleteVisible = true },
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            ProfileOwnerHeader(profile)
            DetailSection(stringResource(R.string.medication_schedule)) {
                LabeledValue(stringResource(R.string.medication_dose), medication.dose)
                LabeledValue(stringResource(R.string.medication_instructions), medication.instructions)
                LabeledValue(
                    stringResource(R.string.medication_active),
                    stringResource(if (medication.isActive) R.string.status_active else R.string.status_inactive),
                )
                LabeledValue(stringResource(R.string.medication_recurrence), stringResource(medication.schedule.recurrence.labelResource()))
                LabeledValue(stringResource(R.string.medication_times), medication.schedule.reminderTimes.localizedTimes())
                LabeledValue(stringResource(R.string.medication_notes), medication.notes.orEmpty())
            }
        }
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

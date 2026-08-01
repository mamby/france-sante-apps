package net.mamby.health.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.Vaccination
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

@Composable
fun SummaryScreen(
    profile: HealthProfile,
    vaccinations: List<Vaccination>,
    today: LocalDate,
    onProfileClick: () -> Unit,
    onSettings: () -> Unit,
    onUpdateProfile: (HealthProfile) -> Unit,
    onUpsertVaccination: (Vaccination) -> Unit,
    onDeleteVaccination: (UUID) -> Unit,
    creationRequest: Long = 0,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
) {
    var profileEditorVisible by remember(profile.id) { mutableStateOf(false) }
    var contactEditor by remember(profile.id) { mutableStateOf<EmergencyContact?>(null) }
    var addingContact by remember(profile.id) { mutableStateOf(false) }
    var vaccinationEditor by remember(profile.id) { mutableStateOf<Vaccination?>(null) }
    var addingVaccination by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(creationRequest) {
        if (creationRequest > 0) profileEditorVisible = true
    }

    AppScreenScaffold(
        title = stringResource(R.string.health_records_title),
        onSettings = onSettings,
        profile = profile,
        onProfileClick = onProfileClick,
        floatingActionButton = {
            FloatingActionButton(onClick = { addingVaccination = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_vaccination))
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        text = { Text(stringResource(R.string.health_info_tab)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = { Text(stringResource(R.string.documents_tab)) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard(stringResource(R.string.summary_title)) {
                    LabeledValue(stringResource(R.string.display_name), profile.displayName)
                    LabeledValue(stringResource(R.string.blood_type), profile.bloodType.orEmpty())
                    LabeledValue(stringResource(R.string.allergies), profile.allergies.joinToString())
                    LabeledValue(stringResource(R.string.chronic_conditions), profile.chronicConditions.joinToString())
                    LabeledValue(stringResource(R.string.surgeries), profile.surgeries.joinToString())
                    Button(onClick = { profileEditorVisible = true }) {
                        Text(stringResource(R.string.edit_profile))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.emergency_contacts))
            }
            if (profile.emergencyContacts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.emergency_contacts),
                        stringResource(R.string.common_not_set),
                    )
                }
            } else {
                items(profile.emergencyContacts, key = { it.id }) { contact ->
                    SectionCard(contact.name) {
                        Text(contact.relationship)
                        Text(contact.phoneNumber)
                        contact.notes?.let { Text(it) }
                        Button(onClick = { contactEditor = contact }) {
                            Text(stringResource(R.string.common_edit))
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { addingContact = true }) {
                    Text(stringResource(R.string.add_emergency_contact))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.vaccinations))
            }
            if (vaccinations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.vaccinations), stringResource(R.string.common_not_set))
                }
            } else {
                items(vaccinations.sortedByDescending { it.dateAdministered }, key = { it.id }) { vaccination ->
                    SectionCard(vaccination.name) {
                        Text(vaccination.dateAdministered.localizedDate())
                        vaccination.provider?.let { Text(it) }
                        vaccination.nextDueOn?.let { Text(it.localizedDate()) }
                        Button(onClick = { vaccinationEditor = vaccination }) {
                            Text(stringResource(R.string.common_edit))
                        }
                    }
                }
            }
        }
    }

    if (profileEditorVisible) {
        ProfileDialog(
            profile = profile,
            onDismiss = { profileEditorVisible = false },
            onSave = {
                onUpdateProfile(it)
                profileEditorVisible = false
            },
        )
    }
    if (addingContact || contactEditor != null) {
        ContactDialog(
            existing = contactEditor,
            onDismiss = {
                addingContact = false
                contactEditor = null
            },
            onSave = { contact ->
                val contacts = profile.emergencyContacts.filterNot { it.id == contact.id } + contact
                onUpdateProfile(profile.copy(emergencyContacts = contacts))
                addingContact = false
                contactEditor = null
            },
            onDelete = contactEditor?.let { contact ->
                {
                    onUpdateProfile(profile.copy(emergencyContacts = profile.emergencyContacts - contact))
                    contactEditor = null
                }
            },
        )
    }
    if (addingVaccination || vaccinationEditor != null) {
        VaccinationDialog(
            existing = vaccinationEditor,
            today = today,
            onDismiss = {
                addingVaccination = false
                vaccinationEditor = null
            },
            onSave = {
                onUpsertVaccination(it)
                addingVaccination = false
                vaccinationEditor = null
            },
            onDelete = vaccinationEditor?.let { vaccination ->
                {
                    onDeleteVaccination(vaccination.id)
                    vaccinationEditor = null
                }
            },
        )
    }
}

@Composable
private fun ProfileDialog(
    profile: HealthProfile,
    onDismiss: () -> Unit,
    onSave: (HealthProfile) -> Unit,
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bloodType by remember { mutableStateOf(profile.bloodType.orEmpty()) }
    var allergies by remember { mutableStateOf(profile.allergies) }
    var conditions by remember { mutableStateOf(profile.chronicConditions) }
    var surgeries by remember { mutableStateOf(profile.surgeries) }
    FormDialog(
        title = stringResource(R.string.edit_profile),
        saveEnabled = displayName.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                profile.copy(
                    displayName = displayName.trim(),
                    bloodType = bloodType.trim().ifBlank { null },
                    allergies = allergies,
                    chronicConditions = conditions,
                    surgeries = surgeries,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.display_name)) })
            OutlinedTextField(bloodType, { bloodType = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.blood_type)) })
            StringListEditor(stringResource(R.string.allergies), allergies, { allergies = it })
            StringListEditor(stringResource(R.string.chronic_conditions), conditions, { conditions = it })
            StringListEditor(stringResource(R.string.surgeries), surgeries, { surgeries = it })
        }
    }
}

@Composable
private fun ContactDialog(
    existing: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var relationship by remember { mutableStateOf(existing?.relationship.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phoneNumber.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_emergency_contact else R.string.edit_emergency_contact),
        saveEnabled = name.isNotBlank() && relationship.isNotBlank() && phone.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EmergencyContact(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = name.trim(),
                    relationship = relationship.trim(),
                    phoneNumber = phone.trim(),
                    notes = notes.trim().ifBlank { null },
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_name)) })
            OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_relationship)) })
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_phone)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.contact_notes)) })
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteConfirmation = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteConfirmation) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_contact_title),
            stringResource(R.string.delete_contact_message),
            { deleteConfirmation = false },
            {
                deleteConfirmation = false
                onDelete?.invoke()
            },
        )
    }
}

@Composable
private fun VaccinationDialog(
    existing: Vaccination?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Vaccination) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var date by remember { mutableStateOf(existing?.dateAdministered ?: today) }
    var provider by remember { mutableStateOf(existing?.provider.orEmpty()) }
    var lot by remember { mutableStateOf(existing?.lotNumber.orEmpty()) }
    var hasNextDue by remember { mutableStateOf(existing?.nextDueOn != null) }
    var nextDue by remember { mutableStateOf(existing?.nextDueOn ?: today) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_vaccination else R.string.edit_vaccination),
        saveEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                Vaccination(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = name.trim(),
                    dateAdministered = date,
                    provider = provider.trim().ifBlank { null },
                    lotNumber = lot.trim().ifBlank { null },
                    nextDueOn = nextDue.takeIf { hasNextDue },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_name)) })
            DateField(stringResource(R.string.vaccination_date), date, { date = it })
            OutlinedTextField(provider, { provider = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_provider)) })
            OutlinedTextField(lot, { lot = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_lot_number)) })
            SwitchField(stringResource(R.string.vaccination_next_due), hasNextDue, { hasNextDue = it })
            if (hasNextDue) DateField(stringResource(R.string.vaccination_next_due), nextDue, { nextDue = it })
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteConfirmation = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteConfirmation) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_vaccination_title),
            stringResource(R.string.delete_vaccination_message),
            { deleteConfirmation = false },
            {
                deleteConfirmation = false
                onDelete?.invoke()
            },
        )
    }
}

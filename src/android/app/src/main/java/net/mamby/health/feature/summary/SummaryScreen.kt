package net.mamby.health.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectiveKind
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthIdentifierKind
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Vaccination
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.CareDirectoryPicker
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.theme.UiTokens

@Composable
fun SummaryScreen(
    record: ProfileRecord,
    today: LocalDate,
    onBack: () -> Unit,
    onUpdateProfile: (HealthProfile) -> Unit,
    onUpsertVaccination: (Vaccination) -> Unit,
    onDeleteVaccination: (UUID) -> Unit,
    onSetPrimaryDoctor: (UUID?) -> Unit,
    onUpsertFamilyHistory: (FamilyHistoryEntry) -> Unit,
    onDeleteFamilyHistory: (UUID) -> Unit,
    onUpsertDirective: (CareDirective) -> Unit,
    onDeleteDirective: (UUID) -> Unit,
    onUpsertIdentifier: (HealthIdentifier) -> Unit,
    onDeleteIdentifier: (UUID) -> Unit,
    onEmergencyContactSelected: (UUID) -> Unit,
    onVaccinationSelected: (UUID) -> Unit,
    onFamilyHistorySelected: (UUID) -> Unit,
    onDirectiveSelected: (UUID) -> Unit,
    onIdentifierSelected: (UUID) -> Unit,
    creationRequest: Long = 0,
) {
    val profile = record.profile
    val vaccinations = record.vaccinations
    var profileEditorVisible by remember(profile.id) { mutableStateOf(false) }
    var contactEditor by remember(profile.id) { mutableStateOf<EmergencyContact?>(null) }
    var addingContact by remember(profile.id) { mutableStateOf(false) }
    var vaccinationEditor by remember(profile.id) { mutableStateOf<Vaccination?>(null) }
    var addingVaccination by remember(profile.id) { mutableStateOf(false) }
    var doctorSelectorVisible by remember(profile.id) { mutableStateOf(false) }
    var familyEditor by remember(profile.id) { mutableStateOf<FamilyHistoryEntry?>(null) }
    var addingFamily by remember(profile.id) { mutableStateOf(false) }
    var directiveEditor by remember(profile.id) { mutableStateOf<CareDirective?>(null) }
    var addingDirective by remember(profile.id) { mutableStateOf(false) }
    var identifierEditor by remember(profile.id) { mutableStateOf<HealthIdentifier?>(null) }
    var addingIdentifier by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(creationRequest) {
        if (creationRequest > 0) profileEditorVisible = true
    }

    AppScreenScaffold(
        title = stringResource(R.string.health_info_title),
        onBack = onBack,
        contextHeader = { ProfileOwnerHeader(profile) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingVaccination = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_vaccination))
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
                val primaryDoctor = record.careDirectory
                    .firstOrNull { it.id == profile.primaryDoctorEntryId }
                SectionCard(stringResource(R.string.primary_doctor)) {
                    Text(primaryDoctor?.name ?: stringResource(R.string.common_not_set))
                    primaryDoctor?.specialty?.let { Text(it) }
                    Button(onClick = { doctorSelectorVisible = true }) {
                        Text(stringResource(R.string.choose_primary_doctor))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.family_history_title))
            }
            items(record.familyHistory, key = FamilyHistoryEntry::id) { entry ->
                SectionCard(entry.condition) {
                    Text(entry.relationship)
                    entry.ageAtOnsetYears?.let {
                        Text(stringResource(R.string.family_history_age_at_onset_value, it))
                    }
                    entry.notes?.let { Text(it) }
                    OutlinedButton(onClick = { onFamilyHistorySelected(entry.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { familyEditor = entry }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { addingFamily = true }) {
                    Text(stringResource(R.string.add_family_history))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.directives_title))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.directives_disclaimer))
            }
            items(record.directives, key = CareDirective::id) { directive ->
                SectionCard(directive.title) {
                    Text(stringResource(directive.kind.labelResource()))
                    Text(directive.recordedOn.localizedDate())
                    Text(directive.text)
                    OutlinedButton(onClick = { onDirectiveSelected(directive.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { directiveEditor = directive }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { addingDirective = true }) {
                    Text(stringResource(R.string.add_directive))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.health_identifiers_title))
            }
            items(record.healthIdentifiers, key = HealthIdentifier::id) { identifier ->
                SectionCard(identifier.label) {
                    Text(maskIdentifier(identifier.value))
                    identifier.issuer?.let { Text(it) }
                    OutlinedButton(onClick = { onIdentifierSelected(identifier.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { identifierEditor = identifier }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { addingIdentifier = true }) {
                    Text(stringResource(R.string.add_health_identifier))
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
                        OutlinedButton(onClick = { onEmergencyContactSelected(contact.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
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
                        OutlinedButton(onClick = { onVaccinationSelected(vaccination.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                        Button(onClick = { vaccinationEditor = vaccination }) {
                            Text(stringResource(R.string.common_edit))
                        }
                    }
                }
            }
        }
    }

    if (profileEditorVisible) {
        HealthProfileDialog(
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
            record = record,
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
    if (doctorSelectorVisible) {
        PrimaryDoctorDialog(
            doctors = record.careDirectory.filter { it.kind == CareDirectoryKind.DOCTOR },
            selectedId = profile.primaryDoctorEntryId,
            onDismiss = { doctorSelectorVisible = false },
            onSelect = {
                onSetPrimaryDoctor(it)
                doctorSelectorVisible = false
            },
        )
    }
    if (addingFamily || familyEditor != null) {
        FamilyHistoryDialog(
            existing = familyEditor,
            onDismiss = {
                addingFamily = false
                familyEditor = null
            },
            onSave = {
                onUpsertFamilyHistory(it)
                addingFamily = false
                familyEditor = null
            },
            onDelete = familyEditor?.let { entry ->
                {
                    onDeleteFamilyHistory(entry.id)
                    familyEditor = null
                }
            },
        )
    }
    if (addingDirective || directiveEditor != null) {
        CareDirectiveDialog(
            existing = directiveEditor,
            today = today,
            documents = record.documents,
            onDismiss = {
                addingDirective = false
                directiveEditor = null
            },
            onSave = {
                onUpsertDirective(it)
                addingDirective = false
                directiveEditor = null
            },
            onDelete = directiveEditor?.let { directive ->
                {
                    onDeleteDirective(directive.id)
                    directiveEditor = null
                }
            },
        )
    }
    if (addingIdentifier || identifierEditor != null) {
        HealthIdentifierDialog(
            existing = identifierEditor,
            onDismiss = {
                addingIdentifier = false
                identifierEditor = null
            },
            onSave = {
                onUpsertIdentifier(it)
                addingIdentifier = false
                identifierEditor = null
            },
            onDelete = identifierEditor?.let { identifier ->
                {
                    onDeleteIdentifier(identifier.id)
                    identifierEditor = null
                }
            },
        )
    }
}

@Composable
private fun PrimaryDoctorDialog(
    doctors: List<CareDirectoryEntry>,
    selectedId: UUID?,
    onDismiss: () -> Unit,
    onSelect: (UUID?) -> Unit,
) {
    var selected by remember(selectedId) { mutableStateOf(selectedId) }
    FormDialog(
        title = stringResource(R.string.choose_primary_doctor),
        saveEnabled = selected != selectedId,
        onDismiss = onDismiss,
        onSave = { onSelect(selected) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            if (doctors.isEmpty()) Text(stringResource(R.string.no_doctors_in_directory))
            doctors.forEach { doctor ->
                FilterChip(
                    selected = selected == doctor.id,
                    onClick = { selected = doctor.id },
                    label = { Text(doctor.name) },
                )
            }
            OutlinedButton(onClick = { selected = null }, enabled = selected != null) {
                Text(stringResource(R.string.clear_primary_doctor))
            }
        }
    }
}

@Composable
private fun FamilyHistoryDialog(
    existing: FamilyHistoryEntry?,
    onDismiss: () -> Unit,
    onSave: (FamilyHistoryEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var relationship by remember(existing?.id) { mutableStateOf(existing?.relationship.orEmpty()) }
    var condition by remember(existing?.id) { mutableStateOf(existing?.condition.orEmpty()) }
    var age by remember(existing?.id) { mutableStateOf(existing?.ageAtOnsetYears?.toString().orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_family_history else R.string.edit_family_history),
        saveEnabled = relationship.isNotBlank() && condition.isNotBlank() &&
            (age.isBlank() || age.toIntOrNull()?.let { it >= 0 } == true),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                FamilyHistoryEntry(
                    id = existing?.id ?: UUID.randomUUID(),
                    relationship = relationship.trim(),
                    condition = condition.trim(),
                    ageAtOnsetYears = age.toIntOrNull(),
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.family_relationship)) })
            OutlinedTextField(condition, { condition = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.family_condition)) })
            OutlinedTextField(age, { age = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.family_age_at_onset)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_notes)) }, minLines = 2)
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteConfirmation = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteConfirmation) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_family_history_title),
            stringResource(R.string.delete_family_history_message),
            { deleteConfirmation = false },
            {
                deleteConfirmation = false
                onDelete?.invoke()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareDirectiveDialog(
    existing: CareDirective?,
    today: LocalDate,
    documents: List<MedicalDocument>,
    onDismiss: () -> Unit,
    onSave: (CareDirective) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: CareDirectiveKind.ADVANCE_DIRECTIVE) }
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var date by remember(existing?.id) { mutableStateOf(existing?.recordedOn ?: today) }
    var relatedDocuments by remember(existing?.id) {
        mutableStateOf(existing?.relatedDocumentIds?.toSet() ?: emptySet())
    }
    var expanded by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_directive else R.string.edit_directive),
        saveEnabled = title.isNotBlank() && text.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CareDirective(
                    id = existing?.id ?: UUID.randomUUID(),
                    kind = kind,
                    title = title.trim(),
                    text = text.trim(),
                    recordedOn = date,
                    relatedDocumentIds = relatedDocuments.toList(),
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            Text(stringResource(R.string.directives_disclaimer))
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    value = stringResource(kind.labelResource()),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.directive_kind)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    CareDirectiveKind.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(stringResource(candidate.labelResource())) },
                            onClick = {
                                kind = candidate
                                expanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directive_title)) })
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directive_text)) }, minLines = 4)
            DateField(stringResource(R.string.directive_date), date, { date = it })
            Text(stringResource(R.string.directive_related_documents))
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
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteConfirmation = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteConfirmation) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_directive_title),
            stringResource(R.string.delete_directive_message),
            { deleteConfirmation = false },
            {
                deleteConfirmation = false
                onDelete?.invoke()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthIdentifierDialog(
    existing: HealthIdentifier?,
    onDismiss: () -> Unit,
    onSave: (HealthIdentifier) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: HealthIdentifierKind.NATIONAL_HEALTH) }
    var label by remember(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var value by remember(existing?.id) { mutableStateOf(existing?.value.orEmpty()) }
    var issuer by remember(existing?.id) { mutableStateOf(existing?.issuer.orEmpty()) }
    var country by remember(existing?.id) { mutableStateOf(existing?.country.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_health_identifier else R.string.edit_health_identifier),
        saveEnabled = label.isNotBlank() && value.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                HealthIdentifier(
                    id = existing?.id ?: UUID.randomUUID(),
                    kind = kind,
                    label = label.trim(),
                    value = value.trim(),
                    issuer = issuer.trim().ifBlank { null },
                    country = country.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    value = stringResource(kind.labelResource()),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.identifier_kind)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    HealthIdentifierKind.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(stringResource(candidate.labelResource())) },
                            onClick = {
                                kind = candidate
                                expanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.identifier_label)) })
            OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.identifier_value)) })
            OutlinedTextField(issuer, { issuer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.identifier_issuer)) })
            OutlinedTextField(country, { country = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.identifier_country)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_notes)) }, minLines = 2)
            if (onDelete != null) {
                OutlinedButton(onClick = { deleteConfirmation = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (deleteConfirmation) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_health_identifier_title),
            stringResource(R.string.delete_health_identifier_message),
            { deleteConfirmation = false },
            {
                deleteConfirmation = false
                onDelete?.invoke()
            },
        )
    }
}

@Composable
fun HealthProfileDialog(
    profile: HealthProfile,
    onDismiss: () -> Unit,
    onSave: (HealthProfile) -> Unit,
    ownerSelected: Boolean = true,
    profilePicker: (@Composable () -> Unit)? = null,
) {
    var displayName by remember(profile.id) { mutableStateOf(profile.displayName) }
    var bloodType by remember(profile.id) { mutableStateOf(profile.bloodType.orEmpty()) }
    var allergies by remember(profile.id) { mutableStateOf(profile.allergies) }
    var conditions by remember(profile.id) { mutableStateOf(profile.chronicConditions) }
    var surgeries by remember(profile.id) { mutableStateOf(profile.surgeries) }
    FormDialog(
        title = stringResource(R.string.edit_profile),
        saveEnabled = ownerSelected && displayName.isNotBlank(),
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
            profilePicker?.invoke()
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
    record: ProfileRecord,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Vaccination) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var date by remember { mutableStateOf(existing?.dateAdministered ?: today) }
    var provider by remember { mutableStateOf(existing?.provider.orEmpty()) }
    var providerEntryId by remember { mutableStateOf(existing?.providerEntryId) }
    var lot by remember { mutableStateOf(existing?.lotNumber.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
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
                    providerEntryId = providerEntryId,
                    lotNumber = lot.trim().ifBlank { null },
                    nextDueOn = nextDue.takeIf { hasNextDue },
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_name)) })
            DateField(stringResource(R.string.vaccination_date), date, { date = it })
            OutlinedTextField(provider, { provider = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_provider)) })
            CareDirectoryPicker(
                entries = record.careDirectory.filter { it.kind != CareDirectoryKind.PHARMACY },
                selectedId = providerEntryId,
                onSelected = { providerEntryId = it },
                label = stringResource(R.string.vaccination_provider_directory),
            )
            OutlinedTextField(lot, { lot = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vaccination_lot_number)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_notes)) }, minLines = 2)
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

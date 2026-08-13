package net.mamby.health.feature.summary

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
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
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthIdentifierKind
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Vaccination
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.EditorFieldPair
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.format.labelResource

data class HealthProfileEditorDraft(
    val profileId: UUID,
    val sourceProfileId: UUID?,
    val displayName: String,
    val bloodType: String,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val surgeries: List<String>,
)

data class EmergencyContactEditorDraft(
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val notes: String,
)

data class VaccinationEditorDraft(
    val name: String,
    val dateAdministered: LocalDate,
    val provider: String,
    val lotNumber: String,
    val hasNextDueDate: Boolean,
    val nextDueOn: LocalDate,
    val notes: String,
)

data class FamilyHistoryEditorDraft(
    val relationship: String,
    val condition: String,
    val ageAtOnsetYears: String,
    val notes: String,
)

data class CareDirectiveEditorDraft(
    val kind: CareDirectiveKind,
    val title: String,
    val text: String,
    val recordedOn: LocalDate,
    val relatedDocumentIds: Set<UUID>,
)

data class HealthIdentifierEditorDraft(
    val kind: HealthIdentifierKind,
    val label: String,
    val value: String,
    val issuer: String,
    val country: String,
    val notes: String,
)

@Composable
fun HealthProfileEditorScreen(
    records: List<ProfileRecord>,
    initialProfileId: UUID,
    onCancel: () -> Unit,
    onSave: (UUID, HealthProfile, (Boolean) -> Unit) -> Unit,
) {
    val initialRecord = records.firstOrNull { it.profile.id == initialProfileId }
    val editorState = rememberEditorState {
        initialRecord?.profile?.toEditorDraft(initialProfileId)
            ?: HealthProfileEditorDraft(
                profileId = initialProfileId,
                sourceProfileId = null,
                displayName = "",
                bloodType = "",
                allergies = emptyList(),
                chronicConditions = emptyList(),
                surgeries = emptyList(),
            )
    }
    val draft = editorState.value
    val selectedRecord = records.firstOrNull { it.profile.id == draft.profileId }

    LaunchedEffect(selectedRecord, draft.sourceProfileId) {
        if (selectedRecord != null && draft.sourceProfileId != selectedRecord.profile.id) {
            editorState.value = selectedRecord.profile.toEditorDraft(selectedRecord.profile.id)
        }
    }

    AppEditorScaffold(
        title = stringResource(R.string.edit_profile),
        isDirty = editorState.isDirty,
        saveEnabled = selectedRecord != null && draft.displayName.isNotBlank(),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            val record = selectedRecord ?: return@AppEditorScaffold
            editorState.isSaving = true
            onSave(
                record.profile.id,
                record.profile.copy(
                    displayName = draft.displayName.trim(),
                    bloodType = draft.bloodType.trim().ifBlank { null },
                    allergies = draft.allergies,
                    chronicConditions = draft.chronicConditions,
                    surgeries = draft.surgeries,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.summary_title)) {
            selectedRecord?.let { ProfileOwnerHeader(it.profile) }
            EditorFieldPair(
                first = { modifier ->
                    OutlinedTextField(
                        value = draft.displayName,
                        onValueChange = { editorState.value = draft.copy(displayName = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.display_name)) },
                        singleLine = true,
                    )
                },
                second = { modifier ->
                    OutlinedTextField(
                        value = draft.bloodType,
                        onValueChange = { editorState.value = draft.copy(bloodType = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.blood_type)) },
                        singleLine = true,
                    )
                },
            )
            StringListEditor(
                stringResource(R.string.allergies),
                draft.allergies,
                { editorState.value = draft.copy(allergies = it) },
            )
            StringListEditor(
                stringResource(R.string.chronic_conditions),
                draft.chronicConditions,
                { editorState.value = draft.copy(chronicConditions = it) },
            )
            StringListEditor(
                stringResource(R.string.surgeries),
                draft.surgeries,
                { editorState.value = draft.copy(surgeries = it) },
            )
        }
    }
}

@Composable
fun EmergencyContactEditorScreen(
    existing: EmergencyContact?,
    onCancel: () -> Unit,
    onSave: (EmergencyContact, (Boolean) -> Unit) -> Unit,
    onDelete: (((Boolean) -> Unit) -> Unit)?,
) {
    val editorState = rememberEditorState {
        EmergencyContactEditorDraft(
            name = existing?.name.orEmpty(),
            relationship = existing?.relationship.orEmpty(),
            phoneNumber = existing?.phoneNumber.orEmpty(),
            notes = existing?.notes.orEmpty(),
        )
    }
    val draft = editorState.value
    AppEditorScaffold(
        title = stringResource(
            if (existing == null) R.string.add_emergency_contact
            else R.string.edit_emergency_contact,
        ),
        isDirty = editorState.isDirty,
        saveEnabled = draft.name.isNotBlank() &&
            draft.relationship.isNotBlank() &&
            draft.phoneNumber.isNotBlank(),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                EmergencyContact(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = draft.name.trim(),
                    relationship = draft.relationship.trim(),
                    phoneNumber = draft.phoneNumber.trim(),
                    notes = draft.notes.trim().ifBlank { null },
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.emergency_contacts)) {
            EditorFieldPair(
                first = { modifier ->
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { editorState.value = draft.copy(name = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.contact_name)) },
                        singleLine = true,
                    )
                },
                second = { modifier ->
                    OutlinedTextField(
                        value = draft.relationship,
                        onValueChange = { editorState.value = draft.copy(relationship = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.contact_relationship)) },
                        singleLine = true,
                    )
                },
            )
            OutlinedTextField(
                value = draft.phoneNumber,
                onValueChange = { editorState.value = draft.copy(phoneNumber = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contact_phone)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contact_notes)) },
                minLines = 2,
            )
            DeleteEditorAction(
                title = R.string.delete_contact_title,
                message = R.string.delete_contact_message,
                enabled = !editorState.isSaving,
                onDelete = onDelete?.let { delete ->
                    {
                        editorState.isSaving = true
                        delete { succeeded ->
                            editorState.isSaving = false
                            if (succeeded) onCancel()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun VaccinationEditorScreen(
    existing: Vaccination?,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (Vaccination, (Boolean) -> Unit) -> Unit,
    onDelete: (((Boolean) -> Unit) -> Unit)?,
) {
    val editorState = rememberEditorState {
        VaccinationEditorDraft(
            name = existing?.name.orEmpty(),
            dateAdministered = existing?.dateAdministered ?: today,
            provider = existing?.provider.orEmpty(),
            lotNumber = existing?.lotNumber.orEmpty(),
            hasNextDueDate = existing?.nextDueOn != null,
            nextDueOn = existing?.nextDueOn ?: today,
            notes = existing?.notes.orEmpty(),
        )
    }
    val draft = editorState.value
    AppEditorScaffold(
        title = stringResource(
            if (existing == null) R.string.add_vaccination else R.string.edit_vaccination,
        ),
        isDirty = editorState.isDirty,
        saveEnabled = draft.name.isNotBlank(),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                Vaccination(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = draft.name.trim(),
                    dateAdministered = draft.dateAdministered,
                    provider = draft.provider.trim().ifBlank { null },
                    lotNumber = draft.lotNumber.trim().ifBlank { null },
                    nextDueOn = draft.nextDueOn.takeIf { draft.hasNextDueDate },
                    notes = draft.notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.vaccinations)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { editorState.value = draft.copy(name = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vaccination_name)) },
                singleLine = true,
            )
            DateField(
                stringResource(R.string.vaccination_date),
                draft.dateAdministered,
                { editorState.value = draft.copy(dateAdministered = it) },
            )
            EditorFieldPair(
                first = { modifier ->
                    OutlinedTextField(
                        value = draft.provider,
                        onValueChange = { editorState.value = draft.copy(provider = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.vaccination_provider)) },
                        singleLine = true,
                    )
                },
                second = { modifier ->
                    OutlinedTextField(
                        value = draft.lotNumber,
                        onValueChange = { editorState.value = draft.copy(lotNumber = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.vaccination_lot_number)) },
                        singleLine = true,
                    )
                },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.common_notes)) },
                minLines = 2,
            )
            SwitchField(
                stringResource(R.string.vaccination_next_due),
                draft.hasNextDueDate,
                { editorState.value = draft.copy(hasNextDueDate = it) },
            )
            if (draft.hasNextDueDate) {
                DateField(
                    stringResource(R.string.vaccination_next_due),
                    draft.nextDueOn,
                    { editorState.value = draft.copy(nextDueOn = it) },
                )
            }
            DeleteEditorAction(
                title = R.string.delete_vaccination_title,
                message = R.string.delete_vaccination_message,
                enabled = !editorState.isSaving,
                onDelete = onDelete?.let { delete ->
                    {
                        editorState.isSaving = true
                        delete { succeeded ->
                            editorState.isSaving = false
                            if (succeeded) onCancel()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun FamilyHistoryEditorScreen(
    existing: FamilyHistoryEntry?,
    onCancel: () -> Unit,
    onSave: (FamilyHistoryEntry, (Boolean) -> Unit) -> Unit,
    onDelete: (((Boolean) -> Unit) -> Unit)?,
) {
    val editorState = rememberEditorState {
        FamilyHistoryEditorDraft(
            relationship = existing?.relationship.orEmpty(),
            condition = existing?.condition.orEmpty(),
            ageAtOnsetYears = existing?.ageAtOnsetYears?.toString().orEmpty(),
            notes = existing?.notes.orEmpty(),
        )
    }
    val draft = editorState.value
    val ageIsValid = draft.ageAtOnsetYears.isBlank() ||
        draft.ageAtOnsetYears.toIntOrNull()?.let { it >= 0 } == true
    AppEditorScaffold(
        title = stringResource(
            if (existing == null) R.string.add_family_history else R.string.edit_family_history,
        ),
        isDirty = editorState.isDirty,
        saveEnabled = draft.relationship.isNotBlank() && draft.condition.isNotBlank() && ageIsValid,
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                FamilyHistoryEntry(
                    id = existing?.id ?: UUID.randomUUID(),
                    relationship = draft.relationship.trim(),
                    condition = draft.condition.trim(),
                    ageAtOnsetYears = draft.ageAtOnsetYears.toIntOrNull(),
                    notes = draft.notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.family_history_title)) {
            EditorFieldPair(
                first = { modifier ->
                    OutlinedTextField(
                        value = draft.relationship,
                        onValueChange = { editorState.value = draft.copy(relationship = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.family_relationship)) },
                        singleLine = true,
                    )
                },
                second = { modifier ->
                    OutlinedTextField(
                        value = draft.condition,
                        onValueChange = { editorState.value = draft.copy(condition = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.family_condition)) },
                        singleLine = true,
                    )
                },
            )
            OutlinedTextField(
                value = draft.ageAtOnsetYears,
                onValueChange = { editorState.value = draft.copy(ageAtOnsetYears = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.family_age_at_onset)) },
                singleLine = true,
                isError = !ageIsValid,
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.common_notes)) },
                minLines = 2,
            )
            DeleteEditorAction(
                title = R.string.delete_family_history_title,
                message = R.string.delete_family_history_message,
                enabled = !editorState.isSaving,
                onDelete = onDelete?.let { delete ->
                    {
                        editorState.isSaving = true
                        delete { succeeded ->
                            editorState.isSaving = false
                            if (succeeded) onCancel()
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareDirectiveEditorScreen(
    existing: CareDirective?,
    today: LocalDate,
    documents: List<MedicalDocument>,
    onCancel: () -> Unit,
    onSave: (CareDirective, (Boolean) -> Unit) -> Unit,
    onDelete: (((Boolean) -> Unit) -> Unit)?,
) {
    val editorState = rememberEditorState {
        CareDirectiveEditorDraft(
            kind = existing?.kind ?: CareDirectiveKind.ADVANCE_DIRECTIVE,
            title = existing?.title.orEmpty(),
            text = existing?.text.orEmpty(),
            recordedOn = existing?.recordedOn ?: today,
            relatedDocumentIds = existing?.relatedDocumentIds?.toSet().orEmpty(),
        )
    }
    val draft = editorState.value
    var kindExpanded by remember { mutableStateOf(false) }
    AppEditorScaffold(
        title = stringResource(
            if (existing == null) R.string.add_directive else R.string.edit_directive,
        ),
        isDirty = editorState.isDirty,
        saveEnabled = draft.title.isNotBlank() && draft.text.isNotBlank(),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                CareDirective(
                    id = existing?.id ?: UUID.randomUUID(),
                    kind = draft.kind,
                    title = draft.title.trim(),
                    text = draft.text.trim(),
                    recordedOn = draft.recordedOn,
                    relatedDocumentIds = draft.relatedDocumentIds.toList(),
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.directives_title)) {
            Text(stringResource(R.string.directives_disclaimer))
            EditorFieldPair(
                first = { modifier ->
                    ExposedDropdownMenuBox(
                        expanded = kindExpanded,
                        onExpandedChange = { kindExpanded = it },
                        modifier = modifier,
                    ) {
                        OutlinedTextField(
                            value = stringResource(draft.kind.labelResource()),
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            readOnly = true,
                            label = { Text(stringResource(R.string.directive_kind)) },
                            trailingIcon = { DropdownTrailingIcon(kindExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = kindExpanded,
                            onDismissRequest = { kindExpanded = false },
                        ) {
                            CareDirectiveKind.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(candidate.labelResource())) },
                                    onClick = {
                                        editorState.value = draft.copy(kind = candidate)
                                        kindExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                second = { modifier ->
                    Box(modifier) {
                        DateField(
                            stringResource(R.string.directive_date),
                            draft.recordedOn,
                            { editorState.value = draft.copy(recordedOn = it) },
                        )
                    }
                },
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { editorState.value = draft.copy(title = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.directive_title)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.text,
                onValueChange = { editorState.value = draft.copy(text = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.directive_text)) },
                minLines = 4,
            )
            Text(stringResource(R.string.directive_related_documents))
            if (documents.isEmpty()) Text(stringResource(R.string.no_related_documents))
            documents.forEach { document ->
                FilterChip(
                    selected = document.id in draft.relatedDocumentIds,
                    onClick = {
                        editorState.value = draft.copy(
                            relatedDocumentIds = if (document.id in draft.relatedDocumentIds) {
                                draft.relatedDocumentIds - document.id
                            } else {
                                draft.relatedDocumentIds + document.id
                            },
                        )
                    },
                    label = { Text(document.title) },
                )
            }
            DeleteEditorAction(
                title = R.string.delete_directive_title,
                message = R.string.delete_directive_message,
                enabled = !editorState.isSaving,
                onDelete = onDelete?.let { delete ->
                    {
                        editorState.isSaving = true
                        delete { succeeded ->
                            editorState.isSaving = false
                            if (succeeded) onCancel()
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthIdentifierEditorScreen(
    existing: HealthIdentifier?,
    onCancel: () -> Unit,
    onSave: (HealthIdentifier, (Boolean) -> Unit) -> Unit,
    onDelete: (((Boolean) -> Unit) -> Unit)?,
) {
    val editorState = rememberEditorState {
        HealthIdentifierEditorDraft(
            kind = existing?.kind ?: HealthIdentifierKind.NATIONAL_HEALTH,
            label = existing?.label.orEmpty(),
            value = existing?.value.orEmpty(),
            issuer = existing?.issuer.orEmpty(),
            country = existing?.country.orEmpty(),
            notes = existing?.notes.orEmpty(),
        )
    }
    val draft = editorState.value
    var kindExpanded by remember { mutableStateOf(false) }
    AppEditorScaffold(
        title = stringResource(
            if (existing == null) R.string.add_health_identifier
            else R.string.edit_health_identifier,
        ),
        isDirty = editorState.isDirty,
        saveEnabled = draft.label.isNotBlank() && draft.value.isNotBlank(),
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                HealthIdentifier(
                    id = existing?.id ?: UUID.randomUUID(),
                    kind = draft.kind,
                    label = draft.label.trim(),
                    value = draft.value.trim(),
                    issuer = draft.issuer.trim().ifBlank { null },
                    country = draft.country.trim().ifBlank { null },
                    notes = draft.notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.health_identifiers_title)) {
            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { kindExpanded = it },
            ) {
                OutlinedTextField(
                    value = stringResource(draft.kind.labelResource()),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.identifier_kind)) },
                    trailingIcon = { DropdownTrailingIcon(kindExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = kindExpanded,
                    onDismissRequest = { kindExpanded = false },
                ) {
                    HealthIdentifierKind.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(stringResource(candidate.labelResource())) },
                            onClick = {
                                editorState.value = draft.copy(kind = candidate)
                                kindExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.label,
                onValueChange = { editorState.value = draft.copy(label = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.identifier_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.value,
                onValueChange = { editorState.value = draft.copy(value = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.identifier_value)) },
                singleLine = true,
            )
            EditorFieldPair(
                first = { modifier ->
                    OutlinedTextField(
                        value = draft.issuer,
                        onValueChange = { editorState.value = draft.copy(issuer = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.identifier_issuer)) },
                        singleLine = true,
                    )
                },
                second = { modifier ->
                    OutlinedTextField(
                        value = draft.country,
                        onValueChange = { editorState.value = draft.copy(country = it) },
                        modifier = modifier,
                        label = { Text(stringResource(R.string.identifier_country)) },
                        singleLine = true,
                    )
                },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.common_notes)) },
                minLines = 2,
            )
            DeleteEditorAction(
                title = R.string.delete_health_identifier_title,
                message = R.string.delete_health_identifier_message,
                enabled = !editorState.isSaving,
                onDelete = onDelete?.let { delete ->
                    {
                        editorState.isSaving = true
                        delete { succeeded ->
                            editorState.isSaving = false
                            if (succeeded) onCancel()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DeleteEditorAction(
    @StringRes title: Int,
    @StringRes message: Int,
    enabled: Boolean,
    onDelete: (() -> Unit)?,
) {
    if (onDelete == null) return
    var confirmationVisible by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirmationVisible = true },
        enabled = enabled,
    ) {
        Text(stringResource(R.string.common_delete))
    }
    if (confirmationVisible) {
        ConfirmDeleteDialog(
            title = stringResource(title),
            message = stringResource(message),
            onDismiss = { confirmationVisible = false },
            onConfirm = {
                confirmationVisible = false
                onDelete()
            },
        )
    }
}

private fun HealthProfile.toEditorDraft(selectedProfileId: UUID): HealthProfileEditorDraft =
    HealthProfileEditorDraft(
        profileId = selectedProfileId,
        sourceProfileId = id.takeIf { selectedProfileId == id },
        displayName = displayName,
        bloodType = bloodType.orEmpty(),
        allergies = allergies,
        chronicConditions = chronicConditions,
        surgeries = surgeries,
    )

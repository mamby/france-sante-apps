package net.mamby.health.feature.directory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.core.model.PostalAddress
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.feature.ProfileOwned
import net.mamby.health.feature.ownedItems
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.ProfilePickerField
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.theme.UiTokens

@Composable
fun DirectoryScreen(
    records: List<ProfileRecord>,
    onAddProfile: (String, (UUID) -> Unit) -> Unit,
    onUpsert: (UUID, CareDirectoryEntry) -> Unit,
    onSelected: (UUID, UUID) -> Unit,
    creationRequest: Long = 0,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
    var creationVisible by remember { mutableStateOf(false) }
    var creationProfileId by remember { mutableStateOf<UUID?>(null) }
    fun startCreation() {
        creationProfileId = filterProfileId ?: records.singleOrNull()?.profile?.id
        creationVisible = true
    }
    LaunchedEffect(creationRequest) { if (creationRequest > 0) startCreation() }
    val filteredRecords = filterProfileId?.let { id -> records.filter { it.profile.id == id } } ?: records
    val entries = remember(filteredRecords) {
        filteredRecords.ownedItems(ProfileRecord::careDirectory).sortedWith(
            compareBy<ProfileOwned<CareDirectoryEntry>> { it.value.name }
                .thenBy { it.profileId }
                .thenBy { it.value.id },
        )
    }
    AppScreenScaffold(
        title = stringResource(R.string.care_directory_title),
        floatingActionButton = {
            FloatingActionButton(onClick = ::startCreation) {
                Icon(
                    painterResource(R.drawable.ic_lucide_plus),
                    stringResource(R.string.add_directory_entry),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = padding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileFilterChip(records, filterProfileId, { filterProfileId = it })
            }
            if (entries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_directory_entries_title),
                        stringResource(R.string.no_directory_entries_body),
                    )
                }
            } else {
                items(entries, key = { "${it.profileId}:${it.value.id}" }) { owned ->
                    val entry = owned.value
                    SectionCard(entry.name) {
                        if (filterProfileId == null && records.size > 1) ProfileMarker(owned.profile)
                        Text(stringResource(entry.kind.labelResource()))
                        entry.specialty?.let { Text(it) }
                        entry.organization?.let { Text(it) }
                        if (owned.profile.primaryDoctorEntryId == entry.id) {
                            Text(stringResource(R.string.primary_doctor))
                        }
                        Button(onClick = { onSelected(owned.profileId, entry.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
    if (creationVisible) {
        DirectoryEntryDialog(
            existing = null,
            ownerSelected = creationProfileId != null,
            profilePicker = {
                ProfilePickerField(records, creationProfileId, { creationProfileId = it }, onAddProfile)
            },
            onDismiss = { creationVisible = false },
            onSave = {
                onUpsert(requireNotNull(creationProfileId), it)
                creationVisible = false
            },
        )
    }
}

@Composable
fun DirectoryEntryDetailScreen(
    record: ProfileRecord,
    entry: CareDirectoryEntry,
    onBack: (() -> Unit)?,
    onUpsert: (CareDirectoryEntry) -> Unit,
    onSetPrimaryDoctor: (UUID?) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(entry.id) { mutableStateOf(false) }
    var deleting by remember(entry.id) { mutableStateOf(false) }
    val isPrimary = record.profile.primaryDoctorEntryId == entry.id
    AppScreenScaffold(
        title = entry.name,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            PageHeader()
            ProfileOwnerHeader(record.profile)
            SectionCard(stringResource(R.string.directory_details)) {
                LabeledValue(stringResource(R.string.directory_kind), stringResource(entry.kind.labelResource()))
                LabeledValue(stringResource(R.string.directory_specialty), entry.specialty.orEmpty())
                LabeledValue(stringResource(R.string.directory_organization), entry.organization.orEmpty())
                LabeledValue(stringResource(R.string.directory_address), entry.address.displayText())
                LabeledValue(stringResource(R.string.directory_phones), entry.phoneNumbers.joinToString())
                LabeledValue(stringResource(R.string.directory_emails), entry.emailAddresses.joinToString())
                LabeledValue(stringResource(R.string.common_notes), entry.notes.orEmpty())
            }
            if (entry.kind == CareDirectoryKind.DOCTOR) {
                Button(onClick = { onSetPrimaryDoctor(entry.id.takeUnless { isPrimary }) }) {
                    Text(stringResource(if (isPrimary) R.string.clear_primary_doctor else R.string.set_primary_doctor))
                }
            }
            Button(onClick = { editing = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editing) {
        DirectoryEntryDialog(
            existing = entry,
            onDismiss = { editing = false },
            onSave = {
                onUpsert(it)
                editing = false
            },
        )
    }
    if (deleting) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_directory_entry_title),
            message = stringResource(R.string.delete_directory_entry_message),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryEntryDialog(
    existing: CareDirectoryEntry?,
    onDismiss: () -> Unit,
    onSave: (CareDirectoryEntry) -> Unit,
    ownerSelected: Boolean = true,
    profilePicker: (@Composable () -> Unit)? = null,
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: CareDirectoryKind.DOCTOR) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var specialty by remember(existing?.id) { mutableStateOf(existing?.specialty.orEmpty()) }
    var organization by remember(existing?.id) { mutableStateOf(existing?.organization.orEmpty()) }
    var addressLines by remember(existing?.id) { mutableStateOf(existing?.address?.addressLines.orEmpty()) }
    var locality by remember(existing?.id) { mutableStateOf(existing?.address?.locality.orEmpty()) }
    var region by remember(existing?.id) { mutableStateOf(existing?.address?.region.orEmpty()) }
    var postalCode by remember(existing?.id) { mutableStateOf(existing?.address?.postalCode.orEmpty()) }
    var country by remember(existing?.id) { mutableStateOf(existing?.address?.country.orEmpty()) }
    var phones by remember(existing?.id) { mutableStateOf(existing?.phoneNumbers.orEmpty()) }
    var emails by remember(existing?.id) { mutableStateOf(existing?.emailAddresses.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_directory_entry else R.string.edit_directory_entry),
        saveEnabled = ownerSelected && name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CareDirectoryEntry(
                    id = existing?.id ?: UUID.randomUUID(),
                    kind = kind,
                    name = name.trim(),
                    specialty = specialty.trim().ifBlank { null },
                    organization = organization.trim().ifBlank { null },
                    address = PostalAddress(
                        addressLines = addressLines,
                        locality = locality.trim().ifBlank { null },
                        region = region.trim().ifBlank { null },
                        postalCode = postalCode.trim().ifBlank { null },
                        country = country.trim().ifBlank { null },
                    ),
                    phoneNumbers = phones,
                    emailAddresses = emails,
                    notes = notes.trim().ifBlank { null },
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            profilePicker?.invoke()
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    value = stringResource(kind.labelResource()),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.directory_kind)) },
                    trailingIcon = { DropdownTrailingIcon(expanded) },
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    CareDirectoryKind.entries.forEach { candidate ->
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
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_name)) })
            OutlinedTextField(specialty, { specialty = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_specialty)) })
            OutlinedTextField(organization, { organization = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_organization)) })
            StringListEditor(stringResource(R.string.directory_address_lines), addressLines, { addressLines = it })
            OutlinedTextField(locality, { locality = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_locality)) })
            OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_region)) })
            OutlinedTextField(postalCode, { postalCode = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_postal_code)) })
            OutlinedTextField(country, { country = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.directory_country)) })
            StringListEditor(stringResource(R.string.directory_phones), phones, { phones = it })
            StringListEditor(stringResource(R.string.directory_emails), emails, { emails = it })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_notes)) }, minLines = 2)
        }
    }
}

private fun PostalAddress.displayText(): String = buildList {
    addAll(addressLines)
    add(listOfNotNull(postalCode, locality).joinToString(" "))
    add(listOfNotNull(region, country).joinToString(" · "))
}.filter(String::isNotBlank).joinToString("\n")

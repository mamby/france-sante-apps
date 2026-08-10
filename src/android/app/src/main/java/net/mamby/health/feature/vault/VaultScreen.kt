package net.mamby.health.feature.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.time.LocalDate
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.DocumentSearch
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.asReference
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
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

data class DocumentImportDraft(
    val uri: Uri,
    val title: String,
    val category: DocumentCategoryRef,
    val documentDate: LocalDate,
    val source: String,
    val sourceEntryId: java.util.UUID? = null,
    val notes: String?,
    val tags: List<String>,
)

@Composable
fun VaultScreen(
    records: List<ProfileRecord>,
    today: LocalDate,
    onBack: () -> Unit,
    onManageCategories: (UUID?) -> Unit,
    onAddProfile: (String, (UUID) -> Unit) -> Unit,
    onImport: (UUID, DocumentImportDraft) -> Unit,
    onDocumentSelected: (UUID, String) -> Unit,
    creationRequest: Long = 0,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
    val selectedRecord = filterProfileId?.let { profileId ->
        records.firstOrNull { it.profile.id == profileId }
    }
    val availableCategories = remember(selectedRecord) {
        val record = selectedRecord ?: return@remember emptyList()
        BuiltInDocumentCategory.entries
            .filter { builtIn ->
                record.builtInDocumentCategoryPreferences
                    .firstOrNull { it.category == builtIn }
                    ?.isHidden != true
            }
            .map(BuiltInDocumentCategory::asReference) +
            record.customDocumentCategories.map { DocumentCategoryRef.Custom(it.id) }
    }
    var category by remember(filterProfileId) { mutableStateOf<DocumentCategoryRef?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var importProfileId by remember { mutableStateOf<UUID?>(null) }
    val filtered = remember(records, selectedRecord, category) {
        if (selectedRecord != null) {
            DocumentSearch.search(selectedRecord.documents, "", category)
                .map { document -> ProfileOwned(selectedRecord, document) }
        } else {
            records.ownedItems(ProfileRecord::documents).sortedWith(
                compareByDescending<ProfileOwned<net.mamby.health.core.model.MedicalDocument>> {
                    it.value.documentDate
                }.thenBy { it.value.title }.thenBy { it.profileId },
            )
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri
        if (uri == null) importProfileId = null
    }
    fun startImport() {
        importProfileId = filterProfileId ?: records.singleOrNull()?.profile?.id
        picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
    }
    LaunchedEffect(creationRequest) { if (creationRequest > 0) startImport() }

    AppScreenScaffold(
        title = stringResource(R.string.documents_tab),
        onBack = onBack,
        actions = {
            IconButton(onClick = { onManageCategories(filterProfileId) }) {
                Icon(
                    painterResource(R.drawable.ic_lucide_sliders_horizontal),
                    stringResource(R.string.manage_document_categories),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::startImport) {
                Icon(painterResource(R.drawable.ic_lucide_plus), stringResource(R.string.import_document))
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
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileFilterChip(records, filterProfileId, { filterProfileId = it })
            }
            if (selectedRecord != null) item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    item {
                        FilterChip(
                            selected = category == null,
                            onClick = { category = null },
                            label = { Text(stringResource(R.string.category_all)) },
                        )
                    }
                    items(availableCategories.size) { index ->
                        val candidate = availableCategories[index]
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(candidate.localizedLabel(selectedRecord)) },
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_documents_title),
                        stringResource(R.string.no_documents_body),
                    )
                }
            } else {
                items(filtered, key = { "${it.profileId}:${it.value.id}" }) { owned ->
                    val document = owned.value
                    SectionCard(document.title) {
                        if (filterProfileId == null && records.size > 1) ProfileMarker(owned.profile)
                        Text(document.category.localizedLabel(owned.record))
                        Text(document.documentDate.localizedDate())
                        Text(document.source)
                        androidx.compose.material3.TextButton(
                            onClick = { onDocumentSelected(owned.profileId, document.id.toString()) },
                        ) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        val owner = records.firstOrNull { it.profile.id == importProfileId }
        DocumentImportDialog(
            uri = uri,
            record = owner ?: records.first(),
            today = today,
            ownerSelected = owner != null,
            profilePicker = {
                ProfilePickerField(records, importProfileId, { importProfileId = it }, onAddProfile)
            },
            onDismiss = {
                pendingUri = null
                importProfileId = null
            },
            onImport = {
                onImport(requireNotNull(importProfileId), it)
                pendingUri = null
                importProfileId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentImportDialog(
    uri: Uri,
    record: ProfileRecord,
    today: LocalDate,
    onDismiss: () -> Unit,
    onImport: (DocumentImportDraft) -> Unit,
    ownerSelected: Boolean = true,
    profilePicker: (@Composable () -> Unit)? = null,
) {
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var sourceEntryId by remember { mutableStateOf<java.util.UUID?>(null) }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var date by remember { mutableStateOf(today) }
    val availableCategories = remember(record) {
        BuiltInDocumentCategory.entries
            .filter { builtIn ->
                record.builtInDocumentCategoryPreferences
                    .firstOrNull { it.category == builtIn }
                    ?.isHidden != true
            }
            .map(BuiltInDocumentCategory::asReference) +
            record.customDocumentCategories.map { DocumentCategoryRef.Custom(it.id) }
    }
    var category by remember(record.profile.id) {
        mutableStateOf(availableCategories.firstOrNull() ?: BuiltInDocumentCategory.OTHER.asReference())
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(record.profile.id) {
        category = availableCategories.firstOrNull() ?: BuiltInDocumentCategory.OTHER.asReference()
        sourceEntryId = null
    }

    FormDialog(
        title = stringResource(R.string.import_document),
        saveEnabled = ownerSelected && title.isNotBlank() && source.isNotBlank() && category in availableCategories,
        onDismiss = onDismiss,
        onSave = {
            onImport(
                DocumentImportDraft(
                    uri = uri,
                    title = title.trim(),
                    category = category,
                    documentDate = date,
                    source = source.trim(),
                    sourceEntryId = sourceEntryId,
                    notes = notes.trim().ifBlank { null },
                    tags = tags,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            profilePicker?.invoke()
            Text(stringResource(R.string.import_limit))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_title)) },
                singleLine = true,
            )
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = category.localizedLabel(record),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    label = { Text(stringResource(R.string.document_category)) },
                    trailingIcon = { DropdownTrailingIcon(categoryExpanded) },
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    availableCategories.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.localizedLabel(record)) },
                            onClick = {
                                category = candidate
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }
            DateField(stringResource(R.string.document_date), date, { date = it })
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_source)) },
                singleLine = true,
            )
            CareDirectoryPicker(
                entries = record.careDirectory,
                selectedId = sourceEntryId,
                onSelected = { sourceEntryId = it },
                label = stringResource(R.string.document_source_directory),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_notes)) },
                minLines = 2,
            )
            StringListEditor(stringResource(R.string.document_tags), tags, { tags = it })
        }
    }
}

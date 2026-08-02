package net.mamby.health.feature.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.DocumentSearch
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.asReference
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.CareDirectoryPicker
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
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
    record: ProfileRecord,
    today: LocalDate,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onSettings: () -> Unit,
    onManageCategories: () -> Unit,
    onImport: (DocumentImportDraft) -> Unit,
    onDocumentSelected: (String) -> Unit,
    creationRequest: Long = 0,
) {
    val profile = record.profile
    val documents = record.documents
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
    var category by remember(profile.id) { mutableStateOf<DocumentCategoryRef?>(null) }
    var pendingUri by remember(profile.id) { mutableStateOf<Uri?>(null) }
    val filtered = remember(documents, category) {
        DocumentSearch.search(documents, "", category)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri
    }
    LaunchedEffect(creationRequest) {
        if (creationRequest > 0) {
            picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
        }
    }

    AppScreenScaffold(
        title = stringResource(R.string.documents_tab),
        onBack = onBack,
        onSettings = onSettings,
        profile = profile,
        onProfileClick = onProfileClick,
        actions = {
            IconButton(onClick = onManageCategories) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.manage_document_categories))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                },
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.import_document))
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
                            label = { Text(candidate.localizedLabel(record)) },
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
                items(filtered, key = { it.id }) { document ->
                    SectionCard(document.title) {
                        Text(document.category.localizedLabel(record))
                        Text(document.documentDate.localizedDate())
                        Text(document.source)
                        androidx.compose.material3.TextButton(
                            onClick = { onDocumentSelected(document.id.toString()) },
                        ) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        DocumentImportDialog(
            uri = uri,
            record = record,
            today = today,
            onDismiss = { pendingUri = null },
            onImport = {
                onImport(it)
                pendingUri = null
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

    FormDialog(
        title = stringResource(R.string.import_document),
        saveEnabled = title.isNotBlank() && source.isNotBlank() && category in availableCategories,
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
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

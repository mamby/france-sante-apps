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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import net.mamby.health.R
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.DocumentSearch
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SampleWorkspaceBanner
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

data class DocumentImportDraft(
    val uri: Uri,
    val title: String,
    val category: DocumentCategory,
    val documentDate: LocalDate,
    val source: String,
    val notes: String?,
    val tags: List<String>,
)

@Composable
fun VaultScreen(
    documents: List<MedicalDocument>,
    isDemo: Boolean,
    today: LocalDate,
    onStartVault: () -> Unit,
    onSettings: () -> Unit,
    onImport: (DocumentImportDraft) -> Unit,
    onDocumentSelected: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(DocumentCategory.ALL) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val filtered = remember(documents, query, category) {
        DocumentSearch.search(documents, query, category)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri
    }

    AppScreenScaffold(
        title = stringResource(R.string.vault_title),
        onSettings = onSettings,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isDemo) onStartVault()
                    else picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
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
            if (isDemo) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SampleWorkspaceBanner(onStartVault)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    label = { Text(stringResource(R.string.vault_search_hint)) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    items(DocumentCategory.entries.size) { index ->
                        val candidate = DocumentCategory.entries[index]
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(stringResource(candidate.labelResource())) },
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
                        Text(stringResource(document.category.labelResource()))
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
    today: LocalDate,
    onDismiss: () -> Unit,
    onImport: (DocumentImportDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var date by remember { mutableStateOf(today) }
    var category by remember { mutableStateOf(DocumentCategory.OTHER) }
    var categoryExpanded by remember { mutableStateOf(false) }

    FormDialog(
        title = stringResource(R.string.import_document),
        saveEnabled = title.isNotBlank() && source.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onImport(
                DocumentImportDraft(
                    uri = uri,
                    title = title.trim(),
                    category = category,
                    documentDate = date,
                    source = source.trim(),
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
                    value = stringResource(category.labelResource()),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    label = { Text(stringResource(R.string.document_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    DocumentCategory.entries.filterNot { it == DocumentCategory.ALL }.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(stringResource(candidate.labelResource())) },
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

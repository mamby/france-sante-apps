package net.mamby.health.feature.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.asReference
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.EditorFieldPair
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedLabel

data class DocumentImportDraft(
    val profileId: UUID,
    val uri: Uri?,
    val pickerRequested: Boolean,
    val title: String,
    val category: DocumentCategoryRef,
    val documentDate: LocalDate,
    val source: String,
    val notes: String?,
    val tags: List<String>,
)

data class DocumentEditorDraft(
    val title: String,
    val category: DocumentCategoryRef,
    val documentDate: LocalDate,
    val source: String,
    val notes: String,
    val tags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentImportEditorScreen(
    records: List<ProfileRecord>,
    initialProfileId: UUID,
    today: LocalDate,
    onCancel: () -> Unit,
    onImport: (UUID, DocumentImportDraft, (Boolean) -> Unit) -> Unit,
) {
    val initialRecord = records.firstOrNull { it.profile.id == initialProfileId }
    val initialCategories = remember(initialRecord) { availableDocumentCategories(initialRecord) }
    val editorState = rememberEditorState {
        DocumentImportDraft(
            profileId = initialProfileId,
            uri = null,
            pickerRequested = false,
            title = "",
            category = initialCategories.firstOrNull()
                ?: BuiltInDocumentCategory.OTHER.asReference(),
            documentDate = today,
            source = "",
            notes = null,
            tags = emptyList(),
        )
    }
    val draft = editorState.value
    val selectedRecord = records.firstOrNull { it.profile.id == draft.profileId }
    val categoryRecord = selectedRecord ?: initialRecord
    val availableCategories = remember(categoryRecord) {
        availableDocumentCategories(categoryRecord)
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            if (editorState.value.uri == null) onCancel()
        } else {
            editorState.value = editorState.value.copy(uri = uri)
        }
    }

    LaunchedEffect(editorState, draft.pickerRequested) {
        if (!draft.pickerRequested) {
            editorState.value = draft.copy(pickerRequested = true)
            picker.launch(DOCUMENT_MIME_TYPES)
        }
    }
    LaunchedEffect(selectedRecord, availableCategories) {
        if (selectedRecord != null && draft.category !in availableCategories) {
            editorState.value = editorState.value.copy(
                category = availableCategories.firstOrNull()
                    ?: BuiltInDocumentCategory.OTHER.asReference(),
            )
        }
    }

    val isDirty = draft.copy(pickerRequested = false) !=
        editorState.initialValue.copy(pickerRequested = false)
    AppEditorScaffold(
        title = stringResource(R.string.import_document),
        isDirty = isDirty,
        saveEnabled = selectedRecord != null &&
            draft.uri != null &&
            draft.title.isNotBlank() &&
            draft.source.isNotBlank() &&
            draft.category in availableCategories,
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            if (draft.uri == null) return@AppEditorScaffold
            editorState.isSaving = true
            onImport(
                draft.profileId,
                draft.copy(
                    title = draft.title.trim(),
                    source = draft.source.trim(),
                    notes = draft.notes.orEmpty().trim().ifBlank { null },
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.document_file)) {
            selectedRecord?.let { ProfileOwnerHeader(it.profile) }
            Text(stringResource(R.string.import_limit))
            OutlinedTextField(
                value = draft.title,
                onValueChange = { editorState.value = draft.copy(title = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_title)) },
                singleLine = true,
            )
            EditorFieldPair(
                first = { modifier ->
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = modifier,
                    ) {
                        OutlinedTextField(
                            value = draft.category.editorLabel(categoryRecord),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            label = { Text(stringResource(R.string.document_category)) },
                            trailingIcon = { DropdownTrailingIcon(categoryExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            availableCategories.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.editorLabel(categoryRecord)) },
                                    onClick = {
                                        editorState.value = draft.copy(category = candidate)
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                second = { modifier ->
                    Box(modifier) {
                        DateField(
                            stringResource(R.string.document_date),
                            draft.documentDate,
                            { editorState.value = draft.copy(documentDate = it) },
                        )
                    }
                },
            )
            OutlinedTextField(
                value = draft.source,
                onValueChange = { editorState.value = draft.copy(source = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_source)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.notes.orEmpty(),
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_notes)) },
                minLines = 2,
            )
            StringListEditor(
                stringResource(R.string.document_tags),
                draft.tags,
                { editorState.value = draft.copy(tags = it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    document: MedicalDocument,
    record: ProfileRecord,
    onCancel: () -> Unit,
    onSave: (MedicalDocument, (Boolean) -> Unit) -> Unit,
) {
    val availableCategories = remember(record) { availableDocumentCategories(record) }
    val editorState = rememberEditorState {
        DocumentEditorDraft(
            title = document.title,
            category = document.category,
            documentDate = document.documentDate,
            source = document.source,
            notes = document.notes.orEmpty(),
            tags = document.tags,
        )
    }
    val draft = editorState.value
    var categoryExpanded by remember { mutableStateOf(false) }

    AppEditorScaffold(
        title = stringResource(R.string.edit_document),
        isDirty = editorState.isDirty,
        saveEnabled = draft.title.isNotBlank() &&
            draft.source.isNotBlank() &&
            draft.category in availableCategories,
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            editorState.isSaving = true
            onSave(
                document.copy(
                    title = draft.title.trim(),
                    category = draft.category,
                    documentDate = draft.documentDate,
                    source = draft.source.trim(),
                    notes = draft.notes.trim().ifBlank { null },
                    tags = draft.tags,
                ),
            ) { succeeded ->
                editorState.isSaving = false
                if (succeeded) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.document_file)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { editorState.value = draft.copy(title = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_title)) },
                singleLine = true,
            )
            EditorFieldPair(
                first = { modifier ->
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = modifier,
                    ) {
                        OutlinedTextField(
                            value = draft.category.localizedLabel(record),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            label = { Text(stringResource(R.string.document_category)) },
                            trailingIcon = { DropdownTrailingIcon(categoryExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            availableCategories.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.localizedLabel(record)) },
                                    onClick = {
                                        editorState.value = draft.copy(category = candidate)
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                second = { modifier ->
                    Box(modifier) {
                        DateField(
                            stringResource(R.string.document_date),
                            draft.documentDate,
                            { editorState.value = draft.copy(documentDate = it) },
                        )
                    }
                },
            )
            OutlinedTextField(
                value = draft.source,
                onValueChange = { editorState.value = draft.copy(source = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_source)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { editorState.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_notes)) },
                minLines = 2,
            )
            StringListEditor(
                stringResource(R.string.document_tags),
                draft.tags,
                { editorState.value = draft.copy(tags = it) },
            )
        }
    }
}

private fun availableDocumentCategories(record: ProfileRecord?): List<DocumentCategoryRef> =
    BuiltInDocumentCategory.entries
        .filter { builtIn ->
            record?.builtInDocumentCategoryPreferences
                ?.firstOrNull { it.category == builtIn }
                ?.isHidden != true
        }
        .map(BuiltInDocumentCategory::asReference) +
        record?.customDocumentCategories.orEmpty().map { DocumentCategoryRef.Custom(it.id) }

@Composable
private fun DocumentCategoryRef.editorLabel(record: ProfileRecord?): String = when {
    record != null -> localizedLabel(record)
    this is DocumentCategoryRef.BuiltIn -> stringResource(category.labelResource())
    else -> stringResource(R.string.category_other)
}

private val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/webp",
)

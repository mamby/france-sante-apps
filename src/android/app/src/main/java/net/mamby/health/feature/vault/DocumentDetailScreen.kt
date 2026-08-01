package net.mamby.health.feature.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.text.format.Formatter
import net.mamby.health.R
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.StringListEditor
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

sealed interface DocumentPreviewState {
    data object Idle : DocumentPreviewState
    data object Loading : DocumentPreviewState
    data class Ready(val image: ImageBitmap, val page: Int, val pageCount: Int) : DocumentPreviewState
    data class Error(val message: String) : DocumentPreviewState
}

@Composable
fun DocumentDetailScreen(
    document: MedicalDocument,
    profile: HealthProfile,
    preview: DocumentPreviewState,
    onBack: () -> Unit,
    onLoadPreview: (Int) -> Unit,
    onEdit: (MedicalDocument) -> Unit,
    onDelete: () -> Unit,
    onProfileClick: () -> Unit,
) {
    var deleteVisible by remember(profile.id) { mutableStateOf(false) }
    var editorVisible by remember(profile.id) { mutableStateOf(false) }
    val context = LocalContext.current
    AppScreenScaffold(
        title = document.title,
        onBack = onBack,
        profile = profile,
        onProfileClick = onProfileClick,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            SectionCard(stringResource(R.string.document_file)) {
                LabeledValue(stringResource(R.string.document_category), stringResource(document.category.labelResource()))
                LabeledValue(stringResource(R.string.document_date), document.documentDate.localizedDate())
                LabeledValue(stringResource(R.string.document_source), document.source)
                LabeledValue(stringResource(R.string.document_mime_type), document.mimeType)
                LabeledValue(stringResource(R.string.document_size), Formatter.formatShortFileSize(context, document.sizeBytes))
                LabeledValue(stringResource(R.string.document_notes), document.notes.orEmpty())
                LabeledValue(stringResource(R.string.document_tags), document.tags.joinToString())
            }
            SectionCard(stringResource(R.string.document_preview)) {
                when (preview) {
                    DocumentPreviewState.Idle -> Button(onClick = { onLoadPreview(0) }) {
                        Text(stringResource(R.string.document_preview))
                    }
                    DocumentPreviewState.Loading -> CircularProgressIndicator()
                    is DocumentPreviewState.Error -> Text(preview.message)
                    is DocumentPreviewState.Ready -> {
                        Image(
                            bitmap = preview.image,
                            contentDescription = stringResource(R.string.document_preview),
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                        if (preview.pageCount > 1) {
                            Text(stringResource(R.string.page_number, preview.page + 1, preview.pageCount))
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                            ) {
                                OutlinedButton(
                                    onClick = { onLoadPreview(preview.page - 1) },
                                    enabled = preview.page > 0,
                                ) { Text(stringResource(R.string.previous_page)) }
                                OutlinedButton(
                                    onClick = { onLoadPreview(preview.page + 1) },
                                    enabled = preview.page + 1 < preview.pageCount,
                                ) { Text(stringResource(R.string.next_page)) }
                            }
                        }
                    }
                }
            }
            Button(onClick = { editorVisible = true }) {
                Text(stringResource(R.string.edit_document))
            }
            OutlinedButton(onClick = { deleteVisible = true }) {
                Text(stringResource(R.string.common_delete))
            }
        }
    }
    if (editorVisible) {
        DocumentEditDialog(
            document = document,
            onDismiss = { editorVisible = false },
            onSave = {
                onEdit(it)
                editorVisible = false
            },
        )
    }
    if (deleteVisible) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_document_title),
            message = stringResource(R.string.delete_document_message),
            onDismiss = { deleteVisible = false },
            onConfirm = {
                deleteVisible = false
                onDelete()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentEditDialog(
    document: MedicalDocument,
    onDismiss: () -> Unit,
    onSave: (MedicalDocument) -> Unit,
) {
    var title by remember { mutableStateOf(document.title) }
    var source by remember { mutableStateOf(document.source) }
    var notes by remember { mutableStateOf(document.notes.orEmpty()) }
    var tags by remember { mutableStateOf(document.tags) }
    var date by remember { mutableStateOf(document.documentDate) }
    var category by remember { mutableStateOf(document.category) }
    var categoryExpanded by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(R.string.edit_document),
        saveEnabled = title.isNotBlank() && source.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                document.copy(
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
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.document_title)) })
            ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
                OutlinedTextField(
                    value = stringResource(category.labelResource()),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.document_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                )
                ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                    net.mamby.health.core.model.DocumentCategory.entries
                        .filterNot { it == net.mamby.health.core.model.DocumentCategory.ALL }
                        .forEach { candidate ->
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
            OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.document_source)) })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.document_notes)) }, minLines = 2)
            StringListEditor(stringResource(R.string.document_tags), tags, { tags = it })
        }
    }
}

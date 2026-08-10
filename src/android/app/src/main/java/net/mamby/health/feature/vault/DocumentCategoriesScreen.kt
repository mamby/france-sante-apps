package net.mamby.health.feature.vault

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.BuiltInDocumentCategoryPreference
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.asReference
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.theme.UiTokens

@Composable
fun ManageDocumentCategoriesScreen(
    record: ProfileRecord,
    onBack: () -> Unit,
    onUpdateBuiltIn: (BuiltInDocumentCategoryPreference, DocumentCategoryRef?) -> Unit,
    onUpsertCustom: (CustomDocumentCategory) -> Unit,
    onDeleteCustom: (UUID, DocumentCategoryRef?) -> Unit,
) {
    var builtInEditor by remember { mutableStateOf<BuiltInDocumentCategory?>(null) }
    var customEditor by remember { mutableStateOf<CustomDocumentCategory?>(null) }
    var adding by remember { mutableStateOf(false) }
    AppScreenScaffold(
        title = stringResource(R.string.manage_document_categories),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_document_category))
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
                ProfileOwnerHeader(record.profile)
            }
            items(BuiltInDocumentCategory.entries, key = BuiltInDocumentCategory::name) { category ->
                val preference = record.builtInDocumentCategoryPreferences
                    .firstOrNull { it.category == category }
                SectionCard(category.asReference().localizedLabel(record)) {
                    Text(stringResource(if (preference?.isHidden == true) R.string.category_hidden else R.string.category_visible))
                    OutlinedButton(onClick = { builtInEditor = category }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            items(record.customDocumentCategories, key = CustomDocumentCategory::id) { category ->
                SectionCard(category.name) {
                    Text(stringResource(R.string.custom_category))
                    OutlinedButton(onClick = { customEditor = category }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
        }
    }

    builtInEditor?.let { category ->
        val existing = record.builtInDocumentCategoryPreferences
            .firstOrNull { it.category == category }
            ?: BuiltInDocumentCategoryPreference(category)
        BuiltInCategoryDialog(
            record = record,
            existing = existing,
            onDismiss = { builtInEditor = null },
            onSave = { preference, replacement ->
                onUpdateBuiltIn(preference, replacement)
                builtInEditor = null
            },
        )
    }
    if (adding || customEditor != null) {
        CustomCategoryDialog(
            record = record,
            existing = customEditor,
            onDismiss = {
                adding = false
                customEditor = null
            },
            onSave = {
                onUpsertCustom(it)
                adding = false
                customEditor = null
            },
            onDelete = customEditor?.let { category ->
                { replacement ->
                    onDeleteCustom(category.id, replacement)
                    customEditor = null
                }
            },
        )
    }
}

@Composable
private fun BuiltInCategoryDialog(
    record: ProfileRecord,
    existing: BuiltInDocumentCategoryPreference,
    onDismiss: () -> Unit,
    onSave: (BuiltInDocumentCategoryPreference, DocumentCategoryRef?) -> Unit,
) {
    var label by remember(existing.category) { mutableStateOf(existing.labelOverride.orEmpty()) }
    var hidden by remember(existing.category) { mutableStateOf(existing.isHidden) }
    var replacement by remember(existing.category) { mutableStateOf<DocumentCategoryRef?>(null) }
    val reference = existing.category.asReference()
    val isUsed = record.documents.any { it.category == reference }
    val requiresReplacement = hidden && isUsed
    FormDialog(
        title = reference.localizedLabel(record),
        saveEnabled = !requiresReplacement || replacement != null,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                existing.copy(labelOverride = label.trim().ifBlank { null }, isHidden = hidden),
                replacement,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.category_custom_label)) },
            )
            SwitchField(stringResource(R.string.hide_category), hidden, { hidden = it })
            if (requiresReplacement) {
                ReplacementCategoryField(record, reference, replacement, { replacement = it })
            }
        }
    }
}

@Composable
private fun CustomCategoryDialog(
    record: ProfileRecord,
    existing: CustomDocumentCategory?,
    onDismiss: () -> Unit,
    onSave: (CustomDocumentCategory) -> Unit,
    onDelete: ((DocumentCategoryRef?) -> Unit)?,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var replacement by remember(existing?.id) { mutableStateOf<DocumentCategoryRef?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val reference = existing?.let { DocumentCategoryRef.Custom(it.id) }
    val isUsed = reference != null && record.documents.any { it.category == reference }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_document_category else R.string.edit_document_category),
        saveEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CustomDocumentCategory(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = name.trim(),
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.category_name)) })
            if (isUsed) {
                ReplacementCategoryField(record, requireNotNull(reference), replacement, { replacement = it })
            }
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !isUsed || replacement != null) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            stringResource(R.string.delete_document_category_title),
            stringResource(R.string.delete_document_category_message),
            { confirmDelete = false },
            {
                confirmDelete = false
                onDelete?.invoke(replacement)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplacementCategoryField(
    record: ProfileRecord,
    excluded: DocumentCategoryRef,
    selected: DocumentCategoryRef?,
    onSelected: (DocumentCategoryRef) -> Unit,
) {
    val choices = BuiltInDocumentCategory.entries
        .map(BuiltInDocumentCategory::asReference)
        .filter { it != excluded && record.isCategoryVisible(it) } +
        record.customDocumentCategories.map { DocumentCategoryRef.Custom(it.id) }.filter { it != excluded }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = selected?.localizedLabel(record).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.replacement_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.localizedLabel(record)) },
                    onClick = {
                        onSelected(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ProfileRecord.isCategoryVisible(reference: DocumentCategoryRef): Boolean = when (reference) {
    is DocumentCategoryRef.BuiltIn -> builtInDocumentCategoryPreferences
        .firstOrNull { it.category == reference.category }
        ?.isHidden != true
    is DocumentCategoryRef.Custom -> customDocumentCategories.any { it.id == reference.id }
}

package net.mamby.health.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.VaultContact
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.EditorFieldPair
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens

@Composable
fun ContactsScreen(
    contacts: List<VaultContact>,
    onAdd: () -> Unit,
    onSelected: (UUID) -> Unit,
) {
    val sortedContacts = remember(contacts) {
        contacts.sortedWith(
            compareBy<VaultContact> { it.name.lowercase(Locale.getDefault()) }
                .thenBy(VaultContact::id),
        )
    }

    AppScreenScaffold(
        title = stringResource(R.string.contacts_title),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_plus),
                    contentDescription = stringResource(R.string.add_contact),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = padding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            if (sortedContacts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.no_contacts_title),
                        body = stringResource(R.string.no_contacts_body),
                    )
                }
            } else {
                items(sortedContacts, key = VaultContact::id) { contact ->
                    SectionCard(contact.name) {
                        contact.firstContactValue()?.let { Text(it) }
                        Button(onClick = { onSelected(contact.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactDetailScreen(
    contact: VaultContact,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDialPhone: (String) -> Unit,
    onComposeEmail: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onSearchAddress: (String) -> Unit,
) {
    var deleteVisible by remember(contact.id) { mutableStateOf(false) }

    AppScreenScaffold(
        title = contact.name,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .consumeWindowInsets(padding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            PageHeader()
            SectionCard(stringResource(R.string.contact_details)) {
                ContactActionGroup(
                    label = stringResource(R.string.contact_phone_numbers),
                    values = contact.phoneNumbers,
                    actionLabel = { stringResource(R.string.contact_phone_action, it) },
                    onClick = onDialPhone,
                )
                ContactActionGroup(
                    label = stringResource(R.string.contact_email_addresses),
                    values = contact.emailAddresses,
                    actionLabel = { stringResource(R.string.contact_email_action, it) },
                    onClick = onComposeEmail,
                )
                ContactActionGroup(
                    label = stringResource(R.string.contact_websites),
                    values = contact.websites,
                    actionLabel = { stringResource(R.string.contact_website_action, it) },
                    onClick = onOpenWebsite,
                )
                ContactActionGroup(
                    label = stringResource(R.string.contact_addresses),
                    values = contact.addresses,
                    actionLabel = { stringResource(R.string.contact_address_action, it) },
                    onClick = onSearchAddress,
                )
                LabeledValue(
                    label = stringResource(R.string.common_notes),
                    value = contact.notes.orEmpty(),
                )
            }
            Button(onClick = onEdit) {
                Text(stringResource(R.string.common_edit))
            }
            OutlinedButton(onClick = { deleteVisible = true }) {
                Text(stringResource(R.string.common_delete))
            }
        }
    }

    if (deleteVisible) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_saved_contact_title),
            message = stringResource(R.string.delete_saved_contact_message),
            onDismiss = { deleteVisible = false },
            onConfirm = {
                deleteVisible = false
                onDelete()
            },
        )
    }
}

@Composable
private fun ContactActionGroup(
    label: String,
    values: List<String>,
    actionLabel: @Composable (String) -> String,
    onClick: (String) -> Unit,
) {
    val nonBlankValues = values.filter(String::isNotBlank)
    if (nonBlankValues.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            nonBlankValues.forEach { value ->
                val accessibilityLabel = actionLabel(value)
                TextButton(
                    onClick = { onClick(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = accessibilityLabel
                            onClick(label = accessibilityLabel, action = null)
                        },
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
fun ContactEditorScreen(
    existing: VaultContact?,
    onCancel: () -> Unit,
    onSave: (VaultContact, (Boolean) -> Unit) -> Unit,
) {
    val state = rememberEditorState {
        ContactDraft(
            id = existing?.id ?: UUID.randomUUID(),
            name = existing?.name.orEmpty(),
            phoneNumbers = existing?.phoneNumbers.toEditorValues(),
            emailAddresses = existing?.emailAddresses.toEditorValues(),
            websites = existing?.websites.toEditorValues(),
            addresses = existing?.addresses.toEditorValues(),
            notes = existing?.notes.orEmpty(),
            updatedAt = existing?.updatedAt ?: Instant.EPOCH,
        )
    }
    val draft = state.value
    val websitesValid = draft.websites.all { it.isBlank() || normalizeWebsite(it) != null }

    AppEditorScaffold(
        title = stringResource(if (existing == null) R.string.add_contact else R.string.edit_contact),
        isDirty = state.isDirty,
        saveEnabled = draft.name.isNotBlank() && websitesValid,
        isSaving = state.isSaving,
        onCancel = onCancel,
        onSave = {
            state.isSaving = true
            onSave(
                VaultContact(
                    id = draft.id,
                    name = draft.name.trim(),
                    phoneNumbers = draft.phoneNumbers.normalizedValues(),
                    emailAddresses = draft.emailAddresses.normalizedValues(),
                    websites = draft.websites.mapNotNull(::normalizeWebsite).deduplicatedValues(),
                    addresses = draft.addresses.normalizedValues(),
                    notes = draft.notes.trim().ifBlank { null },
                    updatedAt = draft.updatedAt,
                ),
            ) { saved ->
                state.isSaving = false
                if (saved) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.editor_section_basic)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { state.value = draft.copy(name = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contact_name)) },
                singleLine = true,
            )
        }
        EditorSection(stringResource(R.string.editor_section_contact_channels)) {
            EditorFieldPair(
                first = { modifier ->
                    ContactValueEditor(
                        label = stringResource(R.string.contact_phone_numbers),
                        values = draft.phoneNumbers,
                        onValuesChange = { state.value = draft.copy(phoneNumbers = it) },
                        keyboardType = KeyboardType.Phone,
                        modifier = modifier,
                    )
                },
                second = { modifier ->
                    ContactValueEditor(
                        label = stringResource(R.string.contact_email_addresses),
                        values = draft.emailAddresses,
                        onValuesChange = { state.value = draft.copy(emailAddresses = it) },
                        keyboardType = KeyboardType.Email,
                        modifier = modifier,
                    )
                },
            )
        }
        EditorSection(stringResource(R.string.editor_section_location_online)) {
            EditorFieldPair(
                first = { modifier ->
                    ContactValueEditor(
                        label = stringResource(R.string.contact_websites),
                        values = draft.websites,
                        onValuesChange = { state.value = draft.copy(websites = it) },
                        keyboardType = KeyboardType.Uri,
                        modifier = modifier,
                        invalidValueMessage = stringResource(R.string.invalid_contact_website),
                        isValid = { it.isBlank() || normalizeWebsite(it) != null },
                    )
                },
                second = { modifier ->
                    ContactValueEditor(
                        label = stringResource(R.string.contact_addresses),
                        values = draft.addresses,
                        onValuesChange = { state.value = draft.copy(addresses = it) },
                        keyboardType = KeyboardType.Text,
                        modifier = modifier,
                        multiline = true,
                    )
                },
            )
        }
        EditorSection(stringResource(R.string.common_notes)) {
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { state.value = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.common_notes)) },
                minLines = 3,
            )
        }
    }
}

@Composable
private fun ContactValueEditor(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    invalidValueMessage: String? = null,
    isValid: (String) -> Boolean = { true },
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        values.forEachIndexed { index, value ->
            val valid = isValid(value)
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        onValuesChange(values.toMutableList().apply { this[index] = next })
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(label) },
                    singleLine = !multiline,
                    minLines = if (multiline) 2 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    isError = !valid,
                    supportingText = invalidValueMessage
                        ?.takeIf { !valid }
                        ?.let { message -> ({ Text(message) }) },
                )
                val removeLabel = stringResource(
                    R.string.remove_contact_value,
                    value.ifBlank { label },
                )
                IconButton(
                    onClick = {
                        onValuesChange(values.toMutableList().apply { removeAt(index) })
                    },
                    modifier = Modifier.semantics {
                        contentDescription = removeLabel
                        onClick(label = removeLabel, action = null)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = null,
                    )
                }
            }
        }
        TextButton(onClick = { onValuesChange(values + "") }) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_plus),
                contentDescription = null,
            )
            Text(stringResource(R.string.add_another))
        }
    }
}

private data class ContactDraft(
    val id: UUID,
    val name: String,
    val phoneNumbers: List<String>,
    val emailAddresses: List<String>,
    val websites: List<String>,
    val addresses: List<String>,
    val notes: String,
    val updatedAt: Instant,
)

private fun VaultContact.firstContactValue(): String? = sequenceOf(
    phoneNumbers,
    emailAddresses,
    websites,
    addresses,
).flatten().firstOrNull(String::isNotBlank)

private fun List<String>?.toEditorValues(): List<String> =
    this?.takeIf(List<String>::isNotEmpty) ?: listOf("")

private fun List<String>.normalizedValues(): List<String> =
    map(String::trim).filter(String::isNotEmpty).deduplicatedValues()

private fun List<String>.deduplicatedValues(): List<String> {
    val seen = mutableSetOf<String>()
    return filter { seen.add(it.lowercase(Locale.ROOT)) }
}

private fun normalizeWebsite(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if (EXPLICIT_URI_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val normalized = runCatching { URI(candidate) }.getOrNull() ?: return null
    return candidate.takeIf {
        normalized.isAbsolute && normalized.host != null &&
            (normalized.scheme.equals("http", ignoreCase = true) ||
                normalized.scheme.equals("https", ignoreCase = true))
    }
}

private val EXPLICIT_URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

package net.mamby.health.feature.notes

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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthNote
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DetailTitleBarActions
import net.mamby.health.ui.components.DetailSection
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FloatingAddButton
import net.mamby.health.ui.components.ListCard
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun NotesScreen(
    notes: List<HealthNote>,
    zoneId: ZoneId,
    onAdd: () -> Unit,
    onSelected: (UUID) -> Unit,
) {
    val sortedNotes = remember(notes) {
        notes.sortedWith(compareByDescending(HealthNote::notedAt).thenBy(HealthNote::id))
    }
    AppScreenScaffold(
        title = stringResource(R.string.health_notes_title),
        floatingActionButton = {
            FloatingAddButton(
                label = stringResource(R.string.add_health_note),
                onClick = onAdd,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = padding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            if (sortedNotes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_health_notes_title),
                        stringResource(R.string.no_health_notes_body),
                    )
                }
            } else {
                items(sortedNotes, key = HealthNote::id) { note ->
                    ListCard(
                        title = note.title,
                        onClick = { onSelected(note.id) },
                    ) {
                        Text(note.notedAt.localizedDateTime(zoneId))
                        Text(note.body)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteDetailScreen(
    note: HealthNote,
    zoneId: ZoneId,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var deleting by remember(note.id) { mutableStateOf(false) }
    AppScreenScaffold(
        title = note.title,
        onBack = onBack,
        actions = {
            DetailTitleBarActions(
                onEdit = onEdit,
                onDelete = { deleting = true },
            )
        },
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
            DetailSection(stringResource(R.string.health_note)) {
                Text(note.notedAt.localizedDateTime(zoneId))
                Text(note.body)
            }
        }
    }
    if (deleting) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_health_note_title),
            message = stringResource(R.string.delete_health_note_message),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

@Composable
fun HealthNoteEditorScreen(
    existing: HealthNote?,
    now: Instant,
    zoneId: ZoneId,
    onCancel: () -> Unit,
    onSave: (HealthNote, (Boolean) -> Unit) -> Unit,
) {
    val state = rememberEditorState {
        HealthNoteDraft(
            id = existing?.id ?: UUID.randomUUID(),
            title = existing?.title.orEmpty(),
            body = existing?.body.orEmpty(),
            notedAt = existing?.notedAt ?: now,
            updatedAt = existing?.updatedAt ?: Instant.EPOCH,
        )
    }
    val draft = state.value
    AppEditorScaffold(
        title = stringResource(if (existing == null) R.string.new_health_note else R.string.edit_health_note),
        isDirty = state.isDirty,
        saveEnabled = draft.title.isNotBlank() && draft.body.isNotBlank(),
        isSaving = state.isSaving,
        onCancel = onCancel,
        onSave = {
            state.isSaving = true
            onSave(
                HealthNote(
                    id = draft.id,
                    title = draft.title.trim(),
                    body = draft.body.trim(),
                    notedAt = draft.notedAt,
                    updatedAt = draft.updatedAt,
                ),
            ) { saved ->
                state.isSaving = false
                if (saved) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.health_note)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { state.value = draft.copy(title = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.health_note_title)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.body,
                onValueChange = { state.value = draft.copy(body = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.health_note_body)) },
                minLines = 4,
            )
        }
    }
}

private data class HealthNoteDraft(
    val id: UUID,
    val title: String,
    val body: String,
    val notedAt: Instant,
    val updatedAt: Instant,
)

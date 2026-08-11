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
import androidx.compose.material3.Button
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
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthNote
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun NotesScreen(
    notes: List<HealthNote>,
    now: Instant,
    zoneId: ZoneId,
    onUpsert: (HealthNote) -> Unit,
    onSelected: (UUID) -> Unit,
    creationRequest: Long = 0,
) {
    var creationVisible by remember { mutableStateOf(false) }
    fun startCreation() { creationVisible = true }
    LaunchedEffect(creationRequest) {
        if (creationRequest > 0) startCreation()
    }
    val sortedNotes = remember(notes) {
        notes.sortedWith(compareByDescending(HealthNote::notedAt).thenBy(HealthNote::id))
    }
    AppScreenScaffold(
        title = stringResource(R.string.health_notes_title),
        floatingActionButton = {
            FloatingActionButton(onClick = ::startCreation) {
                Icon(painterResource(R.drawable.ic_lucide_plus), stringResource(R.string.add_health_note))
            }
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
                    SectionCard(note.title) {
                        Text(note.notedAt.localizedDateTime(zoneId))
                        Text(note.body)
                        Button(onClick = { onSelected(note.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
    if (creationVisible) {
        HealthNoteDialog(
            existing = null,
            now = now,
            zoneId = zoneId,
            onDismiss = { creationVisible = false },
            onSave = {
                onUpsert(it)
                creationVisible = false
            },
        )
    }
}

@Composable
fun NoteDetailScreen(
    note: HealthNote,
    now: Instant,
    zoneId: ZoneId,
    onBack: (() -> Unit)?,
    onUpsert: (HealthNote) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(note.id) { mutableStateOf(false) }
    var deleting by remember(note.id) { mutableStateOf(false) }
    AppScreenScaffold(
        title = note.title,
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
            SectionCard(stringResource(R.string.health_note)) {
                Text(note.notedAt.localizedDateTime(zoneId))
                Text(note.body)
            }
            Button(onClick = { editing = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editing) {
        HealthNoteDialog(
            existing = note,
            now = now,
            zoneId = zoneId,
            onDismiss = { editing = false },
            onSave = {
                onUpsert(it)
                editing = false
            },
        )
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
private fun HealthNoteDialog(
    existing: HealthNote?,
    now: Instant,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (HealthNote) -> Unit,
) {
    val initial = existing?.notedAt ?: now
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var date by remember(existing?.id) { mutableStateOf(initial.atZone(zoneId).toLocalDate()) }
    var time by remember(existing?.id) { mutableStateOf(initial.atZone(zoneId).toLocalTime()) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_health_note else R.string.edit_health_note),
        saveEnabled = title.isNotBlank() && body.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                HealthNote(
                    id = existing?.id ?: UUID.randomUUID(),
                    title = title.trim(),
                    body = body.trim(),
                    notedAt = date.atTime(time).atZone(zoneId).toInstant(),
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.health_note_title)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.health_note_body)) },
                minLines = 4,
            )
            DateField(stringResource(R.string.health_note_date), date, { date = it })
            TimeField(stringResource(R.string.health_note_time), time, { time = it })
        }
    }
}

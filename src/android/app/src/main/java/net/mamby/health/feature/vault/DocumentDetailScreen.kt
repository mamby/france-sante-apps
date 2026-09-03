package net.mamby.health.feature.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.detailTitleBarActions
import net.mamby.health.ui.components.DetailSection
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

sealed interface DocumentPreviewState {
    data object Idle : DocumentPreviewState
    data class Loading(val profileId: UUID, val documentId: UUID) : DocumentPreviewState
    data class Ready(
        val profileId: UUID,
        val documentId: UUID,
        val image: ImageBitmap,
        val page: Int,
        val pageCount: Int,
    ) : DocumentPreviewState
    data class Error(val profileId: UUID, val documentId: UUID, val message: String) : DocumentPreviewState
}

@Composable
fun DocumentDetailScreen(
    document: MedicalDocument,
    record: ProfileRecord,
    preview: DocumentPreviewState,
    onBack: (() -> Unit)?,
    onLoadPreview: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val profile = record.profile
    var deleteVisible by remember(profile.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val visiblePreview = preview.takeIf { state ->
        when (state) {
            DocumentPreviewState.Idle -> true
            is DocumentPreviewState.Loading ->
                state.profileId == profile.id && state.documentId == document.id
            is DocumentPreviewState.Ready ->
                state.profileId == profile.id && state.documentId == document.id
            is DocumentPreviewState.Error ->
                state.profileId == profile.id && state.documentId == document.id
        }
    } ?: DocumentPreviewState.Idle
    AppScreenScaffold(
        title = document.title,
        onBack = onBack,
        actions = detailTitleBarActions(
            onEdit = onEdit,
            onDelete = { deleteVisible = true },
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            ProfileOwnerHeader(profile)
            DetailSection(stringResource(R.string.document_file)) {
                LabeledValue(stringResource(R.string.document_category), document.category.localizedLabel(record))
                LabeledValue(stringResource(R.string.document_date), document.documentDate.localizedDate())
                LabeledValue(stringResource(R.string.document_source), document.source)
                LabeledValue(stringResource(R.string.document_mime_type), document.mimeType)
                LabeledValue(stringResource(R.string.document_size), Formatter.formatShortFileSize(context, document.sizeBytes))
                LabeledValue(stringResource(R.string.document_notes), document.notes.orEmpty())
                LabeledValue(stringResource(R.string.document_tags), document.tags.joinToString())
            }
            DetailSection(stringResource(R.string.document_preview)) {
                when (visiblePreview) {
                    DocumentPreviewState.Idle -> Button(onClick = { onLoadPreview(0) }) {
                        Text(stringResource(R.string.document_preview))
                    }
                    is DocumentPreviewState.Loading -> CircularProgressIndicator()
                    is DocumentPreviewState.Error -> Text(visiblePreview.message)
                    is DocumentPreviewState.Ready -> {
                        Image(
                            bitmap = visiblePreview.image,
                            contentDescription = stringResource(R.string.document_preview),
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                        if (visiblePreview.pageCount > 1) {
                            Text(
                                stringResource(
                                    R.string.page_number,
                                    visiblePreview.page + 1,
                                    visiblePreview.pageCount,
                                ),
                            )
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                            ) {
                                OutlinedButton(
                                    onClick = { onLoadPreview(visiblePreview.page - 1) },
                                    enabled = visiblePreview.page > 0,
                                ) { Text(stringResource(R.string.previous_page)) }
                                OutlinedButton(
                                    onClick = { onLoadPreview(visiblePreview.page + 1) },
                                    enabled = visiblePreview.page + 1 < visiblePreview.pageCount,
                                ) { Text(stringResource(R.string.next_page)) }
                            }
                        }
                    }
                }
            }
        }
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

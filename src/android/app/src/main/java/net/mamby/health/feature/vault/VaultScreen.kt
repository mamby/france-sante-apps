package net.mamby.health.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FloatingAddButton
import net.mamby.health.ui.components.ListCard
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.titleBarAction
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

@Composable
fun VaultScreen(
    records: List<ProfileRecord>,
    onBack: () -> Unit,
    onManageCategories: (UUID?) -> Unit,
    onImportRequested: (UUID?) -> Unit,
    onDocumentSelected: (UUID, String) -> Unit,
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
    AppScreenScaffold(
        title = stringResource(R.string.documents_tab),
        onBack = onBack,
        actions = listOf(
            titleBarAction(
                label = stringResource(R.string.manage_document_categories),
                icon = R.drawable.ic_lucide_sliders_horizontal,
                onClick = { onManageCategories(filterProfileId) },
            ),
        ),
        floatingActionButton = {
            FloatingAddButton(
                label = stringResource(R.string.import_document),
                onClick = {
                    onImportRequested(filterProfileId ?: records.singleOrNull()?.profile?.id)
                },
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
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
                    ListCard(
                        title = document.title,
                        onClick = { onDocumentSelected(owned.profileId, document.id.toString()) },
                    ) {
                        if (filterProfileId == null && records.size > 1) ProfileMarker(owned.profile)
                        Text(document.category.localizedLabel(owned.record))
                        Text(document.documentDate.localizedDate())
                        Text(document.source)
                    }
                }
            }
        }
    }
}

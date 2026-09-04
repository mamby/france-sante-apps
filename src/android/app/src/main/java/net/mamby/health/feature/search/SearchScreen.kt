package net.mamby.health.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthSearch
import net.mamby.health.core.model.HealthSearchGroup
import net.mamby.health.core.model.HealthSearchResult
import net.mamby.health.core.model.HealthSearchScope
import net.mamby.health.core.model.HealthSearchTarget
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.VaultContact
import net.mamby.androidkit.compose.layout.AndroidKitPage
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.ListCard
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens
import net.mamby.health.ui.format.localizedLabel

enum class SearchFilter { ALL, HEALTH_RECORDS, NOTES, CONTACTS, MEDICATIONS, SCHEDULE }

@Composable
fun SearchScreen(
    records: List<ProfileRecord>,
    notes: List<HealthNote>,
    schedules: List<Schedule>,
    contacts: List<VaultContact>,
    onResultSelected: (HealthSearchResult) -> Unit,
    query: String,
    filter: SearchFilter,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (SearchFilter) -> Unit,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
    val recordsById = remember(records) { records.associateBy { it.profile.id } }
    val selectedFilterRecord = filterProfileId?.let(recordsById::get)
    val effectiveProfileId = selectedFilterRecord?.profile?.id
    val filteredRecords = selectedFilterRecord?.let { listOf(it) } ?: records
    val results = remember(filteredRecords, notes, schedules, contacts, query, filter) {
        HealthSearch.search(filteredRecords, notes, schedules, contacts, query).filter { result ->
            when (filter) {
                SearchFilter.ALL -> true
                SearchFilter.HEALTH_RECORDS -> result.group == HealthSearchGroup.HEALTH_RECORDS
                SearchFilter.NOTES -> result.group == HealthSearchGroup.NOTES
                SearchFilter.CONTACTS -> result.group == HealthSearchGroup.CONTACTS
                SearchFilter.MEDICATIONS -> result.group == HealthSearchGroup.MEDICATIONS
                SearchFilter.SCHEDULE -> result.group == HealthSearchGroup.SCHEDULE
            }
        }
    }

    AndroidKitPage(
        title = stringResource(R.string.search_title),
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .fitInside(WindowInsetsRulers.Ime.current)
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileFilterChip(records, effectiveProfileId, { filterProfileId = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_lucide_search),
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            stringResource(
                                if (effectiveProfileId == null) R.string.search_hint_all else R.string.search_hint,
                            ),
                        )
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    items(SearchFilter.entries.size) { index ->
                        val candidate = SearchFilter.entries[index]
                        FilterChip(
                            selected = filter == candidate,
                            onClick = { onFilterChanged(candidate) },
                            label = { Text(stringResource(candidate.labelResource())) },
                        )
                    }
                }
            }
            when {
                query.isBlank() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        if (effectiveProfileId == null) {
                            stringResource(R.string.search_initial_title_all)
                        } else {
                            stringResource(
                                R.string.search_initial_title,
                                selectedFilterRecord.profile.displayName,
                            )
                        },
                        stringResource(R.string.search_initial_body),
                    )
                }
                results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.search_no_results_title),
                        if (effectiveProfileId == null) {
                            stringResource(R.string.search_no_results_body_all)
                        } else {
                            stringResource(
                                R.string.search_no_results_body,
                                selectedFilterRecord.profile.displayName,
                            )
                        },
                    )
                }
                else -> items(results, key = { "${it.scope}:${it.target}" }) { result ->
                    val owner = (result.scope as? HealthSearchScope.Profile)
                        ?.profileId
                        ?.let(recordsById::get)
                    val primaryText = when (val target = result.target) {
                        is HealthSearchTarget.Measurement -> owner?.let { record ->
                            record.measurements
                                .firstOrNull { it.id == target.id }
                                ?.type
                                ?.localizedLabel(record)
                        }
                            ?: result.primaryText
                        else -> result.primaryText
                    }
                    ListCard(
                        title = primaryText,
                        onClick = { onResultSelected(result) },
                    ) {
                        if (owner != null && effectiveProfileId == null && records.size > 1) {
                            ProfileMarker(owner.profile)
                        }
                        result.secondaryText?.takeIf(String::isNotBlank)?.let { Text(it) }
                    }
                }
            }
        }
    }
}

private fun SearchFilter.labelResource(): Int = when (this) {
    SearchFilter.ALL -> R.string.search_filter_all
    SearchFilter.HEALTH_RECORDS -> R.string.nav_health_records
    SearchFilter.NOTES -> R.string.nav_notes
    SearchFilter.CONTACTS -> R.string.contacts_title
    SearchFilter.MEDICATIONS -> R.string.nav_medications
    SearchFilter.SCHEDULE -> R.string.schedule_title
}

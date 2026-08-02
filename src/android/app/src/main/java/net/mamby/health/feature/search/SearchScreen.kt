package net.mamby.health.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.core.model.HealthSearch
import net.mamby.health.core.model.HealthSearchGroup
import net.mamby.health.core.model.HealthSearchResult
import net.mamby.health.core.model.HealthSearchTarget
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.theme.UiTokens
import net.mamby.health.ui.format.localizedLabel

enum class SearchFilter { ALL, HEALTH_RECORDS, MEDICATIONS, APPOINTMENTS }

@Composable
fun SearchScreen(
    record: ProfileRecord,
    onProfileClick: () -> Unit,
    onSettings: () -> Unit,
    onResultSelected: (HealthSearchResult) -> Unit,
    query: String,
    filter: SearchFilter,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (SearchFilter) -> Unit,
) {
    val results = remember(record, query, filter) {
        HealthSearch.search(record, query).filter { result ->
            when (filter) {
                SearchFilter.ALL -> true
                SearchFilter.HEALTH_RECORDS -> result.group == HealthSearchGroup.HEALTH_RECORDS
                SearchFilter.MEDICATIONS -> result.group == HealthSearchGroup.MEDICATIONS
                SearchFilter.APPOINTMENTS -> result.group == HealthSearchGroup.APPOINTMENTS
            }
        }
    }

    AppScreenScaffold(
        title = stringResource(R.string.search_title),
        onSettings = onSettings,
        profile = record.profile,
        onProfileClick = onProfileClick,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.search_hint)) },
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
                        stringResource(R.string.search_initial_title, record.profile.displayName),
                        stringResource(R.string.search_initial_body),
                    )
                }
                results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.search_no_results_title),
                        stringResource(R.string.search_no_results_body, record.profile.displayName),
                    )
                }
                else -> items(results, key = { "${it.target}:${it.primaryText}" }) { result ->
                    val primaryText = when (val target = result.target) {
                        is HealthSearchTarget.Measurement -> record.measurements
                            .firstOrNull { it.id == target.id }
                            ?.type
                            ?.localizedLabel(record)
                            ?: result.primaryText
                        else -> result.primaryText
                    }
                    SectionCard(primaryText) {
                        result.secondaryText?.takeIf(String::isNotBlank)?.let { Text(it) }
                        androidx.compose.material3.TextButton(onClick = { onResultSelected(result) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
}

private fun SearchFilter.labelResource(): Int = when (this) {
    SearchFilter.ALL -> R.string.search_filter_all
    SearchFilter.HEALTH_RECORDS -> R.string.nav_health_records
    SearchFilter.MEDICATIONS -> R.string.nav_medications
    SearchFilter.APPOINTMENTS -> R.string.nav_appointments
}

package net.mamby.health.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.ProfileRecord
import net.mamby.androidkit.compose.layout.AndroidKitPage
import net.mamby.health.ui.components.ListCard
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens

@Composable
fun HealthRecordsHubScreen(
    records: List<ProfileRecord>,
    onHealthInfo: (UUID) -> Unit,
    onAddHealthInfo: () -> Unit,
    onMeasurements: () -> Unit,
    onDocuments: () -> Unit,
) {
    AndroidKitPage(
        title = stringResource(R.string.health_records_title),
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = padding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            if (records.isEmpty()) {
                item {
                    HubCard(
                        title = stringResource(R.string.health_info_title),
                        body = stringResource(R.string.health_info_hub_body),
                        actionLabel = stringResource(R.string.start_new),
                        onOpen = onAddHealthInfo,
                    )
                }
            }
            items(records, key = { it.profile.id }) { record ->
                HubCard(
                    title = stringResource(R.string.health_info_title),
                    body = stringResource(R.string.health_info_hub_body),
                    profile = record.takeIf { records.size > 1 },
                    onOpen = { onHealthInfo(record.profile.id) },
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.measurements_title),
                    body = stringResource(R.string.measurements_hub_body, records.sumOf { it.measurements.size }),
                    onOpen = onMeasurements,
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.documents_tab),
                    body = stringResource(R.string.documents_hub_body, records.sumOf { it.documents.size }),
                    onOpen = onDocuments,
                )
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    body: String,
    profile: ProfileRecord? = null,
    actionLabel: String? = null,
    onOpen: () -> Unit,
) {
    if (actionLabel == null) {
        ListCard(title = title, onClick = onOpen) {
            profile?.let { ProfileMarker(it.profile) }
            Text(body)
        }
    } else {
        SectionCard(title) {
            profile?.let { ProfileMarker(it.profile) }
            Text(body)
            Button(onClick = onOpen) {
                Text(actionLabel)
            }
        }
    }
}

package net.mamby.health.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.theme.UiTokens

@Composable
fun HealthRecordsHubScreen(
    record: ProfileRecord,
    onProfileClick: () -> Unit,
    onHealthInfo: () -> Unit,
    onMeasurements: () -> Unit,
    onNotes: () -> Unit,
    onDirectory: () -> Unit,
    onDocuments: () -> Unit,
) {
    AppScreenScaffold(
        title = stringResource(R.string.health_records_title),
        profile = record.profile,
        onProfileClick = onProfileClick,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item {
                HubCard(
                    title = stringResource(R.string.health_info_title),
                    body = stringResource(R.string.health_info_hub_body),
                    onOpen = onHealthInfo,
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.measurements_title),
                    body = stringResource(R.string.measurements_hub_body, record.measurements.size),
                    onOpen = onMeasurements,
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.health_notes_title),
                    body = stringResource(R.string.health_notes_hub_body, record.notes.size),
                    onOpen = onNotes,
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.care_directory_title),
                    body = stringResource(R.string.care_directory_hub_body, record.careDirectory.size),
                    onOpen = onDirectory,
                )
            }
            item {
                HubCard(
                    title = stringResource(R.string.documents_tab),
                    body = stringResource(R.string.documents_hub_body, record.documents.size),
                    onOpen = onDocuments,
                )
            }
        }
    }
}

@Composable
private fun HubCard(title: String, body: String, onOpen: () -> Unit) {
    SectionCard(title) {
        Text(body)
        Button(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
    }
}

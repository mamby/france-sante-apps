package net.mamby.health.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import java.time.Clock
import java.time.ZoneId
import net.mamby.health.R
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.RecurrenceCalculator
import net.mamby.health.core.model.VaultItem
import net.mamby.health.core.model.VaultItemKind
import net.mamby.health.core.model.index
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.MetricCard
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.theme.UiTokens

@Composable
fun DashboardScreen(
    record: ProfileRecord,
    clock: Clock,
    zoneId: ZoneId,
    onProfileClick: () -> Unit,
    onSettings: () -> Unit,
    onReminders: () -> Unit,
    onDocumentSelected: (String) -> Unit,
    onRecentItem: (VaultItem) -> Unit = { item ->
        if (item.kind == VaultItemKind.DOCUMENT) onDocumentSelected(item.id.toString())
    },
    onAddHealthInfo: () -> Unit,
    onImportDocument: () -> Unit,
    onAddMedication: () -> Unit,
    onAddAppointment: () -> Unit,
) {
    val now = clock.instant()
    val nextAppointment = record.appointments
        .asSequence()
        .filter { it.startsAt.isAfter(now) }
        .minByOrNull { it.startsAt }
    val nextReminder = record.reminders
        .asSequence()
        .mapNotNull { reminder ->
            RecurrenceCalculator.nextOccurrence(reminder, now, zoneId)?.let { it to reminder }
        }
        .minByOrNull { it.first }
    val activeMedications = record.medications.count { it.isActive }
    val upcomingAppointments = record.appointments.count { it.startsAt.isAfter(now) }
    val enabledReminders = record.reminders.count { it.isEnabled } +
        record.medications.count { it.isActive && it.remindersEnabled } +
        record.appointments.count { it.reminderLeadMinutes != null && it.startsAt.isAfter(now) }
    val recentItems = record.index().take(4)

    AppScreenScaffold(
        title = stringResource(R.string.dashboard_title),
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
                Text(stringResource(R.string.dashboard_greeting, record.profile.displayName))
            }

            if (record.documents.isEmpty() && record.medications.isEmpty() &&
                record.appointments.isEmpty() && record.vaccinations.isEmpty() &&
                record.notes.isEmpty() && record.measurements.isEmpty() &&
                record.careDirectory.isEmpty() && record.familyHistory.isEmpty() &&
                record.directives.isEmpty() && record.healthIdentifiers.isEmpty() &&
                record.profile.bloodType == null && record.profile.allergies.isEmpty() &&
                record.profile.chronicConditions.isEmpty() && record.profile.surgeries.isEmpty()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(stringResource(R.string.getting_started_title)) {
                        Button(onClick = onAddHealthInfo) { Text(stringResource(R.string.getting_started_health)) }
                        Button(onClick = onImportDocument) { Text(stringResource(R.string.import_document)) }
                        Button(onClick = onAddMedication) { Text(stringResource(R.string.add_medication)) }
                        Button(onClick = onAddAppointment) { Text(stringResource(R.string.add_appointment)) }
                    }
                }
            }

            item { MetricCard(stringResource(R.string.documents_metric), record.documents.size.toString()) }
            item { MetricCard(stringResource(R.string.medications_metric), activeMedications.toString()) }
            item { MetricCard(stringResource(R.string.appointments_metric), upcomingAppointments.toString()) }
            item { MetricCard(stringResource(R.string.reminders_metric), enabledReminders.toString()) }

            item {
                SectionCard(stringResource(R.string.next_appointment)) {
                    if (nextAppointment == null) {
                        Text(stringResource(R.string.no_upcoming_appointment))
                    } else {
                        Text(nextAppointment.title)
                        Text(nextAppointment.startsAt.localizedDateTime(zoneId))
                        Text(nextAppointment.clinician)
                    }
                }
            }
            item {
                SectionCard(stringResource(R.string.next_reminder)) {
                    if (nextReminder == null) {
                        Text(stringResource(R.string.no_active_reminder))
                    } else {
                        Text(nextReminder.second.title)
                        Text(nextReminder.first.localizedDateTime(zoneId))
                    }
                    Button(onClick = onReminders) {
                        Text(stringResource(R.string.manage_reminders))
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard(stringResource(R.string.quick_health_summary)) {
                    LabeledValue(stringResource(R.string.blood_type), record.profile.bloodType.orEmpty())
                    LabeledValue(stringResource(R.string.allergies), record.profile.allergies.joinToString())
                    LabeledValue(
                        stringResource(R.string.last_updated),
                        record.profile.lastUpdatedAt.atZone(zoneId).toLocalDate().localizedDate(),
                    )
                }
            }

            if (recentItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.recent_health_items))
                }
                items(
                    items = recentItems,
                    key = { it.id },
                ) { item ->
                    val title = if (item.kind == VaultItemKind.MEASUREMENT) {
                        record.measurements.firstOrNull { it.id == item.id }?.type?.localizedLabel(record)
                            ?: item.title
                    } else item.title
                    SectionCard(title, modifier = Modifier) {
                        Text(item.updatedAt.localizedDateTime(zoneId))
                        Button(onClick = { onRecentItem(item) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.health_disclaimer))
            }
        }
    }
}

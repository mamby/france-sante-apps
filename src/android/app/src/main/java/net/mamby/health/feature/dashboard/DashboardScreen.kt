package net.mamby.health.feature.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.RecurrenceCalculator
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.ScheduleCalculator
import net.mamby.health.core.model.VaultItem
import net.mamby.health.core.model.VaultItemKind
import net.mamby.health.core.model.VaultContact
import net.mamby.health.core.model.index
import net.mamby.health.feature.ProfileOwned
import net.mamby.health.feature.ownedItems
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.MetricCard
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.theme.UiTokens

@Composable
fun DashboardScreen(
    records: List<ProfileRecord>,
    notes: List<HealthNote>,
    schedules: List<Schedule>,
    contacts: List<VaultContact>,
    clock: Clock,
    zoneId: ZoneId,
    onMedications: () -> Unit,
    onSchedule: () -> Unit,
    onDocumentSelected: (UUID, String) -> Unit,
    onRecentItem: (UUID, VaultItem) -> Unit = { profileId, item ->
        if (item.kind == VaultItemKind.DOCUMENT) onDocumentSelected(profileId, item.id.toString())
    },
    onNoteSelected: (UUID) -> Unit,
    onScheduleSelected: (UUID) -> Unit,
    onContactSelected: (UUID) -> Unit,
    onAddHealthInfo: () -> Unit,
    onImportDocument: () -> Unit,
    onAddMedication: () -> Unit,
    onAddSchedule: () -> Unit,
) {
    val now = clock.instant()
    val nextMedication = records.ownedItems(ProfileRecord::medications)
        .asSequence()
        .mapNotNull { owned ->
            RecurrenceCalculator.nextOccurrence(owned.value, now, zoneId)?.let { occurrence ->
                MedicationPreview(occurrence, owned)
            }
        }
        .minWithOrNull(
            compareBy<MedicationPreview> { it.occurrence }
                .thenBy { it.owned.profileId }
                .thenBy { it.owned.value.id },
        )
    val nextSchedule = schedules
        .asSequence()
        .mapNotNull { schedule ->
            ScheduleCalculator.nextOccurrence(schedule, now, zoneId)?.let { occurrence -> occurrence to schedule }
        }
        .minWithOrNull(compareBy<Pair<net.mamby.health.core.model.ScheduleOccurrence, Schedule>> { it.first.startsAt }
            .thenBy { it.second.id })
    val activeMedications = records.sumOf { record -> record.medications.count { it.isActive } }
    val upcomingSchedules = schedules.count { ScheduleCalculator.nextOccurrence(it, now, zoneId) != null }
    val recentItems = (
        records.ownedItems(ProfileRecord::index).map(RecentDashboardItem::ProfileItem) +
            notes.map(RecentDashboardItem::NoteItem) +
            schedules.map(RecentDashboardItem::ScheduleItem) +
            contacts.map(RecentDashboardItem::ContactItem)
        )
        .sortedWith(
            compareByDescending<RecentDashboardItem> { it.item.updatedAt }
                .thenBy(RecentDashboardItem::scopeKey)
                .thenBy { it.item.id },
        )
        .take(4)
    val isEmpty = notes.isEmpty() && schedules.isEmpty() && contacts.isEmpty() &&
        records.all(ProfileRecord::isHealthDataEmpty)

    AppScreenScaffold(
        title = stringResource(R.string.dashboard_title),
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.dashboard_greeting_all))
            }

            item {
                DashboardPreviewCard(
                    title = stringResource(R.string.nav_medications),
                    onClick = onMedications,
                ) {
                    LabeledValue(
                        stringResource(R.string.medications_metric),
                        activeMedications.toString(),
                    )
                    Text(
                        stringResource(R.string.next_medication_dose),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (nextMedication == null) {
                        Text(stringResource(R.string.no_scheduled_medication))
                    } else {
                        if (records.size > 1) ProfileMarker(nextMedication.owned.profile)
                        Text(nextMedication.owned.value.name)
                        nextMedication.owned.value.dose.takeIf(String::isNotBlank)?.let { Text(it) }
                        Text(nextMedication.occurrence.localizedDateTime(zoneId))
                    }
                }
            }
            item {
                DashboardPreviewCard(
                    title = stringResource(R.string.schedule_title),
                    onClick = onSchedule,
                ) {
                    LabeledValue(
                        stringResource(R.string.schedules_metric),
                        upcomingSchedules.toString(),
                    )
                    Text(
                        stringResource(R.string.next_schedule),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (nextSchedule == null) {
                        Text(stringResource(R.string.no_upcoming_schedule))
                    } else {
                        Text(nextSchedule.second.title)
                        Text(nextSchedule.first.startsAt.localizedDateTime(zoneId))
                        nextSchedule.second.people.takeIf(List<String>::isNotEmpty)?.let { Text(it.joinToString()) }
                    }
                }
            }

            if (isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(stringResource(R.string.getting_started_title)) {
                        Button(onClick = onAddHealthInfo) { Text(stringResource(R.string.getting_started_health)) }
                        Button(onClick = onImportDocument) { Text(stringResource(R.string.import_document)) }
                        Button(onClick = onAddMedication) { Text(stringResource(R.string.add_medication)) }
                        Button(onClick = onAddSchedule) { Text(stringResource(R.string.add_schedule)) }
                    }
                }
            }

            item { MetricCard(stringResource(R.string.documents_metric), records.sumOf { it.documents.size }.toString()) }

            items(
                items = records,
                key = { "summary:${it.profile.id}" },
                span = { GridItemSpan(maxLineSpan) },
            ) { record ->
                SectionCard(stringResource(R.string.quick_health_summary)) {
                    if (records.size > 1) ProfileMarker(record.profile)
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
                    key = { "${it.scopeKey}:${it.item.id}" },
                ) { recent ->
                    val item = recent.item
                    val title = when (recent) {
                        is RecentDashboardItem.NoteItem -> recent.note.title
                        is RecentDashboardItem.ScheduleItem -> recent.schedule.title
                        is RecentDashboardItem.ContactItem -> recent.contact.name
                        is RecentDashboardItem.ProfileItem -> if (item.kind == VaultItemKind.MEASUREMENT) {
                            recent.owned.record.measurements
                                .firstOrNull { it.id == item.id }
                                ?.type
                                ?.localizedLabel(recent.owned.record)
                                ?: item.title
                        } else item.title
                    }
                    SectionCard(title, modifier = Modifier) {
                        if (recent is RecentDashboardItem.ProfileItem && records.size > 1) {
                            ProfileMarker(recent.owned.profile)
                        }
                        Text(item.updatedAt.localizedDateTime(zoneId))
                        Button(onClick = {
                            when (recent) {
                                is RecentDashboardItem.NoteItem -> onNoteSelected(recent.note.id)
                                is RecentDashboardItem.ScheduleItem -> onScheduleSelected(recent.schedule.id)
                                is RecentDashboardItem.ContactItem -> onContactSelected(recent.contact.id)
                                is RecentDashboardItem.ProfileItem -> onRecentItem(recent.owned.profileId, item)
                            }
                        }) {
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

private fun ProfileRecord.isHealthDataEmpty(): Boolean =
    documents.isEmpty() && medications.isEmpty() &&
        vaccinations.isEmpty() && measurements.isEmpty() &&
        familyHistory.isEmpty() && directives.isEmpty() &&
        healthIdentifiers.isEmpty() && profile.emergencyContacts.isEmpty() && profile.bloodType == null &&
        profile.allergies.isEmpty() &&
        profile.chronicConditions.isEmpty() && profile.surgeries.isEmpty()

@Composable
private fun DashboardPreviewCard(
    title: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private data class MedicationPreview(
    val occurrence: Instant,
    val owned: ProfileOwned<net.mamby.health.core.model.Medication>,
)

private sealed interface RecentDashboardItem {
    val item: VaultItem
    val scopeKey: String

    data class ProfileItem(val owned: ProfileOwned<VaultItem>) : RecentDashboardItem {
        override val item: VaultItem = owned.value
        override val scopeKey: String = owned.profileId.toString()
    }

    data class NoteItem(val note: HealthNote) : RecentDashboardItem {
        override val item = VaultItem(note.id, VaultItemKind.NOTE, note.title, note.updatedAt)
        override val scopeKey: String = "vault"
    }

    data class ScheduleItem(val schedule: Schedule) : RecentDashboardItem {
        override val item = VaultItem(schedule.id, VaultItemKind.SCHEDULE, schedule.title, schedule.updatedAt)
        override val scopeKey: String = "vault"
    }

    data class ContactItem(val contact: VaultContact) : RecentDashboardItem {
        override val item = VaultItem(contact.id, VaultItemKind.CONTACT, contact.name, contact.updatedAt)
        override val scopeKey: String = "vault-contact"
    }
}

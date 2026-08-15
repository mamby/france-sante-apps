package net.mamby.health.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.theme.HomeTileTone
import net.mamby.health.ui.theme.LocalHomeTilePalette
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
    onAddNote: () -> Unit = {},
    onAddContact: () -> Unit = {},
    restorePrompt: (@Composable () -> Unit)? = null,
) {
    val adaptiveTileMinWidth = UiTokens.HomeTileMinWidth *
        LocalDensity.current.fontScale.coerceAtLeast(1f)
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
            columns = GridCells.Adaptive(adaptiveTileMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            restorePrompt?.let { prompt ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    prompt()
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                DashboardSectionHeading(stringResource(R.string.dashboard_greeting_all))
            }

            item {
                DashboardTile(
                    title = stringResource(R.string.nav_medications),
                    tone = HomeTileTone.CORAL,
                    onClick = onMedications,
                ) {
                    DashboardLabeledValue(
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
                DashboardTile(
                    title = stringResource(R.string.schedule_title),
                    tone = HomeTileTone.MINT,
                    onClick = onSchedule,
                ) {
                    DashboardLabeledValue(
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
                    DashboardSectionHeading(stringResource(R.string.getting_started_title))
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.add_health_note),
                        tone = HomeTileTone.YELLOW,
                        onClick = onAddNote,
                    )
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.add_schedule),
                        tone = HomeTileTone.MINT,
                        onClick = onAddSchedule,
                    )
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.add_contact),
                        tone = HomeTileTone.SKY,
                        onClick = onAddContact,
                    )
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.getting_started_health),
                        tone = HomeTileTone.LAVENDER,
                        onClick = onAddHealthInfo,
                    )
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.import_document),
                        tone = HomeTileTone.PEACH,
                        onClick = onImportDocument,
                    )
                }
                item {
                    DashboardActionTile(
                        title = stringResource(R.string.add_medication),
                        tone = HomeTileTone.CORAL,
                        onClick = onAddMedication,
                    )
                }
            }

            item {
                DashboardMetricTile(
                    title = stringResource(R.string.documents_metric),
                    value = records.sumOf { it.documents.size }.toString(),
                    tone = HomeTileTone.PEACH,
                )
            }

            items(
                items = records,
                key = { "summary:${it.profile.id}" },
            ) { record ->
                DashboardTile(
                    title = stringResource(R.string.quick_health_summary),
                    tone = HomeTileTone.LAVENDER,
                ) {
                    if (records.size > 1) ProfileMarker(record.profile)
                    DashboardLabeledValue(stringResource(R.string.blood_type), record.profile.bloodType.orEmpty())
                    DashboardLabeledValue(stringResource(R.string.allergies), record.profile.allergies.joinToString())
                    DashboardLabeledValue(
                        stringResource(R.string.last_updated),
                        record.profile.lastUpdatedAt.atZone(zoneId).toLocalDate().localizedDate(),
                    )
                }
            }

            if (recentItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DashboardSectionHeading(stringResource(R.string.recent_health_items))
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
                    DashboardTile(
                        title = title,
                        tone = recent.homeTileTone,
                        onClick = {
                            when (recent) {
                                is RecentDashboardItem.NoteItem -> onNoteSelected(recent.note.id)
                                is RecentDashboardItem.ScheduleItem -> onScheduleSelected(recent.schedule.id)
                                is RecentDashboardItem.ContactItem -> onContactSelected(recent.contact.id)
                                is RecentDashboardItem.ProfileItem -> onRecentItem(recent.owned.profileId, item)
                            }
                        },
                    ) {
                        if (recent is RecentDashboardItem.ProfileItem && records.size > 1) {
                            ProfileMarker(recent.owned.profile)
                        }
                        Text(item.updatedAt.localizedDateTime(zoneId))
                    }
                }
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
private fun DashboardSectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DashboardActionTile(
    title: String,
    tone: HomeTileTone,
    onClick: () -> Unit,
) {
    DashboardTile(title = title, tone = tone, onClick = onClick)
}

@Composable
private fun DashboardMetricTile(
    title: String,
    value: String,
    tone: HomeTileTone,
) {
    DashboardTile(title = title, tone = tone) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardLabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value.ifBlank { stringResource(R.string.common_not_set) })
    }
}

@Composable
private fun DashboardTile(
    title: String,
    tone: HomeTileTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalHomeTilePalette.current.colorsFor(tone)
    val cardColors = CardDefaults.cardColors(
        containerColor = colors.container,
        contentColor = colors.content,
    )
    val cardElevation = CardDefaults.cardElevation(defaultElevation = UiTokens.HomeTileElevation)
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiTokens.HomeTileMinHeight)
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }

    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
            elevation = cardElevation,
            content = cardContent,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
            elevation = cardElevation,
            content = cardContent,
        )
    }
}

private val RecentDashboardItem.homeTileTone: HomeTileTone
    get() = when (item.kind) {
        VaultItemKind.DOCUMENT -> HomeTileTone.PEACH
        VaultItemKind.MEDICATION -> HomeTileTone.CORAL
        VaultItemKind.SCHEDULE -> HomeTileTone.MINT
        VaultItemKind.VACCINATION -> HomeTileTone.MINT
        VaultItemKind.NOTE -> HomeTileTone.YELLOW
        VaultItemKind.MEASUREMENT -> HomeTileTone.AQUA
        VaultItemKind.CONTACT -> HomeTileTone.SKY
        VaultItemKind.FAMILY_HISTORY -> HomeTileTone.LAVENDER
        VaultItemKind.DIRECTIVE -> HomeTileTone.LAVENDER
        VaultItemKind.IDENTIFIER -> HomeTileTone.SKY
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

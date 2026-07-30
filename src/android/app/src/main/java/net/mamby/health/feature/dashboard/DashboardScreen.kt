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
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.RecurrenceCalculator
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.MetricCard
import net.mamby.health.ui.components.SampleWorkspaceBanner
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun DashboardScreen(
    vault: HealthVault,
    isDemo: Boolean,
    clock: Clock,
    zoneId: ZoneId,
    onStartVault: () -> Unit,
    onSettings: () -> Unit,
    onReminders: () -> Unit,
    onDocumentSelected: (String) -> Unit,
) {
    val now = clock.instant()
    val nextAppointment = vault.appointments
        .asSequence()
        .filter { it.startsAt.isAfter(now) }
        .minByOrNull { it.startsAt }
    val nextReminder = vault.reminders
        .asSequence()
        .mapNotNull { reminder ->
            RecurrenceCalculator.nextOccurrence(reminder, now, zoneId)?.let { it to reminder }
        }
        .minByOrNull { it.first }
    val activeMedications = vault.medications.count { it.isActive }
    val upcomingAppointments = vault.appointments.count { it.startsAt.isAfter(now) }
    val enabledReminders = vault.reminders.count { it.isEnabled } +
        vault.medications.count { it.isActive && it.remindersEnabled } +
        vault.appointments.count { it.reminderLeadMinutes != null && it.startsAt.isAfter(now) }

    AppScreenScaffold(
        title = stringResource(R.string.dashboard_title),
        onSettings = onSettings,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            if (isDemo) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SampleWorkspaceBanner(onStartVault)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.dashboard_greeting, vault.profile.displayName))
            }

            item { MetricCard(stringResource(R.string.documents_metric), vault.documents.size.toString()) }
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
                    LabeledValue(stringResource(R.string.blood_type), vault.profile.bloodType.orEmpty())
                    LabeledValue(stringResource(R.string.allergies), vault.profile.allergies.joinToString())
                    LabeledValue(
                        stringResource(R.string.last_updated),
                        vault.profile.lastUpdatedAt.atZone(zoneId).toLocalDate().localizedDate(),
                    )
                }
            }

            if (vault.documents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.recent_documents))
                }
                items(
                    items = vault.documents.sortedByDescending { it.documentDate }.take(4),
                    key = { it.id },
                ) { document ->
                    SectionCard(document.title, modifier = Modifier) {
                        Text(document.documentDate.localizedDate())
                        Button(onClick = { onDocumentSelected(document.id.toString()) }) {
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

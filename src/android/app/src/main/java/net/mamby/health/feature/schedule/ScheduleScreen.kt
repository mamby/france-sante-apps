package net.mamby.health.feature.schedule

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Reminder
import net.mamby.health.feature.appointments.AppointmentsScreen
import net.mamby.health.feature.reminders.RemindersScreen
import net.mamby.health.ui.theme.UiTokens

@Composable
fun ScheduleScreen(
    records: List<ProfileRecord>,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    notificationsBlocked: Boolean,
    onAddProfile: (String, (UUID) -> Unit) -> Unit,
    onUpsertAppointment: (UUID, Appointment) -> Unit,
    onAppointmentSelected: (UUID, String) -> Unit,
    onUpsertReminder: (UUID, Reminder) -> Unit,
    onDeleteReminder: (UUID, UUID) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    selectedSection: ScheduleSection,
    onSectionSelected: (ScheduleSection) -> Unit,
    appointmentCreationRequest: Long = 0,
) {
    val sectionSelector = @Composable {
        ScheduleSectionSelector(selectedSection, onSectionSelected)
    }
    when (selectedSection) {
        ScheduleSection.Appointments -> AppointmentsScreen(
            records = records,
            zoneId = zoneId,
            now = now,
            onAddProfile = onAddProfile,
            onUpsert = onUpsertAppointment,
            onSelected = onAppointmentSelected,
            creationRequest = appointmentCreationRequest,
            titleResource = R.string.schedule_title,
            sectionSelector = sectionSelector,
        )
        ScheduleSection.Reminders -> RemindersScreen(
            records = records,
            today = today,
            notificationsBlocked = notificationsBlocked,
            now = now,
            zoneId = zoneId,
            onAddProfile = onAddProfile,
            onUpsert = onUpsertReminder,
            onDelete = onDeleteReminder,
            onOpenNotificationSettings = onOpenNotificationSettings,
            titleResource = R.string.schedule_title,
            sectionSelector = sectionSelector,
        )
    }
}

@Composable
private fun ScheduleSectionSelector(
    selectedSection: ScheduleSection,
    onSelected: (ScheduleSection) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiTokens.ScreenPadding),
    ) {
        ScheduleSection.entries.forEachIndexed { index, section ->
            SegmentedButton(
                selected = section == selectedSection,
                onClick = { onSelected(section) },
                shape = SegmentedButtonDefaults.itemShape(index, ScheduleSection.entries.size),
                label = { Text(stringResource(section.labelResource)) },
            )
        }
    }
}

enum class ScheduleSection(val labelResource: Int) {
    Appointments(R.string.appointments_title),
    Reminders(R.string.reminders_title),
}

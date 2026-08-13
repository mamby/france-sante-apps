package net.mamby.health.notifications

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.ReminderRecurrence as VaultRecurrence
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.ScheduleAlert
import net.mamby.health.core.model.ScheduleRecurrence
import net.mamby.health.core.model.ScheduleTiming
import net.mamby.health.data.VaultState
import net.mamby.health.testing.StubVaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultReminderSourceTest {
    @Test
    fun requestsIncludeEveryProfileWithCollisionFreeProfileScopedIds() = runTest {
        val vault = HealthVault(
            revision = 2,
            profiles = listOf(
                profileRecord(FIRST_PROFILE, FIRST_MEDICATION),
                profileRecord(SECOND_PROFILE, SECOND_MEDICATION),
            ),
            schedules = listOf(
                Schedule(
                    id = SCHEDULE,
                    title = "Check-in",
                    timing = ScheduleTiming.LocalTimed(LocalDate.of(2026, 7, 30), LocalTime.of(8, 30)),
                    recurrence = ScheduleRecurrence.Daily(),
                    alert = ScheduleAlert.Timed(10),
                    updatedAt = NOW,
                ),
                Schedule(
                    id = ONE_TIME_SCHEDULE,
                    title = "One-time visit",
                    timing = ScheduleTiming.InstantTimed(Instant.parse("2026-07-30T09:00:00Z")),
                    alert = ScheduleAlert.Timed(0),
                    updatedAt = NOW,
                ),
            ),
            updatedAt = NOW,
        )
        val repository = object : StubVaultRepository() {
            override val state: StateFlow<VaultState> = MutableStateFlow(
                VaultState.Ready(vault),
            )
        }
        val source = VaultReminderSource(
            repository,
            object : ZoneIdProvider {
                override fun current() = ZoneOffset.UTC
            },
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        val requests = source.activeReminderRequests()

        assertEquals(
            setOf(FIRST_PROFILE.toString(), SECOND_PROFILE.toString()),
            requests.mapNotNull { (it.target as? ReminderTarget.Medication)?.profileId }.toSet(),
        )
        assertEquals(requests.size, requests.map { it.id }.distinct().size)
        assertTrue(requests.filter { it.target is ReminderTarget.Medication }.all { it.id.startsWith("profile:") })
        val scheduleRequest = requests.single { it.target == ReminderTarget.Schedule(SCHEDULE.toString()) }
        assertEquals(
            ReminderRecurrence.Once(Instant.parse("2026-07-30T08:20:00Z")),
            scheduleRequest.recurrence,
        )
        val oneTime = requests.single { it.target == ReminderTarget.Schedule(ONE_TIME_SCHEDULE.toString()) }
        val scheduledOccurrence = (oneTime.recurrence as ReminderRecurrence.Once).occurrence
        assertEquals(
            ReminderTarget.Schedule(ONE_TIME_SCHEDULE.toString()),
            source.requestForDelivery(ReminderScheduleKey.from(oneTime.id), scheduledOccurrence)?.target,
        )
    }

    @Test
    fun rootWideScheduleReminderIsAvailableWithoutProfiles() = runTest {
        val schedule = Schedule(
            id = SCHEDULE,
            title = "Check-in",
            timing = ScheduleTiming.LocalTimed(LocalDate.of(2026, 7, 30), LocalTime.of(8, 30)),
            alert = ScheduleAlert.Timed(10),
            updatedAt = NOW,
        )
        val vault = HealthVault(
            revision = 1,
            profiles = emptyList(),
            schedules = listOf(schedule),
            updatedAt = NOW,
        )
        val repository = object : StubVaultRepository() {
            override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Ready(vault))
        }
        val source = VaultReminderSource(
            repository,
            object : ZoneIdProvider {
                override fun current() = ZoneOffset.UTC
            },
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        val request = source.activeReminderRequests().single()
        val occurrence = (request.recurrence as ReminderRecurrence.Once).occurrence

        assertEquals(ReminderTarget.Schedule(SCHEDULE.toString()), request.target)
        assertEquals(Instant.parse("2026-07-30T08:20:00Z"), occurrence)
        assertTrue(request.target !is ReminderTarget.Medication)
        assertEquals(
            request,
            source.requestForDelivery(ReminderScheduleKey.from(request.id), occurrence),
        )
    }

    private fun profileRecord(profileId: UUID, medicationId: UUID) = ProfileRecord(
        profile = HealthProfile(profileId, "Owner", lastUpdatedAt = NOW),
        medications = listOf(
            Medication(
                id = medicationId,
                name = "Medication",
                dose = "5 mg",
                instructions = "Daily",
                schedule = MedicationSchedule(
                    recurrence = VaultRecurrence.DAILY,
                    reminderTimes = listOf(LocalTime.of(8, 0)),
                    startsOn = LocalDate.of(2026, 7, 1),
                ),
                remindersEnabled = true,
                updatedAt = NOW,
            ),
        ),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIRST_PROFILE: UUID = UUID.fromString("a4a94b20-1489-4947-b89f-b463fb5d7e74")
        val SECOND_PROFILE: UUID = UUID.fromString("70ddbe28-78cd-45d8-aa9f-f012d8e911f5")
        val FIRST_MEDICATION: UUID = UUID.fromString("a5d9a8bb-3905-47c3-97a2-691c51532924")
        val SECOND_MEDICATION: UUID = UUID.fromString("d9dbf4da-8420-4dcc-a7e0-8cc06848e8e3")
        val SCHEDULE: UUID = UUID.fromString("5d6c0fdb-296d-4c52-9e91-22a2fce4e317")
        val ONE_TIME_SCHEDULE: UUID = UUID.fromString("3624ba94-0642-49cd-a1c8-3e773b7bc992")
    }
}

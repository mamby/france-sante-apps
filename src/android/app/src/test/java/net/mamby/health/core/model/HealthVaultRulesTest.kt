package net.mamby.health.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthVaultRulesTest {
    @Test
    fun emptyHealthDataIsValidWithoutProfiles() {
        val empty = HealthVault.empty(Instant.EPOCH)

        assertEquals(empty, empty.requireValid())
        assertEquals(1, empty.version)
        assertEquals(0L, empty.revision)
        assertEquals(emptyList<ProfileRecord>(), empty.profiles)
    }

    @Test
    fun documentSearchRequiresEveryTermAndHonorsCategory() {
        val now = Instant.parse("2026-07-30T00:00:00Z")
        val matching = document(
            title = "Annual blood panel",
            category = BuiltInDocumentCategory.LAB_RESULTS.asReference(),
            notes = "Fasting result",
            now = now,
        )
        val other = document(
            title = "Discharge report",
            category = BuiltInDocumentCategory.REPORTS.asReference(),
            notes = "Blood pressure follow-up",
            now = now.minusSeconds(60),
        )

        val result = DocumentSearch.search(
            documents = listOf(other, matching),
            query = "blood fasting",
            category = BuiltInDocumentCategory.LAB_RESULTS.asReference(),
        )

        assertEquals(listOf(matching), result)
    }

    @Test
    fun monthlyRecurrenceClampsToLastDayOfShortMonth() {
        val schedule = schedule(
            startsOn = LocalDate.of(2025, 1, 31),
            recurrence = ScheduleRecurrence.Monthly(31),
        )

        val next = ScheduleCalculator.nextOccurrence(
            schedule,
            Instant.parse("2025-02-01T09:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(Instant.parse("2025-02-28T10:00:00Z"), next?.startsAt)
    }

    @Test
    fun weeklyRecurrenceUsesConfiguredDaysAndRespectsEndDate() {
        val schedule = schedule(
            startsOn = LocalDate.of(2026, 7, 1),
            recurrence = ScheduleRecurrence.Weekly(
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                repeatUntil = LocalDate.of(2026, 7, 6),
            ),
        )

        assertEquals(
            Instant.parse("2026-07-06T10:00:00Z"),
            ScheduleCalculator.nextOccurrence(
                schedule,
                Instant.parse("2026-07-05T12:00:00Z"),
                ZoneId.of("UTC"),
            )?.startsAt,
        )
        assertNull(
            ScheduleCalculator.nextOccurrence(
                schedule,
                Instant.parse("2026-07-06T11:00:00Z"),
                ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun medicationRecurrenceReturnsTheEarliestActiveDoseWithoutRequiringNotifications() {
        val medication = Medication(
            id = UUID.randomUUID(),
            name = "Medication",
            dose = "5 mg",
            instructions = "Daily",
            schedule = MedicationSchedule(
                recurrence = ReminderRecurrence.DAILY,
                reminderTimes = listOf(LocalTime.of(18, 0), LocalTime.of(8, 0)),
                startsOn = LocalDate.of(2026, 7, 1),
            ),
            remindersEnabled = false,
            updatedAt = Instant.EPOCH,
        )

        assertEquals(
            Instant.parse("2026-07-31T08:00:00Z"),
            RecurrenceCalculator.nextOccurrence(
                medication,
                Instant.parse("2026-07-30T19:00:00Z"),
                ZoneId.of("UTC"),
            ),
        )
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                medication.copy(isActive = false),
                Instant.parse("2026-07-30T19:00:00Z"),
                ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun allDayAlertUsesSelectedLocalTimeAndAdvancesAfterDelivery() {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            title = "Daily task",
            timing = ScheduleTiming.AllDay(LocalDate.of(2026, 7, 30)),
            recurrence = ScheduleRecurrence.Daily(),
            alert = ScheduleAlert.AllDay(daysBefore = 1, timeOfDay = LocalTime.of(9, 0)),
            updatedAt = Instant.EPOCH,
        )

        assertEquals(
            Instant.parse("2026-07-30T09:00:00Z"),
            ScheduleCalculator.nextAlert(schedule, Instant.parse("2026-07-30T08:00:00Z"), ZoneId.of("UTC")),
        )
        assertEquals(
            Instant.parse("2026-07-31T09:00:00Z"),
            ScheduleCalculator.nextAlert(schedule, Instant.parse("2026-07-30T10:00:00Z"), ZoneId.of("UTC")),
        )
    }

    @Test
    fun recurringTimedDurationCanCrossMidnightAndStopsAtRepeatLimit() {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            title = "Overnight care",
            timing = ScheduleTiming.LocalTimed(
                startsOn = LocalDate.of(2026, 7, 30),
                timeOfDay = LocalTime.of(23, 30),
                durationMinutes = 120,
            ),
            recurrence = ScheduleRecurrence.Daily(LocalDate.of(2026, 7, 31)),
            updatedAt = Instant.EPOCH,
        )

        val occurrence = ScheduleCalculator.nextOccurrence(
            schedule,
            Instant.parse("2026-07-30T22:00:00Z"),
            ZoneId.of("UTC"),
        )
        assertEquals(Instant.parse("2026-07-30T23:30:00Z"), occurrence?.startsAt)
        assertEquals(Instant.parse("2026-07-31T01:30:00Z"), occurrence?.endsAt)
        assertNull(
            ScheduleCalculator.nextOccurrence(
                schedule,
                Instant.parse("2026-07-31T23:30:00Z"),
                ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun validationRejectsInvalidEndsAndCaseInsensitiveDuplicatePeople() {
        val duplicatePeople = HealthVault.withProfile(
            Instant.EPOCH,
            UUID.randomUUID(),
            "Owner",
        ).copy(
            schedules = listOf(
                Schedule(
                    id = UUID.randomUUID(),
                    title = "Visit",
                    timing = ScheduleTiming.InstantTimed(Instant.EPOCH),
                    people = listOf("Amina", "amina"),
                    updatedAt = Instant.EPOCH,
                ),
            ),
        )

        assertThrows(VaultValidationException::class.java) { duplicatePeople.requireValid() }
        val invalidEnd = duplicatePeople.copy(
            schedules = listOf(
                duplicatePeople.schedules.single().copy(
                    people = listOf("Amina"),
                    timing = ScheduleTiming.InstantTimed(Instant.EPOCH, Instant.EPOCH.minusSeconds(1)),
                ),
            ),
        )
        assertThrows(VaultValidationException::class.java) { invalidEnd.requireValid() }
    }

    private fun document(
        title: String,
        category: DocumentCategoryRef,
        notes: String,
        now: Instant,
    ) = MedicalDocument(
        id = UUID.randomUUID(),
        title = title,
        category = category,
        documentDate = LocalDate.of(2026, 7, 1),
        source = "Source",
        notes = notes,
        blobId = UUID.randomUUID(),
        mimeType = "application/pdf",
        sizeBytes = 100,
        updatedAt = now,
    )

    private fun schedule(
        startsOn: LocalDate,
        recurrence: ScheduleRecurrence,
    ) = Schedule(
        id = UUID.randomUUID(),
        title = "Schedule",
        timing = ScheduleTiming.LocalTimed(startsOn, LocalTime.of(10, 0)),
        recurrence = recurrence,
        updatedAt = Instant.EPOCH,
    )
}

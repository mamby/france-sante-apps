package net.mamby.health.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthVaultRulesTest {
    @Test
    fun documentSearchRequiresEveryTermAndHonorsCategory() {
        val now = Instant.parse("2026-07-30T00:00:00Z")
        val matching = document(
            title = "Annual blood panel",
            category = DocumentCategory.LAB_RESULTS,
            notes = "Fasting result",
            now = now,
        )
        val other = document(
            title = "Discharge report",
            category = DocumentCategory.REPORTS,
            notes = "Blood pressure follow-up",
            now = now.minusSeconds(60),
        )

        val result = DocumentSearch.search(
            documents = listOf(other, matching),
            query = "blood fasting",
            category = DocumentCategory.LAB_RESULTS,
        )

        assertEquals(listOf(matching), result)
    }

    @Test
    fun monthlyRecurrenceClampsToLastDayOfShortMonth() {
        val reminder = reminder(
            startsOn = LocalDate.of(2025, 1, 31),
            recurrence = ReminderRecurrence.MONTHLY,
        )

        val next = RecurrenceCalculator.nextOccurrence(
            reminder,
            Instant.parse("2025-02-01T09:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(Instant.parse("2025-02-28T10:00:00Z"), next)
    }

    @Test
    fun weeklyRecurrenceUsesConfiguredDaysAndRespectsEndDate() {
        val reminder = reminder(
            startsOn = LocalDate.of(2026, 7, 1),
            recurrence = ReminderRecurrence.WEEKLY,
            days = setOf(DayOfWeek.MONDAY),
            endsOn = LocalDate.of(2026, 7, 6),
        )

        assertEquals(
            Instant.parse("2026-07-06T10:00:00Z"),
            RecurrenceCalculator.nextOccurrence(
                reminder,
                Instant.parse("2026-07-05T12:00:00Z"),
                ZoneId.of("UTC"),
            ),
        )
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                reminder,
                Instant.parse("2026-07-06T11:00:00Z"),
                ZoneId.of("UTC"),
            ),
        )
    }

    private fun document(
        title: String,
        category: DocumentCategory,
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

    private fun reminder(
        startsOn: LocalDate,
        recurrence: ReminderRecurrence,
        days: Set<DayOfWeek> = emptySet(),
        endsOn: LocalDate? = null,
    ) = Reminder(
        id = UUID.randomUUID(),
        title = "Reminder",
        startsOn = startsOn,
        timeOfDay = LocalTime.of(10, 0),
        recurrence = recurrence,
        daysOfWeek = days,
        endsOn = endsOn,
        updatedAt = Instant.EPOCH,
    )
}

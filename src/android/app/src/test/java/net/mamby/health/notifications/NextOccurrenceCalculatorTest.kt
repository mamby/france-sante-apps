package net.mamby.health.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextOccurrenceCalculatorTest {
    private val calculator = NextOccurrenceCalculator()

    @Test
    fun dailyOccurrenceIsStrictlyAfterTheReferenceInstant() {
        val zone = ZoneId.of("Europe/Paris")
        val recurrence = ReminderRecurrence.Daily(LocalTime.of(8, 30))
        val reference = LocalDate.of(2026, 7, 30)
            .atTime(8, 30)
            .atZone(zone)
            .toInstant()

        val result = calculator.nextOccurrence(recurrence, reference, zone)

        assertEquals(
            LocalDate.of(2026, 7, 31).atTime(8, 30).atZone(zone).toInstant(),
            result,
        )
    }

    @Test
    fun dailyOccurrenceUsesZoneRulesAcrossADaylightSavingGap() {
        val zone = ZoneId.of("Europe/Paris")
        val recurrence = ReminderRecurrence.Daily(
            localTime = LocalTime.of(2, 30),
            startDate = LocalDate.of(2026, 3, 29),
        )

        val result = calculator.nextOccurrence(
            recurrence = recurrence,
            strictlyAfter = Instant.parse("2026-03-28T23:00:00Z"),
            zoneId = zone,
        )

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), result)
    }

    @Test
    fun weeklyOccurrenceStopsAtTheConfiguredEndDate() {
        val zone = ZoneId.of("UTC")
        val recurrence = ReminderRecurrence.Weekly(
            isoDaysOfWeek = setOf(1),
            localTime = LocalTime.NOON,
            startDate = LocalDate.of(2026, 7, 27),
            endDate = LocalDate.of(2026, 7, 27),
        )

        val result = calculator.nextOccurrence(
            recurrence,
            Instant.parse("2026-07-27T12:00:00Z"),
            zone,
        )

        assertNull(result)
    }

    @Test
    fun monthlyOccurrenceSkipsMonthsWithoutTheRequestedDay() {
        val zone = ZoneId.of("UTC")
        val recurrence = ReminderRecurrence.Monthly(
            dayOfMonth = 31,
            localTime = LocalTime.of(9, 0),
        )

        val result = calculator.nextOccurrence(
            recurrence,
            Instant.parse("2026-03-31T09:00:00Z"),
            zone,
        )

        assertEquals(Instant.parse("2026-05-31T09:00:00Z"), result)
    }
}

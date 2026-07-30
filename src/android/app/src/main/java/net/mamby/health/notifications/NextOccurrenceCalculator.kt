package net.mamby.health.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class NextOccurrenceCalculator @Inject constructor() {
    fun nextOccurrence(
        recurrence: ReminderRecurrence,
        strictlyAfter: Instant,
        zoneId: ZoneId,
    ): Instant? = when (recurrence) {
        is ReminderRecurrence.Once -> recurrence.occurrence.takeIf { it.isAfter(strictlyAfter) }
        is ReminderRecurrence.Daily -> nextDaily(recurrence, strictlyAfter, zoneId)
        is ReminderRecurrence.Weekly -> nextWeekly(recurrence, strictlyAfter, zoneId)
        is ReminderRecurrence.Monthly -> nextMonthly(recurrence, strictlyAfter, zoneId)
    }

    private fun nextDaily(
        recurrence: ReminderRecurrence.Daily,
        strictlyAfter: Instant,
        zoneId: ZoneId,
    ): Instant? {
        val today = strictlyAfter.atZone(zoneId).toLocalDate()
        var date = laterOf(today, recurrence.startDate)
        var candidate = date.atTime(recurrence.localTime).atZone(zoneId).toInstant()
        if (!candidate.isAfter(strictlyAfter)) {
            date = date.plusDays(1)
            candidate = date.atTime(recurrence.localTime).atZone(zoneId).toInstant()
        }
        return candidate.takeIf { recurrence.endDate == null || !date.isAfter(recurrence.endDate) }
    }

    private fun nextWeekly(
        recurrence: ReminderRecurrence.Weekly,
        strictlyAfter: Instant,
        zoneId: ZoneId,
    ): Instant? {
        val today = strictlyAfter.atZone(zoneId).toLocalDate()
        val firstDate = laterOf(today, recurrence.startDate)
        for (offset in 0..MAX_WEEKLY_SEARCH_OFFSET_DAYS) {
            val date = firstDate.plusDays(offset.toLong())
            if (recurrence.endDate != null && date.isAfter(recurrence.endDate)) return null
            if (date.dayOfWeek.value !in recurrence.isoDaysOfWeek) continue

            val candidate = date.atTime(recurrence.localTime).atZone(zoneId).toInstant()
            if (candidate.isAfter(strictlyAfter)) return candidate
        }
        return null
    }

    private fun nextMonthly(
        recurrence: ReminderRecurrence.Monthly,
        strictlyAfter: Instant,
        zoneId: ZoneId,
    ): Instant? {
        val today = strictlyAfter.atZone(zoneId).toLocalDate()
        val firstDate = laterOf(today, recurrence.startDate)
        for (offset in 0..MAX_MONTHLY_SEARCH_OFFSET_MONTHS) {
            val month = firstDate.plusMonths(offset.toLong())
            if (recurrence.dayOfMonth > month.lengthOfMonth()) continue
            val date = month.withDayOfMonth(recurrence.dayOfMonth)
            if (date.isBefore(firstDate)) continue
            if (recurrence.endDate != null && date.isAfter(recurrence.endDate)) return null
            val candidate = date.atTime(recurrence.localTime).atZone(zoneId).toInstant()
            if (candidate.isAfter(strictlyAfter)) return candidate
        }
        return null
    }

    private fun laterOf(left: LocalDate, right: LocalDate?): LocalDate =
        if (right != null && right.isAfter(left)) right else left

    private companion object {
        const val MAX_WEEKLY_SEARCH_OFFSET_DAYS = 7
        const val MAX_MONTHLY_SEARCH_OFFSET_MONTHS = 12
    }
}

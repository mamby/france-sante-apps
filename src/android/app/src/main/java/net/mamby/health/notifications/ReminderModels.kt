package net.mamby.health.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

@Serializable
sealed interface ReminderTarget {
    @Serializable
    data class Medication(
        val profileId: String,
        val medicationId: String,
    ) : ReminderTarget

    @Serializable
    data class Schedule(val scheduleId: String) : ReminderTarget
}

@Serializable
sealed interface ReminderRecurrence {
    @Serializable
    data class Once(
        @Serializable(with = InstantSerializer::class)
        val occurrence: Instant,
    ) : ReminderRecurrence

    @Serializable
    data class Daily(
        @Serializable(with = LocalTimeSerializer::class)
        val localTime: LocalTime,
        @Serializable(with = NullableLocalDateSerializer::class)
        val startDate: LocalDate? = null,
        @Serializable(with = NullableLocalDateSerializer::class)
        val endDate: LocalDate? = null,
    ) : ReminderRecurrence {
        init {
            require(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
                "Reminder end date must not precede its start date"
            }
        }
    }

    @Serializable
    data class Weekly(
        val isoDaysOfWeek: Set<Int>,
        @Serializable(with = LocalTimeSerializer::class)
        val localTime: LocalTime,
        @Serializable(with = NullableLocalDateSerializer::class)
        val startDate: LocalDate? = null,
        @Serializable(with = NullableLocalDateSerializer::class)
        val endDate: LocalDate? = null,
    ) : ReminderRecurrence {
        init {
            require(isoDaysOfWeek.isNotEmpty()) { "A weekly reminder needs at least one day" }
            require(isoDaysOfWeek.all { it in 1..7 }) {
                "Weekly reminder days use ISO values from 1 through 7"
            }
            require(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
                "Reminder end date must not precede its start date"
            }
        }
    }

    @Serializable
    data class Monthly(
        val dayOfMonth: Int,
        @Serializable(with = LocalTimeSerializer::class)
        val localTime: LocalTime,
        @Serializable(with = NullableLocalDateSerializer::class)
        val startDate: LocalDate? = null,
        @Serializable(with = NullableLocalDateSerializer::class)
        val endDate: LocalDate? = null,
    ) : ReminderRecurrence {
        init {
            require(dayOfMonth in 1..31) { "Monthly reminder day must be from 1 through 31" }
            require(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
                "Reminder end date must not precede its start date"
            }
        }
    }
}

@Serializable
data class ReminderRequest(
    val id: String,
    val target: ReminderTarget,
    val title: String,
    val message: String,
    val recurrence: ReminderRecurrence,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Reminder id must not be blank" }
        when (target) {
            is ReminderTarget.Medication -> {
                require(target.profileId.isNotBlank()) { "Medication reminder profile id must not be blank" }
                require(target.medicationId.isNotBlank()) { "Medication reminder target id must not be blank" }
            }
            is ReminderTarget.Schedule -> require(target.scheduleId.isNotBlank()) {
                "Schedule reminder target id must not be blank"
            }
        }
        require(title.isNotBlank()) { "Reminder title must not be blank" }
    }
}

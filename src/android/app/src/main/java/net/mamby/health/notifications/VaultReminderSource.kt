package net.mamby.health.notifications

import javax.inject.Inject
import javax.inject.Singleton
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.ReminderRecurrence as VaultRecurrence
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState

@Singleton
class VaultReminderSource @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val zoneIdProvider: ZoneIdProvider,
) : ReminderSource {
    override suspend fun activeReminderRequests(): List<ReminderRequest> {
        if (vaultRepository.state.value is VaultState.Loading) vaultRepository.initialize()
        val vault = (vaultRepository.state.value as? VaultState.Ready)?.vault ?: return emptyList()
        return buildList {
            vault.medications
                .asSequence()
                .filter { it.isActive && it.remindersEnabled }
                .flatMap(::medicationRequests)
                .forEach(::add)
            vault.appointments.mapNotNull(::appointmentRequest).forEach(::add)
            vault.reminders.filter(Reminder::isEnabled).mapNotNull(::generalRequest).forEach(::add)
        }
    }

    private fun medicationRequests(medication: Medication): Sequence<ReminderRequest> =
        medication.schedule.reminderTimes.asSequence().mapNotNull { localTime ->
            val recurrence = when (medication.schedule.recurrence) {
                VaultRecurrence.NONE -> medication.schedule.startsOn?.let { startsOn ->
                    ReminderRecurrence.Once(
                        startsOn
                            .atTime(localTime)
                            .atZone(zoneIdProvider.current())
                            .toInstant(),
                    )
                }
                VaultRecurrence.DAILY -> ReminderRecurrence.Daily(
                    localTime = localTime,
                    startDate = medication.schedule.startsOn,
                    endDate = medication.schedule.endsOn,
                )
                VaultRecurrence.WEEKLY -> {
                    val days = medication.schedule.daysOfWeek
                        .ifEmpty { medication.schedule.startsOn?.let { setOf(it.dayOfWeek) }.orEmpty() }
                    days.takeIf { it.isNotEmpty() }?.let {
                        ReminderRecurrence.Weekly(
                            isoDaysOfWeek = days.mapTo(mutableSetOf()) { it.value },
                            localTime = localTime,
                            startDate = medication.schedule.startsOn,
                            endDate = medication.schedule.endsOn,
                        )
                    }
                }
                VaultRecurrence.MONTHLY -> medication.schedule.startsOn?.let { startsOn ->
                    ReminderRecurrence.Monthly(
                        dayOfMonth = startsOn.dayOfMonth,
                        localTime = localTime,
                        startDate = startsOn,
                        endDate = medication.schedule.endsOn,
                    )
                }
            } ?: return@mapNotNull null
            ReminderRequest(
                id = "medication:${medication.id}:$localTime",
                type = ReminderType.MEDICATION,
                targetId = medication.id.toString(),
                title = medication.name,
                message = listOf(medication.dose, medication.instructions)
                    .filter(String::isNotBlank)
                    .joinToString(SEPARATOR),
                recurrence = recurrence,
            )
        }

    private fun appointmentRequest(appointment: Appointment): ReminderRequest? {
        val leadMinutes = appointment.reminderLeadMinutes ?: return null
        if (leadMinutes < 0) return null
        return ReminderRequest(
            id = "appointment:${appointment.id}",
            type = ReminderType.APPOINTMENT,
            targetId = appointment.id.toString(),
            title = appointment.title,
            message = listOf(appointment.clinician, appointment.location)
                .filter(String::isNotBlank)
                .joinToString(SEPARATOR),
            recurrence = ReminderRecurrence.Once(
                appointment.startsAt.minusSeconds(Math.multiplyExact(leadMinutes, 60L)),
            ),
        )
    }

    private fun generalRequest(reminder: Reminder): ReminderRequest? {
        val recurrence = when (reminder.recurrence) {
            VaultRecurrence.NONE -> ReminderRecurrence.Once(
                reminder.startsOn
                    .atTime(reminder.timeOfDay)
                    .atZone(zoneIdProvider.current())
                    .toInstant(),
            )
            VaultRecurrence.DAILY -> ReminderRecurrence.Daily(
                localTime = reminder.timeOfDay,
                startDate = reminder.startsOn,
                endDate = reminder.endsOn,
            )
            VaultRecurrence.WEEKLY -> {
                val days = reminder.daysOfWeek.ifEmpty { setOf(reminder.startsOn.dayOfWeek) }
                ReminderRecurrence.Weekly(
                    isoDaysOfWeek = days.mapTo(mutableSetOf()) { it.value },
                    localTime = reminder.timeOfDay,
                    startDate = reminder.startsOn,
                    endDate = reminder.endsOn,
                )
            }
            VaultRecurrence.MONTHLY -> ReminderRecurrence.Monthly(
                dayOfMonth = reminder.startsOn.dayOfMonth,
                localTime = reminder.timeOfDay,
                startDate = reminder.startsOn,
                endDate = reminder.endsOn,
            )
        }
        return ReminderRequest(
            id = "reminder:${reminder.id}",
            type = ReminderType.GENERAL,
            targetId = reminder.id.toString(),
            title = reminder.title,
            message = reminder.notes.orEmpty(),
            recurrence = recurrence,
        )
    }

    private companion object {
        const val SEPARATOR = " · "
    }
}

package net.mamby.health.notifications

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.ReminderRecurrence as VaultRecurrence
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.ScheduleCalculator
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState

@Singleton
class VaultReminderSource @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val zoneIdProvider: ZoneIdProvider,
    private val clock: Clock,
) : ReminderSource {
    override suspend fun activeReminderRequests(): List<ReminderRequest> {
        val vault = readyVault() ?: return emptyList()
        return buildList {
            vault.profiles.forEach { record ->
                val profileId = record.profile.id.toString()
                record.medications
                    .asSequence()
                    .filter { it.isActive && it.remindersEnabled }
                    .flatMap { medicationRequests(profileId, it) }
                    .forEach(::add)
            }
            vault.schedules.mapNotNull(::scheduleRequest).forEach(::add)
        }
    }

    override suspend fun requestForDelivery(
        scheduleKey: String,
        scheduledOccurrence: Instant,
    ): ReminderRequest? {
        val vault = readyVault() ?: return null
        vault.profiles.forEach { record ->
            val profileId = record.profile.id.toString()
            record.medications
                .asSequence()
                .filter { it.isActive && it.remindersEnabled }
                .flatMap { medicationRequests(profileId, it) }
                .firstOrNull { ReminderScheduleKey.from(it.id) == scheduleKey }
                ?.let { return it }
        }
        val schedule = vault.schedules.firstOrNull {
            ReminderScheduleKey.from("schedule:${it.id}") == scheduleKey
        } ?: return null
        val expected = ScheduleCalculator.nextAlert(
            schedule,
            scheduledOccurrence.minusNanos(1),
            zoneIdProvider.current(),
        ) ?: return null
        return scheduleRequest(schedule, scheduledOccurrence)
            .takeIf { expected.toEpochMilli() == scheduledOccurrence.toEpochMilli() }
    }

    private fun medicationRequests(profileId: String, medication: Medication): Sequence<ReminderRequest> =
        medication.schedule.reminderTimes.asSequence().mapNotNull { localTime ->
            val recurrence = when (medication.schedule.recurrence) {
                VaultRecurrence.NONE -> medication.schedule.startsOn?.let { startsOn ->
                    ReminderRecurrence.Once(startsOn.atTime(localTime).atZone(zoneIdProvider.current()).toInstant())
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
                id = "profile:$profileId:medication:${medication.id}:$localTime",
                target = ReminderTarget.Medication(profileId, medication.id.toString()),
                title = medication.name,
                message = listOf(medication.dose, medication.instructions)
                    .filter(String::isNotBlank)
                    .joinToString(SEPARATOR),
                recurrence = recurrence,
            )
        }

    private fun scheduleRequest(schedule: Schedule): ReminderRequest? {
        val occurrence = ScheduleCalculator.nextAlert(schedule, clock.instant(), zoneIdProvider.current()) ?: return null
        return scheduleRequest(schedule, occurrence)
    }

    private fun scheduleRequest(schedule: Schedule, occurrence: Instant): ReminderRequest =
        ReminderRequest(
            id = "schedule:${schedule.id}",
            target = ReminderTarget.Schedule(schedule.id.toString()),
            title = schedule.title,
            message = listOf(schedule.people.joinToString(), schedule.location.orEmpty())
                .filter(String::isNotBlank)
                .joinToString(SEPARATOR),
            recurrence = ReminderRecurrence.Once(occurrence),
        )

    private suspend fun readyVault(): net.mamby.health.core.model.HealthVault? {
        if (vaultRepository.state.value is VaultState.Loading) vaultRepository.initialize()
        return (vaultRepository.state.value as? VaultState.Ready)?.vault
    }

    private companion object {
        const val SEPARATOR = " · "
    }
}

package net.mamby.health.notifications

import java.time.Instant

interface ReminderScheduler {
    suspend fun schedule(request: ReminderRequest)

    suspend fun cancel(reminderId: String)

    suspend fun reconcile(requests: Collection<ReminderRequest>)

    suspend fun cancelAll()

    suspend fun scheduleFollowing(request: ReminderRequest)
}

interface ReminderSource {
    suspend fun activeReminderRequests(): List<ReminderRequest>

    suspend fun requestForDelivery(scheduleKey: String, scheduledOccurrence: Instant): ReminderRequest? =
        activeReminderRequests().firstOrNull { ReminderScheduleKey.from(it.id) == scheduleKey }
}

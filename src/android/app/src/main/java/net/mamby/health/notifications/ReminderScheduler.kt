package net.mamby.health.notifications

interface ReminderScheduler {
    suspend fun schedule(request: ReminderRequest)

    suspend fun cancel(reminderId: String)

    suspend fun reconcile(requests: Collection<ReminderRequest>)

    suspend fun cancelAll()

    suspend fun scheduleFollowing(request: ReminderRequest)
}

interface ReminderSource {
    suspend fun activeReminderRequests(): List<ReminderRequest>
}

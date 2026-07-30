package net.mamby.health.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Instant

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val publisher: NotificationPublisher,
    private val scheduler: ReminderScheduler,
    private val source: ReminderSource,
    private val clock: Clock,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val scheduleKey = inputData.getString(SCHEDULE_KEY) ?: return Result.failure()
        val scheduledEpochMillis = inputData.getLong(OCCURRENCE_KEY, Long.MIN_VALUE)
        if (scheduledEpochMillis == Long.MIN_VALUE) return Result.failure()

        return runCatching {
            val request = source.activeReminderRequests()
                .firstOrNull { ReminderScheduleKey.from(it.id) == scheduleKey }
                ?: return@runCatching Result.success()
            if (clock.instant().isBefore(Instant.ofEpochMilli(scheduledEpochMillis))) {
                scheduler.scheduleFollowing(request)
                return@runCatching Result.success()
            }
            publisher.publish(request)
            scheduler.scheduleFollowing(request)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val SCHEDULE_KEY = "schedule_key"
        const val OCCURRENCE_KEY = "occurrence_epoch_millis"
    }
}

@HiltWorker
class ReminderReconciliationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val source: ReminderSource,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        scheduler.reconcile(source.activeReminderRequests())
        Result.success()
    }.getOrElse { Result.retry() }
}

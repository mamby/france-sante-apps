package net.mamby.health.notifications

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState
import net.mamby.health.di.ApplicationScope

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val calculator: NextOccurrenceCalculator,
    private val clock: Clock,
    private val zoneIdProvider: ZoneIdProvider,
    private val notificationPublisher: NotificationPublisher,
    private val reminderSource: ReminderSource,
    private val vaultRepository: VaultRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : ReminderScheduler {
    init {
        applicationScope.launch {
            var wasReady = false
            vaultRepository.state
                .map { state -> (state as? VaultState.Ready)?.vault?.revision }
                .distinctUntilChanged()
                .collect { revision ->
                    if (revision != null) {
                        reconcile(reminderSource.activeReminderRequests())
                        wasReady = true
                    } else if (wasReady) {
                        cancelAll()
                        wasReady = false
                    }
                }
        }
    }

    override suspend fun schedule(request: ReminderRequest) {
        enqueue(request, ExistingWorkPolicy.REPLACE)
    }

    override suspend fun cancel(reminderId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(reminderId))
        notificationPublisher.cancel(reminderId)
    }

    override suspend fun reconcile(requests: Collection<ReminderRequest>) {
        workManager.cancelAllWorkByTag(REMINDER_WORK_TAG)
        notificationPublisher.cancelAll()
        requests.filter(ReminderRequest::enabled).forEach { request ->
            enqueue(request, ExistingWorkPolicy.REPLACE)
        }
    }

    override suspend fun cancelAll() {
        workManager.cancelAllWorkByTag(REMINDER_WORK_TAG)
        notificationPublisher.cancelAll()
    }

    override suspend fun scheduleFollowing(request: ReminderRequest) {
        enqueue(request, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(request: ReminderRequest, policy: ExistingWorkPolicy) {
        if (!request.enabled) return
        val now = clock.instant()
        val occurrence = calculator.nextOccurrence(
            recurrence = request.recurrence,
            strictlyAfter = now,
            zoneId = zoneIdProvider.current(),
        ) ?: return
        val delay = Duration.between(now, occurrence).coerceAtLeast(Duration.ZERO)
        val input = androidx.work.Data.Builder()
            .putString(ReminderWorker.SCHEDULE_KEY, ReminderScheduleKey.from(request.id))
            .putLong(ReminderWorker.OCCURRENCE_KEY, occurrence.toEpochMilli())
            .build()
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delay)
            .addTag(REMINDER_WORK_TAG)
            .addTag(reminderTag(request.id))
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(request.id), policy, work)
    }

    private fun uniqueWorkName(id: String) = "health-reminder:${ReminderScheduleKey.from(id)}"

    private fun reminderTag(id: String) = "health-reminder-id:${ReminderScheduleKey.from(id)}"

    companion object {
        const val REMINDER_WORK_TAG = "health-reminders"
    }
}

internal object ReminderScheduleKey {
    fun from(reminderId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(reminderId.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

interface ZoneIdProvider {
    fun current(): ZoneId
}

class SystemZoneIdProvider @Inject constructor() : ZoneIdProvider {
    override fun current(): ZoneId = ZoneId.systemDefault()
}

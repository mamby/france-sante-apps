package net.mamby.health.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination
import net.mamby.health.data.ImportedDocumentData
import net.mamby.health.data.MedicalDocumentDraft
import net.mamby.health.data.RestoreDocumentBlob
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState
import net.mamby.health.testing.StubVaultRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerReminderSchedulerInstrumentedTest {
    private lateinit var workManager: WorkManager
    private lateinit var applicationScope: CoroutineScope
    private lateinit var vaultRepository: SchedulerVaultRepository
    private lateinit var reminderSource: RecordingReminderSource
    private lateinit var notificationPublisher: RecordingNotificationPublisher
    private lateinit var scheduler: WorkManagerReminderScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        vaultRepository = SchedulerVaultRepository()
        reminderSource = RecordingReminderSource()
        notificationPublisher = RecordingNotificationPublisher()
        scheduler = WorkManagerReminderScheduler(
            workManager = workManager,
            calculator = NextOccurrenceCalculator(),
            clock = FIXED_CLOCK,
            zoneIdProvider = FixedZoneIdProvider,
            notificationPublisher = notificationPublisher,
            reminderSource = reminderSource,
            vaultRepository = vaultRepository,
            applicationScope = applicationScope,
        )
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        workManager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun schedule_enqueuesAUniqueTaggedWorkerWithTheCalculatedDelay() = runBlocking {
        val request = reminderRequest(
            id = "medication:morning",
            occurrence = FIXED_NOW.plus(Duration.ofMinutes(90)),
        )

        scheduler.schedule(request)

        val work = workFor(request.id).single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(Duration.ofMinutes(90).toMillis(), work.initialDelayMillis)
        assertTrue(WorkManagerReminderScheduler.REMINDER_WORK_TAG in work.tags)
        assertTrue(reminderTag(request.id) in work.tags)
    }

    @Test
    fun schedule_replacesThePendingOccurrenceForTheSameReminder() = runBlocking {
        val first = reminderRequest("appointment:checkup", FIXED_NOW.plus(Duration.ofHours(1)))
        val replacement = reminderRequest("appointment:checkup", FIXED_NOW.plus(Duration.ofHours(2)))

        scheduler.schedule(first)
        scheduler.schedule(replacement)

        val unfinished = workFor(replacement.id).filterNot { it.state.isFinished }
        assertEquals(1, unfinished.size)
        assertEquals(Duration.ofHours(2).toMillis(), unfinished.single().initialDelayMillis)
    }

    @Test
    fun scheduleFollowing_appendsBehindThePendingOccurrence() = runBlocking {
        val request = reminderRequest("reminder:hydration", FIXED_NOW.plus(Duration.ofHours(1)))

        scheduler.schedule(request)
        scheduler.scheduleFollowing(request)

        assertEquals(
            setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED),
            workFor(request.id).map(WorkInfo::state).toSet(),
        )
    }

    @Test
    fun schedule_ignoresDisabledAndExpiredReminders() = runBlocking {
        scheduler.schedule(
            reminderRequest(
                id = "reminder:disabled",
                occurrence = FIXED_NOW.plus(Duration.ofHours(1)),
                enabled = false,
            ),
        )
        scheduler.schedule(reminderRequest("reminder:expired", FIXED_NOW.minusSeconds(1)))

        assertTrue(workManager.getWorkInfosByTag(WorkManagerReminderScheduler.REMINDER_WORK_TAG).get().isEmpty())
    }

    @Test
    fun reconcile_cancelsStaleWorkAndQueuesOnlyEnabledFutureReminders() = runBlocking {
        val stale = reminderRequest("reminder:stale", FIXED_NOW.plus(Duration.ofHours(1)))
        val active = reminderRequest("reminder:active", FIXED_NOW.plus(Duration.ofHours(2)))
        val disabled = reminderRequest(
            id = "reminder:disabled",
            occurrence = FIXED_NOW.plus(Duration.ofHours(3)),
            enabled = false,
        )
        val expired = reminderRequest("reminder:expired", FIXED_NOW.minusSeconds(1))
        scheduler.schedule(stale)

        scheduler.reconcile(listOf(active, disabled, expired))

        assertTrue(workFor(stale.id).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(listOf(WorkInfo.State.ENQUEUED), workFor(active.id).map(WorkInfo::state))
        assertTrue(workFor(disabled.id).isEmpty())
        assertTrue(workFor(expired.id).isEmpty())
    }

    @Test
    fun cancel_stopsUniqueWorkAndClearsItsDeliveredNotification() = runBlocking {
        val request = reminderRequest("medication:evening", FIXED_NOW.plus(Duration.ofHours(1)))
        scheduler.schedule(request)

        scheduler.cancel(request.id)

        assertTrue(workFor(request.id).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(listOf(request.id), notificationPublisher.cancelledReminderIds)
    }

    @Test
    fun vaultReadyAndUnavailableTransitions_reconcileThenCancelAllReminders() {
        val request = reminderRequest("reminder:vault-observer", FIXED_NOW.plus(Duration.ofHours(1)))
        reminderSource.requests = listOf(request)

        vaultRepository.mutableState.value = VaultState.Ready(
            healthVault(revision = 1),
        )

        assertEquals(listOf(WorkInfo.State.ENQUEUED), workFor(request.id).map(WorkInfo::state))

        vaultRepository.mutableState.value = VaultState.Missing

        assertTrue(workFor(request.id).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(2, notificationPublisher.cancelAllCalls)
    }

    private fun workFor(reminderId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(uniqueWorkName(reminderId)).get()

    private fun uniqueWorkName(reminderId: String) =
        "health-reminder:${ReminderScheduleKey.from(reminderId)}"

    private fun reminderTag(reminderId: String) =
        "health-reminder-id:${ReminderScheduleKey.from(reminderId)}"

    private fun reminderRequest(
        id: String,
        occurrence: Instant,
        enabled: Boolean = true,
    ) = ReminderRequest(
        id = id,
        profileId = PROFILE_ID.toString(),
        type = ReminderType.GENERAL,
        title = "Reminder",
        message = "Message",
        recurrence = ReminderRecurrence.Once(occurrence),
        enabled = enabled,
    )

    private fun healthVault(revision: Long): HealthVault = HealthVault.empty(
        now = FIXED_NOW,
        profileId = PROFILE_ID,
        displayName = "Owner",
    ).copy(revision = revision)

    private object FixedZoneIdProvider : ZoneIdProvider {
        override fun current() = ZoneOffset.UTC
    }

    private class RecordingReminderSource : ReminderSource {
        var requests: List<ReminderRequest> = emptyList()

        override suspend fun activeReminderRequests(): List<ReminderRequest> = requests
    }

    private class RecordingNotificationPublisher : NotificationPublisher {
        val cancelledReminderIds = mutableListOf<String>()
        var cancelAllCalls = 0

        override fun permissionState() = NotificationPermissionState.GRANTED

        override fun publish(request: ReminderRequest) = NotificationPublishResult.Published

        override fun cancel(reminderId: String) {
            cancelledReminderIds += reminderId
        }

        override fun cancelAll() {
            cancelAllCalls += 1
        }
    }

    private class SchedulerVaultRepository : StubVaultRepository() {
        val mutableState = MutableStateFlow<VaultState>(VaultState.Loading)
        override val state: StateFlow<VaultState> = mutableState
    }

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        val PROFILE_ID: UUID = UUID.fromString("e10c32dc-0a48-41ab-a8ed-bc1420650e31")
    }
}

package net.mamby.health.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.data.VaultState
import net.mamby.health.testing.StubVaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultReminderSourceTest {
    @Test
    fun requestsIncludeEveryProfileWithCollisionFreeProfileScopedIds() = runTest {
        val vault = HealthVault(
            revision = 2,
            profiles = listOf(
                profileRecord(FIRST_PROFILE, FIRST_MEDICATION),
                profileRecord(SECOND_PROFILE, SECOND_MEDICATION),
            ),
            updatedAt = NOW,
        )
        val repository = object : StubVaultRepository() {
            override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Ready(vault, FIRST_PROFILE))
        }
        val source = VaultReminderSource(
            repository,
            object : ZoneIdProvider {
                override fun current() = ZoneOffset.UTC
            },
        )

        val requests = source.activeReminderRequests()

        assertEquals(
            setOf(FIRST_PROFILE.toString(), SECOND_PROFILE.toString()),
            requests.map { it.profileId }.toSet(),
        )
        assertEquals(requests.size, requests.map { it.id }.distinct().size)
        assertTrue(requests.all { it.id.startsWith("profile:${it.profileId}:") })
    }

    private fun profileRecord(profileId: UUID, medicationId: UUID) = ProfileRecord(
        profile = HealthProfile(profileId, "Owner", lastUpdatedAt = NOW),
        medications = listOf(
            Medication(
                id = medicationId,
                name = "Medication",
                dose = "5 mg",
                instructions = "Daily",
                schedule = MedicationSchedule(
                    recurrence = ReminderRecurrence.DAILY,
                    reminderTimes = listOf(LocalTime.of(8, 0)),
                    startsOn = LocalDate.of(2026, 7, 1),
                ),
                remindersEnabled = true,
                updatedAt = NOW,
            ),
        ),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIRST_PROFILE: UUID = UUID.fromString("a4a94b20-1489-4947-b89f-b463fb5d7e74")
        val SECOND_PROFILE: UUID = UUID.fromString("70ddbe28-78cd-45d8-aa9f-f012d8e911f5")
        val FIRST_MEDICATION: UUID = UUID.fromString("a5d9a8bb-3905-47c3-97a2-691c51532924")
        val SECOND_MEDICATION: UUID = UUID.fromString("d9dbf4da-8420-4dcc-a7e0-8cc06848e8e3")
    }
}

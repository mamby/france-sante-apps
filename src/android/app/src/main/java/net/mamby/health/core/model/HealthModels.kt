@file:kotlinx.serialization.UseSerializers(
    net.mamby.health.core.model.UuidSerializer::class,
    net.mamby.health.core.model.InstantSerializer::class,
    net.mamby.health.core.model.LocalDateSerializer::class,
    net.mamby.health.core.model.LocalTimeSerializer::class,
    net.mamby.health.core.model.DayOfWeekSerializer::class,
)

package net.mamby.health.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentCategory {
    ALL,
    LAB_RESULTS,
    PRESCRIPTIONS,
    REPORTS,
    VACCINATIONS,
    OTHER,
}

@Serializable
enum class ReminderRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

@Serializable
enum class VaultItemKind {
    DOCUMENT,
    MEDICATION,
    APPOINTMENT,
    VACCINATION,
    REMINDER,
}

@Serializable
data class EmergencyContact(
    val id: UUID,
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val notes: String? = null,
)

@Serializable
data class HealthProfile(
    val id: UUID,
    val displayName: String,
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val surgeries: List<String> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val lastUpdatedAt: Instant,
)

@Serializable
data class MedicalDocument(
    val id: UUID,
    val title: String,
    val category: DocumentCategory,
    val documentDate: LocalDate,
    val source: String,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val blobId: UUID,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFileName: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class MedicationSchedule(
    val recurrence: ReminderRecurrence = ReminderRecurrence.NONE,
    val reminderTimes: List<LocalTime> = emptyList(),
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
)

@Serializable
data class Medication(
    val id: UUID,
    val name: String,
    val dose: String,
    val instructions: String,
    val schedule: MedicationSchedule = MedicationSchedule(),
    val isActive: Boolean = true,
    val remindersEnabled: Boolean = false,
    val notes: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class Appointment(
    val id: UUID,
    val title: String,
    val clinician: String,
    val location: String,
    val startsAt: Instant,
    val relatedDocumentIds: List<UUID> = emptyList(),
    val notes: String? = null,
    val reminderLeadMinutes: Long? = null,
    val updatedAt: Instant,
)

@Serializable
data class Vaccination(
    val id: UUID,
    val name: String,
    val dateAdministered: LocalDate,
    val provider: String? = null,
    val lotNumber: String? = null,
    val nextDueOn: LocalDate? = null,
    val updatedAt: Instant,
)

@Serializable
data class Reminder(
    val id: UUID,
    val title: String,
    val startsOn: LocalDate,
    val timeOfDay: LocalTime,
    val recurrence: ReminderRecurrence = ReminderRecurrence.NONE,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val endsOn: LocalDate? = null,
    val isEnabled: Boolean = true,
    val notes: String? = null,
    val updatedAt: Instant,
)

data class HealthSummary(
    val bloodType: String?,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val surgeries: List<String>,
    val emergencyContacts: List<EmergencyContact>,
    val lastUpdatedAt: Instant,
)

data class VaultItem(
    val id: UUID,
    val kind: VaultItemKind,
    val title: String,
    val updatedAt: Instant,
)

@Serializable
data class ProfileRecord(
    val profile: HealthProfile,
    val documents: List<MedicalDocument> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

@Serializable
data class HealthVault(
    val version: Int = CURRENT_VERSION,
    val revision: Long,
    val profiles: List<ProfileRecord>,
    val updatedAt: Instant,
) {
    companion object {
        const val CURRENT_VERSION: Int = 2

        fun empty(
            now: Instant,
            profileId: UUID = UUID.randomUUID(),
            displayName: String,
        ): HealthVault = HealthVault(
            revision = 0,
            profiles = listOf(
                ProfileRecord(
                    profile = HealthProfile(
                        id = profileId,
                        displayName = displayName,
                        lastUpdatedAt = now,
                    ),
                ),
            ),
            updatedAt = now,
        )
    }
}

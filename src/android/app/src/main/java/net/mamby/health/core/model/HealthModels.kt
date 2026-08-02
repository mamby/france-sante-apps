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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BuiltInDocumentCategory {
    LAB_RESULTS,
    PRESCRIPTIONS,
    REPORTS,
    VACCINATIONS,
    INVOICES_RECEIPTS,
    DIRECTIVES,
    OTHER,
}

@Serializable
sealed interface DocumentCategoryRef {
    @Serializable
    @SerialName("builtIn")
    data class BuiltIn(val category: BuiltInDocumentCategory) : DocumentCategoryRef

    @Serializable
    @SerialName("custom")
    data class Custom(val id: UUID) : DocumentCategoryRef
}

fun BuiltInDocumentCategory.asReference(): DocumentCategoryRef =
    DocumentCategoryRef.BuiltIn(this)

@Serializable
data class BuiltInDocumentCategoryPreference(
    val category: BuiltInDocumentCategory,
    val labelOverride: String? = null,
    val isHidden: Boolean = false,
)

@Serializable
data class CustomDocumentCategory(
    val id: UUID,
    val name: String,
    val updatedAt: Instant,
)

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
    NOTE,
    MEASUREMENT,
    DIRECTORY_ENTRY,
    FAMILY_HISTORY,
    DIRECTIVE,
    IDENTIFIER,
}

@Serializable
enum class BuiltInMeasurementType {
    WEIGHT,
    HEIGHT,
    BLOOD_PRESSURE,
    PULSE,
    TEMPERATURE,
    OXYGEN_SATURATION,
    BLOOD_GLUCOSE,
}

@Serializable
sealed interface MeasurementTypeRef {
    @Serializable
    @SerialName("builtIn")
    data class BuiltIn(
        @SerialName("measurementType") val type: BuiltInMeasurementType,
    ) : MeasurementTypeRef

    @Serializable
    @SerialName("custom")
    data class Custom(val id: UUID) : MeasurementTypeRef
}

@Serializable
enum class MeasurementUnit {
    KILOGRAM,
    POUND,
    CENTIMETER,
    INCH,
    CELSIUS,
    FAHRENHEIT,
    BEATS_PER_MINUTE,
    PERCENT,
    MILLIMETERS_OF_MERCURY,
    MILLIGRAMS_PER_DECILITER,
    MILLIMOLES_PER_LITER,
}

@Serializable
sealed interface MeasurementUnitRef {
    @Serializable
    @SerialName("builtIn")
    data class BuiltIn(val unit: MeasurementUnit) : MeasurementUnitRef

    @Serializable
    @SerialName("custom")
    data class Custom(val symbol: String) : MeasurementUnitRef
}

@Serializable
sealed interface MeasurementReading {
    @Serializable
    @SerialName("scalar")
    data class Scalar(
        val value: Double,
        val unit: MeasurementUnitRef,
    ) : MeasurementReading

    @Serializable
    @SerialName("bloodPressure")
    data class BloodPressure(
        val systolic: Double,
        val diastolic: Double,
        val pulseBeatsPerMinute: Double? = null,
        val unit: MeasurementUnitRef = MeasurementUnitRef.BuiltIn(
            MeasurementUnit.MILLIMETERS_OF_MERCURY,
        ),
    ) : MeasurementReading
}

@Serializable
data class CustomMeasurementType(
    val id: UUID,
    val name: String,
    val suggestedUnit: String,
    val updatedAt: Instant,
)

@Serializable
data class HealthMeasurement(
    val id: UUID,
    val type: MeasurementTypeRef,
    val reading: MeasurementReading,
    val measuredAt: Instant,
    val notes: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class HealthNote(
    val id: UUID,
    val title: String,
    val body: String,
    val notedAt: Instant,
    val updatedAt: Instant,
)

@Serializable
enum class CareDirectoryKind {
    DOCTOR,
    HOSPITAL,
    CLINIC,
    PHARMACY,
    LABORATORY,
    OTHER,
}

@Serializable
data class PostalAddress(
    val addressLines: List<String> = emptyList(),
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)

@Serializable
data class CareDirectoryEntry(
    val id: UUID,
    val kind: CareDirectoryKind,
    val name: String,
    val specialty: String? = null,
    val organization: String? = null,
    val address: PostalAddress = PostalAddress(),
    val phoneNumbers: List<String> = emptyList(),
    val emailAddresses: List<String> = emptyList(),
    val notes: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class FamilyHistoryEntry(
    val id: UUID,
    val relationship: String,
    val condition: String,
    val ageAtOnsetYears: Int? = null,
    val notes: String? = null,
    val updatedAt: Instant,
)

@Serializable
enum class CareDirectiveKind {
    ADVANCE_DIRECTIVE,
    CARE_PREFERENCE,
    PROCEDURE_CONSENT_RECORD,
    OTHER,
}

@Serializable
data class CareDirective(
    val id: UUID,
    val kind: CareDirectiveKind,
    val title: String,
    val text: String,
    val recordedOn: LocalDate,
    val relatedDocumentIds: List<UUID> = emptyList(),
    val updatedAt: Instant,
)

@Serializable
enum class HealthIdentifierKind {
    NATIONAL_HEALTH,
    SOCIAL_SECURITY,
    INSURANCE,
    PATIENT,
    OTHER,
}

@Serializable
data class HealthIdentifier(
    val id: UUID,
    val kind: HealthIdentifierKind,
    val label: String,
    val value: String,
    val issuer: String? = null,
    val country: String? = null,
    val notes: String? = null,
    val updatedAt: Instant,
)

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
    val primaryDoctorEntryId: UUID? = null,
    val lastUpdatedAt: Instant,
)

@Serializable
data class MedicalDocument(
    val id: UUID,
    val title: String,
    val category: DocumentCategoryRef,
    val documentDate: LocalDate,
    val source: String,
    val sourceEntryId: UUID? = null,
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
    val prescriberEntryId: UUID? = null,
    val pharmacyEntryId: UUID? = null,
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
    val clinicianEntryId: UUID? = null,
    val facilityEntryId: UUID? = null,
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
    val providerEntryId: UUID? = null,
    val lotNumber: String? = null,
    val nextDueOn: LocalDate? = null,
    val notes: String? = null,
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
    val notes: List<HealthNote> = emptyList(),
    val measurements: List<HealthMeasurement> = emptyList(),
    val customMeasurementTypes: List<CustomMeasurementType> = emptyList(),
    val careDirectory: List<CareDirectoryEntry> = emptyList(),
    val familyHistory: List<FamilyHistoryEntry> = emptyList(),
    val directives: List<CareDirective> = emptyList(),
    val healthIdentifiers: List<HealthIdentifier> = emptyList(),
    val customDocumentCategories: List<CustomDocumentCategory> = emptyList(),
    val builtInDocumentCategoryPreferences: List<BuiltInDocumentCategoryPreference> = emptyList(),
)

@Serializable
data class HealthVault(
    val version: Int = CURRENT_VERSION,
    val revision: Long,
    val profiles: List<ProfileRecord>,
    val updatedAt: Instant,
) {
    companion object {
        const val CURRENT_VERSION: Int = 3

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

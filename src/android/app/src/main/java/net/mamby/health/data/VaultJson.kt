@file:kotlinx.serialization.UseSerializers(
    net.mamby.health.core.model.UuidSerializer::class,
    net.mamby.health.core.model.InstantSerializer::class,
    net.mamby.health.core.model.LocalDateSerializer::class,
    net.mamby.health.core.model.LocalTimeSerializer::class,
    net.mamby.health.core.model.DayOfWeekSerializer::class,
)

package net.mamby.health.data

import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.Vaccination
import net.mamby.health.core.model.asReference
import net.mamby.health.core.model.requireValid

data class DecodedVault(
    val sourceVersion: Int,
    val vault: HealthVault,
)

object VaultCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun encode(vault: HealthVault): ByteArray =
        json.encodeToString(vault.requireValid()).toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): DecodedVault {
        val source = bytes.toString(StandardCharsets.UTF_8)
        val version = json.parseToJsonElement(source)
            .jsonObject["version"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: throw IllegalArgumentException("Vault schema version is missing.")
        val vault = when (version) {
            1 -> json.decodeFromString<HealthVaultV1>(source).toCurrent()
            2 -> json.decodeFromString<HealthVaultV2>(source).toCurrent()
            HealthVault.CURRENT_VERSION -> json.decodeFromString<HealthVault>(source)
            else -> throw UnsupportedVaultVersionException(version)
        }
        return DecodedVault(version, vault.requireValid())
    }
}

@Serializable
private enum class DocumentCategoryV2 {
    ALL,
    LAB_RESULTS,
    PRESCRIPTIONS,
    REPORTS,
    VACCINATIONS,
    OTHER,
}

private fun DocumentCategoryV2.toCurrent(): DocumentCategoryRef = when (this) {
    DocumentCategoryV2.LAB_RESULTS -> BuiltInDocumentCategory.LAB_RESULTS.asReference()
    DocumentCategoryV2.PRESCRIPTIONS -> BuiltInDocumentCategory.PRESCRIPTIONS.asReference()
    DocumentCategoryV2.REPORTS -> BuiltInDocumentCategory.REPORTS.asReference()
    DocumentCategoryV2.VACCINATIONS -> BuiltInDocumentCategory.VACCINATIONS.asReference()
    DocumentCategoryV2.OTHER -> BuiltInDocumentCategory.OTHER.asReference()
    DocumentCategoryV2.ALL -> throw IllegalArgumentException("ALL cannot be stored as a document category.")
}

@Serializable
private data class EmergencyContactV2(
    val id: UUID,
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val notes: String? = null,
) {
    fun toCurrent() = EmergencyContact(id, name, relationship, phoneNumber, notes)
}

@Serializable
private data class HealthProfileV2(
    val id: UUID,
    val displayName: String,
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val surgeries: List<String> = emptyList(),
    val emergencyContacts: List<EmergencyContactV2> = emptyList(),
    val lastUpdatedAt: Instant,
) {
    fun toCurrent() = HealthProfile(
        id = id,
        displayName = displayName,
        bloodType = bloodType,
        allergies = allergies,
        chronicConditions = chronicConditions,
        surgeries = surgeries,
        emergencyContacts = emergencyContacts.map(EmergencyContactV2::toCurrent),
        lastUpdatedAt = lastUpdatedAt,
    )
}

@Serializable
private data class MedicalDocumentV2(
    val id: UUID,
    val title: String,
    val category: DocumentCategoryV2,
    val documentDate: LocalDate,
    val source: String,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val blobId: UUID,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFileName: String? = null,
    val updatedAt: Instant,
) {
    fun toCurrent() = MedicalDocument(
        id = id,
        title = title,
        category = category.toCurrent(),
        documentDate = documentDate,
        source = source,
        notes = notes,
        tags = tags,
        blobId = blobId,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        originalFileName = originalFileName,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class MedicationScheduleV2(
    val recurrence: ReminderRecurrence = ReminderRecurrence.NONE,
    val reminderTimes: List<LocalTime> = emptyList(),
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
) {
    fun toCurrent() = MedicationSchedule(recurrence, reminderTimes, daysOfWeek, startsOn, endsOn)
}

@Serializable
private data class MedicationV2(
    val id: UUID,
    val name: String,
    val dose: String,
    val instructions: String,
    val schedule: MedicationScheduleV2 = MedicationScheduleV2(),
    val isActive: Boolean = true,
    val remindersEnabled: Boolean = false,
    val notes: String? = null,
    val updatedAt: Instant,
) {
    fun toCurrent() = Medication(
        id = id,
        name = name,
        dose = dose,
        instructions = instructions,
        schedule = schedule.toCurrent(),
        isActive = isActive,
        remindersEnabled = remindersEnabled,
        notes = notes,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class AppointmentV2(
    val id: UUID,
    val title: String,
    val clinician: String,
    val location: String,
    val startsAt: Instant,
    val relatedDocumentIds: List<UUID> = emptyList(),
    val notes: String? = null,
    val reminderLeadMinutes: Long? = null,
    val updatedAt: Instant,
) {
    fun toCurrent() = Appointment(
        id = id,
        title = title,
        clinician = clinician,
        location = location,
        startsAt = startsAt,
        relatedDocumentIds = relatedDocumentIds,
        notes = notes,
        reminderLeadMinutes = reminderLeadMinutes,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class VaccinationV2(
    val id: UUID,
    val name: String,
    val dateAdministered: LocalDate,
    val provider: String? = null,
    val lotNumber: String? = null,
    val nextDueOn: LocalDate? = null,
    val updatedAt: Instant,
) {
    fun toCurrent() = Vaccination(
        id = id,
        name = name,
        dateAdministered = dateAdministered,
        provider = provider,
        lotNumber = lotNumber,
        nextDueOn = nextDueOn,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class ReminderV2(
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
) {
    fun toCurrent() = Reminder(
        id,
        title,
        startsOn,
        timeOfDay,
        recurrence,
        daysOfWeek,
        endsOn,
        isEnabled,
        notes,
        updatedAt,
    )
}

@Serializable
private data class ProfileRecordV2(
    val profile: HealthProfileV2,
    val documents: List<MedicalDocumentV2> = emptyList(),
    val medications: List<MedicationV2> = emptyList(),
    val appointments: List<AppointmentV2> = emptyList(),
    val vaccinations: List<VaccinationV2> = emptyList(),
    val reminders: List<ReminderV2> = emptyList(),
) {
    fun toCurrent() = ProfileRecord(
        profile = profile.toCurrent(),
        documents = documents.map(MedicalDocumentV2::toCurrent),
        medications = medications.map(MedicationV2::toCurrent),
        appointments = appointments.map(AppointmentV2::toCurrent),
        vaccinations = vaccinations.map(VaccinationV2::toCurrent),
        reminders = reminders.map(ReminderV2::toCurrent),
    )
}

@Serializable
private data class HealthVaultV2(
    val version: Int = 2,
    val revision: Long,
    val profiles: List<ProfileRecordV2>,
    val updatedAt: Instant,
) {
    fun toCurrent() = HealthVault(
        revision = revision,
        profiles = profiles.map(ProfileRecordV2::toCurrent),
        updatedAt = updatedAt,
    )
}

@Serializable
private data class HealthVaultV1(
    val version: Int = 1,
    val revision: Long,
    val profile: HealthProfileV2,
    val documents: List<MedicalDocumentV2> = emptyList(),
    val medications: List<MedicationV2> = emptyList(),
    val appointments: List<AppointmentV2> = emptyList(),
    val vaccinations: List<VaccinationV2> = emptyList(),
    val reminders: List<ReminderV2> = emptyList(),
    val updatedAt: Instant,
) {
    fun toCurrent(): HealthVault = HealthVault(
        revision = revision,
        profiles = listOf(
            ProfileRecord(
                profile = profile.toCurrent(),
                documents = documents.map(MedicalDocumentV2::toCurrent),
                medications = medications.map(MedicationV2::toCurrent),
                appointments = appointments.map(AppointmentV2::toCurrent),
                vaccinations = vaccinations.map(VaccinationV2::toCurrent),
                reminders = reminders.map(ReminderV2::toCurrent),
            ),
        ),
        updatedAt = updatedAt,
    )
}

package net.mamby.health.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import net.mamby.health.R
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.core.model.Vaccination
import net.mamby.health.notifications.ZoneIdProvider

/** Creates localized, deterministic sample records that are never persisted. */
@Singleton
class LocalizedDemoVaultProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneIdProvider: ZoneIdProvider,
) : DemoVaultProvider {
    override fun create(now: Instant): HealthVault {
        val zoneId = zoneIdProvider.current()
        val today = now.atZone(zoneId).toLocalDate()
        val contact = EmergencyContact(
            id = id("0f28b2f5-3a54-4d1e-988b-837e9774c0c7"),
            name = text(R.string.demo_contact_name),
            relationship = text(R.string.demo_contact_relationship),
            phoneNumber = text(R.string.demo_contact_phone),
            notes = text(R.string.demo_contact_notes),
        )
        val bloodPanelId = id("45e8bfcc-8382-4d5e-91a8-c883186f2bc9")
        val prescriptionId = id("db20938a-98a4-4bf7-b3cc-6d43dc5e0e35")
        val vaccinationDocumentId = id("08e63f7b-64c0-4e2b-99e1-574b2f53ac69")
        val documents = listOf(
            sampleDocument(
                id = bloodPanelId,
                blobId = id("1c4bebc0-7a92-4d45-ae07-e120118ce182"),
                title = text(R.string.demo_document_blood_title),
                category = DocumentCategory.LAB_RESULTS,
                date = today.minusDays(30),
                source = text(R.string.demo_document_blood_source),
                notes = text(R.string.demo_document_blood_notes),
                updatedAt = now.minus(Duration.ofDays(2)),
            ),
            sampleDocument(
                id = prescriptionId,
                blobId = id("276378ac-599f-4e90-bad2-e01bd63854f2"),
                title = text(R.string.demo_document_inhaler_title),
                category = DocumentCategory.PRESCRIPTIONS,
                date = today.minusDays(15),
                source = text(R.string.demo_document_inhaler_source),
                notes = text(R.string.demo_document_inhaler_notes),
                updatedAt = now.minus(Duration.ofDays(1)),
            ),
            sampleDocument(
                id = vaccinationDocumentId,
                blobId = id("254612d6-e967-43f2-807f-c1270172d527"),
                title = text(R.string.demo_document_flu_title),
                category = DocumentCategory.VACCINATIONS,
                date = today.minusDays(90),
                source = text(R.string.demo_document_flu_source),
                notes = text(R.string.demo_document_flu_notes),
                updatedAt = now.minus(Duration.ofDays(3)),
            ),
        )
        val appointmentStart = today.plusDays(3).atTime(10, 30).atZone(zoneId).toInstant()

        return HealthVault(
            revision = 0,
            profile = HealthProfile(
                id = id("54b14ed3-9421-4660-b419-637afbd44ba7"),
                displayName = text(R.string.demo_profile_name),
                bloodType = text(R.string.demo_blood_type),
                allergies = listOf(text(R.string.demo_allergy_penicillin)),
                chronicConditions = listOf(text(R.string.demo_condition_asthma)),
                surgeries = listOf(text(R.string.demo_surgery_appendectomy)),
                emergencyContacts = listOf(contact),
                lastUpdatedAt = now.minus(Duration.ofDays(4)),
            ),
            documents = documents,
            medications = listOf(
                Medication(
                    id = id("6be3ed85-2c07-442c-b49b-60d340600818"),
                    name = text(R.string.demo_medication_name),
                    dose = text(R.string.demo_medication_dose),
                    instructions = text(R.string.demo_medication_instructions),
                    schedule = MedicationSchedule(
                        recurrence = ReminderRecurrence.DAILY,
                        reminderTimes = listOf(LocalTime.of(8, 0)),
                        daysOfWeek = DayOfWeek.entries.toSet(),
                        startsOn = today.minusDays(60),
                    ),
                    isActive = true,
                    remindersEnabled = true,
                    notes = text(R.string.demo_medication_notes),
                    updatedAt = now.minus(Duration.ofHours(12)),
                ),
            ),
            appointments = listOf(
                Appointment(
                    id = id("f887357c-8640-487b-97c5-66bde3472d57"),
                    title = text(R.string.demo_appointment_title),
                    clinician = text(R.string.demo_appointment_clinician),
                    location = text(R.string.demo_appointment_location),
                    startsAt = appointmentStart,
                    relatedDocumentIds = listOf(bloodPanelId),
                    notes = text(R.string.demo_appointment_notes),
                    reminderLeadMinutes = 60,
                    updatedAt = now.minus(Duration.ofHours(8)),
                ),
            ),
            vaccinations = listOf(
                Vaccination(
                    id = id("27076995-c423-45a5-a9c3-71422f08af46"),
                    name = text(R.string.demo_vaccination_name),
                    dateAdministered = today.minusDays(90),
                    provider = text(R.string.demo_vaccination_provider),
                    updatedAt = now.minus(Duration.ofDays(3)),
                ),
            ),
            reminders = listOf(
                Reminder(
                    id = id("5efe6e67-361d-4fc6-a1e0-f38842fc99bb"),
                    title = text(R.string.demo_reminder_title),
                    startsOn = today,
                    timeOfDay = LocalTime.of(20, 0),
                    recurrence = ReminderRecurrence.DAILY,
                    isEnabled = true,
                    notes = text(R.string.demo_reminder_notes),
                    updatedAt = now.minus(Duration.ofHours(4)),
                ),
            ),
            updatedAt = now,
        )
    }

    private fun sampleDocument(
        id: UUID,
        blobId: UUID,
        title: String,
        category: DocumentCategory,
        date: java.time.LocalDate,
        source: String,
        notes: String,
        updatedAt: Instant,
    ) = MedicalDocument(
        id = id,
        title = title,
        category = category,
        documentDate = date,
        source = source,
        notes = notes,
        blobId = blobId,
        mimeType = "application/pdf",
        sizeBytes = 0,
        originalFileName = null,
        updatedAt = updatedAt,
    )

    private fun text(resourceId: Int): String = context.getString(resourceId)

    private fun id(value: String): UUID = UUID.fromString(value)
}

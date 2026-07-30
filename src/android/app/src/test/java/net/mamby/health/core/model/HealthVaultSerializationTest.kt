package net.mamby.health.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HealthVaultSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun versionedVaultRoundTripsAllJavaTypesWithoutPersistingDerivedViews() {
        val now = Instant.parse("2026-07-30T08:15:30Z")
        val contactId = UUID.fromString("3768e039-aa59-48f0-99c0-7b4a2e4af7a2")
        val documentId = UUID.fromString("9eb4c6cb-7a77-4c2f-891b-d437fe7a7d98")
        val blobId = UUID.fromString("27d14e33-91aa-47d5-bf19-fd7beb082d96")
        val vault = HealthVault(
            revision = 7,
            profile = HealthProfile(
                id = UUID.fromString("2361588e-ee3f-466b-b054-6d8f4f132c60"),
                displayName = "Amina",
                bloodType = "O+",
                allergies = listOf("Penicillin"),
                emergencyContacts = listOf(
                    EmergencyContact(contactId, "Alex", "Partner", "+33 1 23 45 67 89"),
                ),
                lastUpdatedAt = now,
            ),
            documents = listOf(
                MedicalDocument(
                    id = documentId,
                    title = "Lab result",
                    category = DocumentCategory.LAB_RESULTS,
                    documentDate = LocalDate.of(2026, 7, 28),
                    source = "Laboratory",
                    blobId = blobId,
                    mimeType = "application/pdf",
                    sizeBytes = 1_024,
                    originalFileName = "result.pdf",
                    updatedAt = now,
                ),
            ),
            medications = listOf(
                Medication(
                    id = UUID.fromString("4f74943a-2fac-41ad-a45d-08e3886d0c9c"),
                    name = "Example medication",
                    dose = "1 tablet",
                    instructions = "With food",
                    schedule = MedicationSchedule(
                        recurrence = ReminderRecurrence.WEEKLY,
                        reminderTimes = listOf(LocalTime.of(8, 30), LocalTime.of(20, 30)),
                        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                        startsOn = LocalDate.of(2026, 7, 1),
                    ),
                    remindersEnabled = true,
                    updatedAt = now,
                ),
            ),
            updatedAt = now,
        ).requireValid()

        val encoded = json.encodeToString(vault)
        val decoded = json.decodeFromString<HealthVault>(encoded)

        assertEquals(vault, decoded)
        assertEquals("O+", decoded.summary().bloodType)
        assertEquals(listOf(documentId), decoded.index().filter { it.kind == VaultItemKind.DOCUMENT }.map { it.id })
        assertFalse(encoded.contains("\"summary\""))
        assertFalse(encoded.contains("\"vaultItems\""))
    }

    @Test
    fun versionOnePayloadWithoutLaterOptionalCollectionsKeepsItsDefaults() {
        val payload = """
            {
              "version": 1,
              "revision": 3,
              "profile": {
                "id": "2361588e-ee3f-466b-b054-6d8f4f132c60",
                "displayName": "Amina",
                "lastUpdatedAt": "2026-07-30T08:15:30Z"
              },
              "updatedAt": "2026-07-30T08:15:30Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<HealthVault>(payload).requireValid()

        assertEquals(3L, decoded.revision)
        assertEquals("Amina", decoded.profile.displayName)
        assertEquals(emptyList<MedicalDocument>(), decoded.documents)
        assertEquals(emptyList<Medication>(), decoded.medications)
        assertEquals(emptyList<Appointment>(), decoded.appointments)
        assertEquals(emptyList<Vaccination>(), decoded.vaccinations)
        assertEquals(emptyList<Reminder>(), decoded.reminders)
    }
}

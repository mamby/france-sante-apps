package net.mamby.health.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.SerializationException
import net.mamby.health.data.VaultCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthVaultSerializationTest {
    private val now = Instant.parse("2026-07-30T08:15:30Z")

    @Test
    fun schemaV1RoundTripsEmptyHealthData() {
        val vault = HealthVault.empty(now).requireValid()

        val encoded = VaultCodec.encode(vault)
        val decoded = VaultCodec.decode(encoded)

        assertEquals(1, decoded.sourceVersion)
        assertEquals(vault, decoded.vault)
        assertTrue(decoded.vault.profiles.isEmpty())
        assertEquals(0L, decoded.vault.revision)
    }

    @Test
    fun schemaV1RoundTripsProfilesAndSharedItemsWithoutDerivedViews() {
        val firstId = UUID.fromString("2361588e-ee3f-466b-b054-6d8f4f132c60")
        val secondId = UUID.fromString("5b49ac57-8a98-41c4-b66b-b6f69cfd9b04")
        val documentId = UUID.fromString("9eb4c6cb-7a77-4c2f-891b-d437fe7a7d98")
        val measurementId = UUID.fromString("8c918e28-6344-484b-a969-a2d23c109bf3")
        val document = MedicalDocument(
            id = documentId,
            title = "Lab result",
            category = BuiltInDocumentCategory.LAB_RESULTS.asReference(),
            documentDate = LocalDate.of(2026, 7, 28),
            source = "Laboratory",
            blobId = UUID.fromString("27d14e33-91aa-47d5-bf19-fd7beb082d96"),
            mimeType = "application/pdf",
            sizeBytes = 1_024,
            updatedAt = now,
        )
        val first = ProfileRecord(
            profile = HealthProfile(firstId, "Amina", bloodType = "O+", lastUpdatedAt = now),
            documents = listOf(document),
            measurements = listOf(
                HealthMeasurement(
                    id = measurementId,
                    type = MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.WEIGHT),
                    reading = MeasurementReading.Scalar(
                        72.5,
                        MeasurementUnitRef.BuiltIn(MeasurementUnit.KILOGRAM),
                    ),
                    measuredAt = now,
                    updatedAt = now,
                ),
            ),
        )
        val noteId = UUID.fromString("096c83e2-7703-49b4-8d24-e5bf5f9c63e4")
        val contactId = UUID.fromString("34a502d7-23c0-4be5-a8fd-34b56dca82d0")
        val vault = HealthVault(
            revision = 7,
            profiles = listOf(
                first,
                ProfileRecord(HealthProfile(secondId, "Sam", lastUpdatedAt = now)),
            ),
            notes = listOf(HealthNote(noteId, "Follow-up", "Context", now, now)),
            schedules = listOf(
                Schedule(
                    id = UUID.fromString("d1430c23-8127-4a1e-9764-42e85ffecdd8"),
                    title = "Family check-in",
                    timing = ScheduleTiming.AllDay(LocalDate.of(2026, 8, 1)),
                    recurrence = ScheduleRecurrence.Monthly(1, LocalDate.of(2026, 12, 1)),
                    alert = ScheduleAlert.AllDay(1, LocalTime.of(9, 0)),
                    people = listOf("Amina", "Sam"),
                    updatedAt = now,
                ),
            ),
            contacts = listOf(
                VaultContact(
                    id = contactId,
                    name = "Dr Martin",
                    phoneNumbers = listOf("+33 1 23 45 67 89"),
                    updatedAt = now,
                ),
            ),
            updatedAt = now,
        ).requireValid()

        val encoded = VaultCodec.encode(vault)
        val decoded = VaultCodec.decode(encoded)

        assertEquals(1, decoded.sourceVersion)
        assertEquals(vault, decoded.vault)
        assertEquals(listOf(documentId, measurementId), decoded.vault.profiles.first().index().map(VaultItem::id))
        assertEquals(listOf(contactId), decoded.vault.contactIndex().map(VaultItem::id))
        val text = encoded.decodeToString()
        assertFalse(text.contains("\"summary\""))
        assertFalse(text.contains("\"vaultItems\""))
    }

    @Test
    fun preResetSchemasAndFutureSchemasAreRejected() {
        assertThrows(UnsupportedVaultVersionException::class.java) {
            VaultCodec.decode("""{"version":6}""".encodeToByteArray())
        }
        assertThrows(UnsupportedVaultVersionException::class.java) {
            VaultCodec.decode("""{"version":2}""".encodeToByteArray())
        }
        assertThrows(UnsupportedVaultVersionException::class.java) {
            VaultCodec.encode(HealthVault.empty(now).copy(version = 2))
        }
    }

    @Test
    fun legacyV1ShapeIsNotMigratedIntoTheResetSchema() {
        val legacy = """
            {
              "version":1,
              "revision":3,
              "profile":{
                "id":"2361588e-ee3f-466b-b054-6d8f4f132c60",
                "displayName":"Amina",
                "lastUpdatedAt":"$now"
              },
              "updatedAt":"$now"
            }
        """.trimIndent().encodeToByteArray()

        assertThrows(SerializationException::class.java) { VaultCodec.decode(legacy) }
    }
}
